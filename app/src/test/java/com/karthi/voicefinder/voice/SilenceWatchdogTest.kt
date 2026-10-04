package com.karthi.voicefinder.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SilenceWatchdogTest {
    private val zero = ShortArray(VoiceAudio.FRAME_SAMPLES)
    private val quietRoom = ShortArray(VoiceAudio.FRAME_SAMPLES) { if (it % 7 == 0) 1 else 0 }

    @Test
    fun tripsOnceAfterSustainedDigitalSilence() {
        val watchdog = SilenceWatchdog(framesToTrip = 5)
        assertEquals(1, (1..20).count { watchdog.feed(zero) })
    }

    @Test
    fun realQuietAudioNeverTrips() {
        val watchdog = SilenceWatchdog(framesToTrip = 5)
        assertFalse((1..50).any { watchdog.feed(if (it % 4 == 0) quietRoom else zero) })
    }
}
