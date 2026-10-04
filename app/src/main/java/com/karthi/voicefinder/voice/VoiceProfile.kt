package com.karthi.voicefinder.voice

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The enrolled wake phrase: several recordings of the owner saying it, plus thresholds calibrated
 * from how much those recordings differ from each other.
 *
 * Besides the whole phrase, the opening and closing parts are calibrated separately ([partThreshold]):
 * a fragment like "எங்க இருக்க" can resemble the whole phrase on average, but its start can't match "ஏய்".
 */
class VoiceProfile(
    val templates: List<Array<FloatArray>>,
    val voiceprintCentroid: FloatArray,
    val phraseThreshold: Float,
    val partThreshold: Float,
    val voiceThreshold: Float,
) {
    init {
        require(templates.size >= MIN_SAMPLES) { "Need at least $MIN_SAMPLES samples" }
    }

    /** Median spoken length of the enrolled phrase, in 10 ms frames. */
    val typicalFrames: Int = templates.map { it.size }.sorted()[templates.size / 2]

    companion object {
        const val MIN_SAMPLES = 3
        const val MAX_SAMPLES = 6

        /** Shortest believable full phrase; anything shorter is a single word or a cough. */
        const val MIN_PHRASE_FRAMES = 50

        /** Slack added on top of the worst-case spread between the owner's own samples. */
        private const val PHRASE_MARGIN = 1.15f
        private const val PART_MARGIN = 1.2f
        private const val VOICE_MARGIN = 1.5f

        /** Enrollment samples must agree in length; a much shorter one is usually missing a word. */
        private const val SAMPLE_LENGTH_TOLERANCE = 0.3f

        /** Fraction of the phrase compared as its opening / closing part. */
        private const val PART_FRACTION = 0.4f

        fun head(frames: Array<FloatArray>): Array<FloatArray> = frames.copyOfRange(0, partLength(frames))
        fun tail(frames: Array<FloatArray>): Array<FloatArray> = frames.copyOfRange(frames.size - partLength(frames), frames.size)

        /** Worse of the opening-part and closing-part DTW distances. */
        fun partDistance(a: Array<FloatArray>, b: Array<FloatArray>): Float =
            max(Dtw.distance(head(a), head(b)), Dtw.distance(tail(a), tail(b)))

        private fun partLength(frames: Array<FloatArray>) = (frames.size * PART_FRACTION).roundToInt().coerceIn(1, frames.size)

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
            for (i in samples.indices) for (j in i + 1 until samples.size) {
                val d = Dtw.distance(samples[i].frames, samples[j].frames)
                val p = partDistance(samples[i].frames, samples[j].frames)
                require(d.isFinite() && p.isFinite()) { "Samples ${i + 1} and ${j + 1} differ too much; tap Reset and record again" }
                worstPair = max(worstPair, d)
                worstPart = max(worstPart, p)
            }

            val dims = samples.first().voiceprint.size
            val centroid = FloatArray(dims)
            for (s in samples) for (k in 0 until dims) centroid[k] += s.voiceprint[k]
            for (k in 0 until dims) centroid[k] /= samples.size
            val worstVoice = samples.maxOf { Dtw.euclidean(it.voiceprint, centroid) }

            return VoiceProfile(
                templates = samples.map { it.frames },
                voiceprintCentroid = centroid,
                phraseThreshold = worstPair * PHRASE_MARGIN,
                partThreshold = worstPart * PART_MARGIN,
                // Floor keeps three near-identical samples from producing an impossibly tight gate.
                voiceThreshold = max(worstVoice * VOICE_MARGIN, 1f),
            )
        }
    }
}
