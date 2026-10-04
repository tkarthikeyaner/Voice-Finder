package com.karthi.voicefinder.voice

import kotlin.math.max
import kotlin.math.min

/**
 * The enrolled wake phrase: several recordings of the owner saying it, plus thresholds calibrated from them.
 *
 * Thresholds are set from both sides: how much the owner's own recordings differ (they must all pass), and
 * how close "same voice, wrong words" comes. The latter is simulated from the recordings themselves
 * ([impostors]: the same sounds in a different order) and the limit is kept well short of it, so another
 * sentence in the same voice and tone doesn't fit inside the tolerance.
 *
 * The phrase is also checked in thirds ([partThreshold]), so each word has to match, not just the average.
 * [negatives] are phrases the user marked as "should not trigger".
 */
class VoiceProfile(
    val templates: List<Array<FloatArray>>,
    val voiceprintCentroid: FloatArray,
    val phraseThreshold: Float,
    val partThreshold: Float,
    val voiceThreshold: Float,
    val negatives: List<Array<FloatArray>> = emptyList(),
) {
    init {
        require(templates.size >= MIN_SAMPLES) { "Need at least $MIN_SAMPLES samples" }
    }

    /** Median spoken length of the enrolled phrase, in 10 ms frames. */
    val typicalFrames: Int = templates.map { it.size }.sorted()[templates.size / 2]

    fun withNegative(frames: Array<FloatArray>) = copy(negatives = (negatives + listOf(frames)).takeLast(MAX_NEGATIVES))

    fun withoutNegatives() = copy(negatives = emptyList())

    private fun copy(negatives: List<Array<FloatArray>>) =
        VoiceProfile(templates, voiceprintCentroid, phraseThreshold, partThreshold, voiceThreshold, negatives)

    companion object {
        const val MIN_SAMPLES = 3
        const val MAX_SAMPLES = 6
        const val MAX_NEGATIVES = 20

        /** Shortest believable full phrase; anything shorter is a single word or a cough. */
        const val MIN_PHRASE_FRAMES = 50

        /** Slack added on top of the worst-case spread between the owner's own samples. */
        private const val PHRASE_MARGIN = 1.15f
        private const val PART_MARGIN = 1.2f
        private const val VOICE_MARGIN = 1.5f

        /** The limit sits at most this far from the owner's worst genuine pair towards the nearest impostor. */
        private const val IMPOSTOR_GAP = 0.5f

        /** Enrollment samples must agree in length; a much shorter one is usually missing a word. */
        private const val SAMPLE_LENGTH_TOLERANCE = 0.3f

        /** The phrase split into three consecutive parts (roughly one per word). */
        fun parts(frames: Array<FloatArray>): List<Array<FloatArray>> {
            val n = frames.size
            return (0 until 3).map { i -> frames.copyOfRange(i * n / 3, maxOf(i * n / 3 + 1, (i + 1) * n / 3)) }
        }

        /** Worst DTW distance among corresponding thirds. */
        fun partDistance(a: Array<FloatArray>, b: Array<FloatArray>): Float =
            parts(a).zip(parts(b)).maxOf { (x, y) -> Dtw.distance(x, y) }

        /** Same voice and sounds, wrong order: what "different words, same tone" looks like to the matcher. */
        private fun impostors(frames: Array<FloatArray>): List<Array<FloatArray>> {
            val third = frames.size / 3
            return listOf(rotate(frames, third), rotate(frames, 2 * third), frames.reversedArray())
        }

        private fun rotate(frames: Array<FloatArray>, by: Int) = Array(frames.size) { frames[(it + by) % frames.size] }

        private fun calibrated(worstGenuine: Float, nearestImpostor: Float, margin: Float): Float {
            // Inconsistent recordings (an impostor closer than a genuine pair): no room for slack at all.
            if (nearestImpostor <= worstGenuine) return worstGenuine
            return min(worstGenuine * margin, worstGenuine + (nearestImpostor - worstGenuine) * IMPOSTOR_GAP)
        }

        fun build(samples: List<Utterance>): VoiceProfile {
            require(samples.size >= MIN_SAMPLES) { "Record at least $MIN_SAMPLES samples (have ${samples.size})" }
            val lengths = samples.map { it.frames.size }
            val median = lengths.sorted()[lengths.size / 2]
            lengths.forEachIndexed { i, len ->
                require(len >= MIN_PHRASE_FRAMES) { "Sample ${i + 1} is too short. Say the whole phrase in one go; tap Reset and record again." }
                require(len >= median * (1 - SAMPLE_LENGTH_TOLERANCE) && len <= median * (1 + SAMPLE_LENGTH_TOLERANCE + 0.2f)) {
                    "Sample ${i + 1} is a different length from the others. It may be missing a word; tap Reset and record again."
                }
            }

            var worstPair = 0f
            var worstPart = 0f
            var nearestImpostor = Float.POSITIVE_INFINITY
            var nearestImpostorPart = Float.POSITIVE_INFINITY
            for (i in samples.indices) for (j in samples.indices) {
                if (i == j) continue
                val a = samples[i].frames
                val b = samples[j].frames
                if (i < j) {
                    val d = Dtw.distance(a, b)
                    val p = partDistance(a, b)
                    require(d.isFinite() && p.isFinite()) { "Samples ${i + 1} and ${j + 1} differ too much; tap Reset and record again" }
                    worstPair = max(worstPair, d)
                    worstPart = max(worstPart, p)
                }
                for (impostor in impostors(a)) {
                    nearestImpostor = min(nearestImpostor, Dtw.distance(impostor, b))
                    nearestImpostorPart = min(nearestImpostorPart, partDistance(impostor, b))
                }
            }

            val dims = samples.first().voiceprint.size
            val centroid = FloatArray(dims)
            for (s in samples) for (k in 0 until dims) centroid[k] += s.voiceprint[k]
            for (k in 0 until dims) centroid[k] /= samples.size
            val worstVoice = samples.maxOf { Dtw.euclidean(it.voiceprint, centroid) }

            return VoiceProfile(
                templates = samples.map { it.frames },
                voiceprintCentroid = centroid,
                phraseThreshold = calibrated(worstPair, nearestImpostor, PHRASE_MARGIN),
                partThreshold = calibrated(worstPart, nearestImpostorPart, PART_MARGIN),
                // Floor keeps three near-identical samples from producing an impossibly tight gate.
                voiceThreshold = max(worstVoice * VOICE_MARGIN, 1f),
            )
        }
    }
}
