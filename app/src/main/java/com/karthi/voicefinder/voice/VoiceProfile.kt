package com.karthi.voicefinder.voice

import kotlin.math.max

/**
 * The enrolled wake phrase: several recordings of the owner saying it, plus thresholds calibrated
 * from how much those recordings differ from each other.
 */
class VoiceProfile(
    val templates: List<Array<FloatArray>>,
    val voiceprintCentroid: FloatArray,
    val phraseThreshold: Float,
    val voiceThreshold: Float,
) {
    init {
        require(templates.size >= MIN_SAMPLES) { "Need at least $MIN_SAMPLES samples" }
    }

    companion object {
        const val MIN_SAMPLES = 3
        const val MAX_SAMPLES = 6

        /** Slack added on top of the worst-case spread between the owner's own samples. */
        private const val PHRASE_MARGIN = 1.15f
        private const val VOICE_MARGIN = 1.5f

        fun build(samples: List<Utterance>): VoiceProfile {
            require(samples.size >= MIN_SAMPLES) { "Record at least $MIN_SAMPLES samples (have ${samples.size})" }
            var worstPair = 0f
            for (i in samples.indices) for (j in i + 1 until samples.size) {
                val d = Dtw.distance(samples[i].frames, samples[j].frames)
                require(d.isFinite()) { "Samples ${i + 1} and ${j + 1} differ too much in length; re-record them" }
                worstPair = max(worstPair, d)
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
                // Floor keeps three near-identical samples from producing an impossibly tight gate.
                voiceThreshold = max(worstVoice * VOICE_MARGIN, 1f),
            )
        }
    }
}
