package com.karthi.voicefinder.voice

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Energy-based voice activity detector that cuts the microphone stream into speech segments.
 * Only these short segments reach the (comparatively expensive) feature extraction, which keeps
 * the always-on listener cheap on battery. The noise floor adapts, so a fan or traffic doesn't
 * register as speech.
 */
class SpeechSegmenter(
    private val frameSamples: Int = VoiceAudio.FRAME_SAMPLES,
    private val startMarginDb: Float = 10f,
    private val endMarginDb: Float = 6f,
    private val absoluteMinDb: Float = -55f,
    private val startFrames: Int = 3,
    private val hangoverFrames: Int = 40,
    private val preRollFrames: Int = 20,
    private val minSpeechFrames: Int = 40,
    private val maxSpeechFrames: Int = 350,
) {
    private var noiseFloorDb = -60f
    private val preRoll = ArrayDeque<ShortArray>()
    private val segment = ArrayList<ShortArray>()
    private var inSpeech = false
    /** Set after an over-long segment; nothing new starts until the talking actually stops. */
    private var awaitingQuiet = false
    private var loudRun = 0
    private var quietRun = 0

    /** Feed one frame; returns a finished speech segment when one just ended, otherwise null. */
    fun feed(frame: ShortArray): ShortArray? {
        require(frame.size == frameSamples) { "Expected $frameSamples samples, got ${frame.size}" }
        val db = levelDb(frame)
        return if (inSpeech) continueSpeech(frame, db) else awaitSpeech(frame, db)
    }

    fun reset() {
        preRoll.clear()
        segment.clear()
        inSpeech = false
        awaitingQuiet = false
        loudRun = 0
        quietRun = 0
    }

    private fun awaitSpeech(frame: ShortArray, db: Float): ShortArray? {
        if (awaitingQuiet) {
            quietRun = if (db < max(noiseFloorDb + endMarginDb, absoluteMinDb)) quietRun + 1 else 0
            if (quietRun >= hangoverFrames) awaitingQuiet = false
            return null
        }
        preRoll.addLast(frame)
        if (preRoll.size > preRollFrames) preRoll.removeFirst()
        if (db > max(noiseFloorDb + startMarginDb, absoluteMinDb)) {
            if (++loudRun >= startFrames) {
                inSpeech = true
                quietRun = 0
                segment.addAll(preRoll)
                preRoll.clear()
            }
        } else {
            loudRun = 0
            // Track the background slowly upward, quickly downward.
            val alpha = if (db > noiseFloorDb) 0.02f else 0.2f
            noiseFloorDb += alpha * (db - noiseFloorDb)
        }
        return null
    }

    private fun continueSpeech(frame: ShortArray, db: Float): ShortArray? {
        segment.add(frame)
        quietRun = if (db < max(noiseFloorDb + endMarginDb, absoluteMinDb)) quietRun + 1 else 0
        val ended = quietRun >= hangoverFrames
        if (!ended && segment.size < maxSpeechFrames) return null

        // A segment that hits the length cap is ongoing noise or conversation, not a short phrase.
        val speechFrames = segment.size - quietRun
        val result = if (ended && speechFrames >= minSpeechFrames) {
            concat(segment.subList(0, speechFrames + minOf(TRAILING_FRAMES, quietRun)))
        } else {
            null
        }
        reset()
        if (!ended) awaitingQuiet = true
        return result
    }

    private fun concat(frames: List<ShortArray>): ShortArray {
        val out = ShortArray(frames.size * frameSamples)
        frames.forEachIndexed { i, f -> f.copyInto(out, i * frameSamples) }
        return out
    }

    private companion object {
        const val TRAILING_FRAMES = 10
    }

    private fun levelDb(frame: ShortArray): Float {
        var sum = 0.0
        for (s in frame) sum += s.toDouble() * s
        val rms = sqrt(sum / frame.size) / 32768.0
        return (20 * log10(max(rms, 1e-6))).toFloat()
    }
}
