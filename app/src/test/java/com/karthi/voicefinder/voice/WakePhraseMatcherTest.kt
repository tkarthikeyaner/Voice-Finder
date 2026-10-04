package com.karthi.voicefinder.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows

class WakePhraseMatcherTest {
    private val extractor = FeatureExtractor()

    private fun utter(phrase: List<Pair<Double, Double>>, voice: SyntheticSpeech.Voice, tempo: Double, seed: Int) =
        extractor.extract(SyntheticSpeech.speak(phrase, voice, tempo, seed))

    private val profile = VoiceProfile.build(
        listOf(0.9 to 1, 1.0 to 2, 1.1 to 3, 0.95 to 4).map { (tempo, seed) -> utter(SyntheticSpeech.PHRASE, SyntheticSpeech.OWNER, tempo, seed) },
    )
    private val matcher = WakePhraseMatcher(profile)

    @Test
    fun ownerSayingThePhraseIsAccepted() {
        val result = matcher.evaluate(utter(SyntheticSpeech.PHRASE, SyntheticSpeech.OWNER, tempo = 1.05, seed = 9), sensitivity = 1f)
        assertTrue(result.toString(), result.accepted)
    }

    @Test
    fun ownerSayingSomethingElseIsRejected() {
        val result = matcher.evaluate(utter(SyntheticSpeech.OTHER_PHRASE, SyntheticSpeech.OWNER, tempo = 1.0, seed = 9), sensitivity = 1f)
        assertFalse(result.toString(), result.accepted)
        assertTrue(result.phraseScore > 1f)
    }

    @Test
    fun strangerSayingThePhraseIsRejected() {
        val result = matcher.evaluate(utter(SyntheticSpeech.PHRASE, SyntheticSpeech.STRANGER, tempo = 1.0, seed = 9), sensitivity = 1f)
        assertFalse(result.toString(), result.accepted)
    }

    @Test
    fun profileNeedsMinimumSamples() {
        val one = utter(SyntheticSpeech.PHRASE, SyntheticSpeech.OWNER, 1.0, 1)
        assertThrows(IllegalArgumentException::class.java) { VoiceProfile.build(listOf(one, one)) }
    }
}
