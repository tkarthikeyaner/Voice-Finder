package com.karthi.voicefinder.voice

import kotlin.math.max
import kotlin.math.min

/**
 * Scores are ratios to the calibrated threshold: 1.0 means "exactly at the limit", lower is a closer match.
 * [complete] is false when the segment is far shorter or longer than the enrolled phrase (a single word,
 * or part of it). [markedWrong] is true when it sounds more like a phrase the user marked as "should not
 * trigger" than like the wake phrase. A segment triggers only when it is complete, not marked wrong, and
 * both phrase and voice are within limits.
 */
data class MatchResult(
    val phraseScore: Float,
    val voiceScore: Float,
    val accepted: Boolean,
    val complete: Boolean = true,
    val markedWrong: Boolean = false,
)

class WakePhraseMatcher(private val profile: VoiceProfile) {

    /** [sensitivity] > 1 accepts looser matches (fewer misses, more false alarms); < 1 is stricter. */
    fun evaluate(utterance: Utterance, sensitivity: Float): MatchResult {
        require(sensitivity > 0f) { "sensitivity must be positive" }
        val frames = utterance.frames
        val lengthRatio = frames.size.toFloat() / profile.typicalFrames
        val complete = frames.size >= VoiceProfile.MIN_PHRASE_FRAMES && lengthRatio in MIN_LENGTH_RATIO..MAX_LENGTH_RATIO

        // Every part has to match the same recording: the whole phrase and each third of it.
        var phraseScore = Float.POSITIVE_INFINITY
        var nearestTemplate = Float.POSITIVE_INFINITY
        for (template in profile.templates) {
            val distance = Dtw.distance(frames, template)
            nearestTemplate = min(nearestTemplate, distance)
            val whole = distance / (profile.phraseThreshold * sensitivity)
            if (whole >= phraseScore) continue
            val parts = VoiceProfile.partDistance(frames, template) / (profile.partThreshold * sensitivity)
            phraseScore = min(phraseScore, max(whole, parts))
        }
        val nearestNegative = profile.negatives.minOfOrNull { Dtw.distance(frames, it) } ?: Float.POSITIVE_INFINITY
        val markedWrong = nearestNegative < nearestTemplate

        val voiceScore = Dtw.euclidean(utterance.voiceprint, profile.voiceprintCentroid) / (profile.voiceThreshold * sensitivity)
        val accepted = complete && !markedWrong && phraseScore <= 1f && voiceScore <= 1f
        return MatchResult(phraseScore, voiceScore, accepted, complete, markedWrong)
    }

    private companion object {
        /** Natural variation in speaking speed; a missing word shortens the phrase well beyond this. */
        const val MIN_LENGTH_RATIO = 0.72f
        const val MAX_LENGTH_RATIO = 1.45f
    }
}
