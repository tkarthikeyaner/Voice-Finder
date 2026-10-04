package com.karthi.voicefinder.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same voice, same tone and rhythm, different words: must not trigger. */
class SimilarPhraseTest {
    private val extractor = FeatureExtractor()
    private val owner = SyntheticSpeech.OWNER
    private val phrase = SyntheticSpeech.PHRASE // e a i u a  ≈ "ஏய் எங்க இருக்க"

    // "ஏய் என்ன பண்ற": same opening, same length and melody, different later vowels.
    private val ennaPandra = listOf(500.0 to 1900.0, 700.0 to 1200.0, 700.0 to 1200.0, 600.0 to 1500.0, 700.0 to 1200.0)
    // "ஏய் எங்க போற": shares the first two words' sounds, different ending.
    private val engaPora = listOf(500.0 to 1900.0, 700.0 to 1200.0, 450.0 to 900.0, 400.0 to 850.0, 650.0 to 1000.0)

    /** Real takes vary: pitch ±8%, vocal tract ±4%, tempo, background noise. */
    private fun utter(vowels: List<Pair<Double, Double>>, tempo: Double, seed: Int, pitch: Double = 1.0, tract: Double = 1.0) =
        extractor.extract(
            SyntheticSpeech.silence(200, seed = seed + 100) +
                SyntheticSpeech.speak(
                    vowels,
                    SyntheticSpeech.Voice(owner.pitchHz * pitch, owner.formantScale * tract),
                    tempo, seed, noise = 0.01,
                ) +
                SyntheticSpeech.silence(150, seed = seed + 200),
        )

    private val samples = listOf(
        utter(phrase, 0.85, 1, pitch = 0.92, tract = 0.97),
        utter(phrase, 1.0, 2, pitch = 1.08, tract = 1.03),
        utter(phrase, 1.15, 3, pitch = 1.0, tract = 1.0),
        utter(phrase, 0.95, 4, pitch = 1.04, tract = 0.98),
    )
    private val matcher = WakePhraseMatcher(VoiceProfile.build(samples))

    @Test
    fun enrolledPhraseStillTriggers() {
        for (tempo in listOf(0.88, 1.0, 1.12)) {
            val r = matcher.evaluate(utter(phrase, tempo, seed = 77), 1f)
            assertTrue("tempo $tempo: $r", r.accepted)
        }
    }

    // One syllable swapped (இ → ஒ), everything else identical: a single different word.
    private val nearMiss = listOf(500.0 to 1900.0, 700.0 to 1200.0, 450.0 to 800.0, 350.0 to 800.0, 700.0 to 1200.0)

    /** The word check alone must reject different words; the voice check must not be what saves us. */
    @Test
    fun differentWordsFailThePhraseCheckItself() {
        for ((name, vowels) in listOf("ஏய் என்ன பண்ற" to ennaPandra, "ஏய் எங்க போற" to engaPora, "near miss" to nearMiss)) {
            val r = matcher.evaluate(utter(vowels, 1.0, seed = 77, pitch = 1.03), 1f)
            assertTrue("$name: $r", r.phraseScore > 1f)
        }
    }

    @Test
    fun differentWordsInSameToneDoNotTrigger() {
        for ((name, vowels) in listOf("ஏய் என்ன பண்ற" to ennaPandra, "ஏய் எங்க போற" to engaPora)) {
            for (sensitivity in listOf(1f, 1.25f)) {
                val r = matcher.evaluate(utter(vowels, 1.0, seed = 77), sensitivity)
                assertFalse("$name @ $sensitivity: $r", r.accepted)
            }
        }
    }

    @Test
    fun phraseMarkedWrongNeverTriggersAgainButRealPhraseStillDoes() {
        val wrong = utter(ennaPandra, 1.0, seed = 5, pitch = 1.02)
        val learned = WakePhraseMatcher(VoiceProfile.build(samples).withNegative(wrong.frames))
        for (seed in listOf(81, 82, 83)) {
            val again = learned.evaluate(utter(ennaPandra, 1.05, seed, pitch = 0.98), 1.25f)
            assertFalse("seed $seed: $again", again.accepted)
            assertTrue(again.markedWrong)
            val genuine = learned.evaluate(utter(phrase, 1.0, seed, pitch = 1.02), 1f)
            assertTrue("seed $seed: $genuine", genuine.accepted)
        }
    }
}
