package com.karthi.voicefinder.voice

/**
 * Detects when Android hands us digital silence instead of real audio. A real microphone always
 * picks up some noise, so a long run of exact zeros means the OS has muted this app's capture
 * (background mic restriction, OEM battery manager, or another app taking priority).
 */
class SilenceWatchdog(private val framesToTrip: Int = 300) {
    private var zeroRun = 0

    /** Returns true exactly once per silent stretch, when it first reaches [framesToTrip] frames. */
    fun feed(frame: ShortArray): Boolean {
        if (frame.any { it.toInt() != 0 }) {
            zeroRun = 0
            return false
        }
        zeroRun++
        return zeroRun == framesToTrip
    }
}
