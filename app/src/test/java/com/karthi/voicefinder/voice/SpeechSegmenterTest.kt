package com.karthi.voicefinder.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechSegmenterTest {

    private fun segments(frames: List<ShortArray>): List<ShortArray> {
        val segmenter = SpeechSegmenter()
        return frames.mapNotNull { segmenter.feed(it) }
    }

    @Test
    fun phraseBetweenSilencesBecomesOneSegment() {
        val phrase = SyntheticSpeech.speak(SyntheticSpeech.PHRASE, SyntheticSpeech.OWNER)
        val found = segments(SyntheticSpeech.frames(SyntheticSpeech.silence(1000), phrase, SyntheticSpeech.silence(1000, seed = 2)))
        assertEquals(1, found.size)
        val ms = found.single().size * 1000 / VoiceAudio.SAMPLE_RATE
        val phraseMs = phrase.size * 1000 / VoiceAudio.SAMPLE_RATE
        assertTrue("segment $ms ms vs phrase $phraseMs ms", ms in phraseMs..phraseMs + 400)
    }

    @Test
    fun pauseBetweenWordsKeepsPhraseInOneSegment() {
        val ey = SyntheticSpeech.speak(SyntheticSpeech.PHRASE.take(1), SyntheticSpeech.OWNER)
        val rest = SyntheticSpeech.speak(SyntheticSpeech.PHRASE.drop(1), SyntheticSpeech.OWNER, seed = 3)
        val found = segments(
            SyntheticSpeech.frames(SyntheticSpeech.silence(800), ey, SyntheticSpeech.silence(450, seed = 4), rest, SyntheticSpeech.silence(1000, seed = 5)),
        )
        assertEquals(1, found.size)
    }

    @Test
    fun shortClickIsIgnored() {
        val click = SyntheticSpeech.speak(SyntheticSpeech.PHRASE.take(1), SyntheticSpeech.OWNER, tempo = 2.0)
        assertTrue(segments(SyntheticSpeech.frames(SyntheticSpeech.silence(800), click, SyntheticSpeech.silence(800))).isEmpty())
    }

    @Test
    fun continuousTalkingIsIgnored() {
        val long = SyntheticSpeech.speak(SyntheticSpeech.PHRASE + SyntheticSpeech.PHRASE + SyntheticSpeech.PHRASE, SyntheticSpeech.OWNER, tempo = 0.8)
        assertTrue(segments(SyntheticSpeech.frames(SyntheticSpeech.silence(800), long, SyntheticSpeech.silence(800))).isEmpty())
    }

    @Test
    fun detectsAgainAfterReset() {
        val segmenter = SpeechSegmenter()
        val frames = SyntheticSpeech.frames(SyntheticSpeech.silence(800), SyntheticSpeech.speak(SyntheticSpeech.PHRASE, SyntheticSpeech.OWNER), SyntheticSpeech.silence(800))
        frames.take(frames.size / 2).forEach { segmenter.feed(it) }
        segmenter.reset()
        assertNotNull(frames.firstNotNullOfOrNull { segmenter.feed(it) })
    }
}
