package com.karthi.voicefinder.voice

/**
 * Scores are ratios to the calibrated threshold: 1.0 means "exactly at the limit", lower is a closer match.
 * A segment triggers only when both the phrase and the voice are within limits.
 */
data class MatchResult(val phraseScore: Float, val voiceScore: Float, val accepted: Boolean)

class WakePhraseMatcher(private val profile: VoiceProfile) {

    /** [sensitivity] > 1 accepts looser matches (fewer misses, more false alarms); < 1 is stricter. */
    fun evaluate(utterance: Utterance, sensitivity: Float): MatchResult {
        require(sensitivity > 0f) { "sensitivity must be positive" }
        val phraseDistance = profile.templates.minOf { Dtw.distance(utterance.frames, it) }
        val voiceDistance = Dtw.euclidean(utterance.voiceprint, profile.voiceprintCentroid)
        val phraseScore = phraseDistance / (profile.phraseThreshold * sensitivity)
        val voiceScore = voiceDistance / (profile.voiceThreshold * sensitivity)
        return MatchResult(phraseScore, voiceScore, phraseScore <= 1f && voiceScore <= 1f)
    }
}
