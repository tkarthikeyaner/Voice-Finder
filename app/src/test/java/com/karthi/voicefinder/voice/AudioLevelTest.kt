package com.karthi.voicefinder.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioLevelTest {
    @Test
    fun silenceIsZeroAndFullScaleIsOne() {
        assertEquals(0f, AudioLevel.normalized(ShortArray(160)), 0f)
        assertEquals(1f, AudioLevel.normalized(ShortArray(160) { Short.MAX_VALUE }), 0f)
    }

    @Test
    fun speechSitsInTheMiddle() {
        val level = AudioLevel.normalized(SyntheticSpeech.speak(SyntheticSpeech.PHRASE, SyntheticSpeech.OWNER).copyOf(160))
        assertTrue("level $level", level in 0.2f..0.95f)
    }
}
