package com.karthi.voicefinder.voice

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

object AudioLevel {
    /** Loudness for a level meter: 0 at about -60 dBFS (quiet room), 1 at -10 dBFS (loud speech up close). */
    fun normalized(frame: ShortArray): Float {
        if (frame.isEmpty()) return 0f
        var sum = 0.0
        for (s in frame) sum += s.toDouble() * s
        val rms = sqrt(sum / frame.size) / 32768.0
        val db = 20 * log10(max(rms, 1e-6))
        return ((db + 60) / 50).toFloat().coerceIn(0f, 1f)
    }
}
