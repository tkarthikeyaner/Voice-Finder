package com.karthi.voicefinder.voice

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** "ஏய் எங்க இருக்க" must trigger only when every word is said, never on a piece of it. */
class PartialPhraseTest {
    private val extractor = FeatureExtractor()
    private val owner = SyntheticSpeech.OWNER
    private val phrase = SyntheticSpeech.PHRASE

    // Five vowel targets stand in for the three words: ஏய் = 0..1, எங்க = 1..3, இருக்க = 3..5.
    private val ey = phrase.take(1)
    private val engaIrukka = phrase.drop(1)
    private val irukka = phrase.drop(3)
    private val eyEnga = phrase.take(3)

    private fun utter(vowels: List<Pair<Double, Double>>, tempo: Double, seed: Int) =
        extractor.extract(
            SyntheticSpeech.silence(200, seed = seed + 100) + SyntheticSpeech.speak(vowels, owner, tempo, seed) +
                SyntheticSpeech.silence(150, seed = seed + 200),
        )

    private val matcher = WakePhraseMatcher(
        VoiceProfile.build(listOf(0.9 to 1, 1.0 to 2, 1.1 to 3, 0.95 to 4).map { (tempo, seed) -> utter(phrase, tempo, seed) }),
    )

    private fun triggers(vowels: List<Pair<Double, Double>>, tempo: Double, sensitivity: Float = 1f) =
        matcher.evaluate(utter(vowels, tempo, seed = 42), sensitivity)

    @Test
    fun fullPhraseTriggersAtNaturalSpeeds() {
        for (tempo in listOf(0.85, 1.0, 1.15)) {
            val r = triggers(phrase, tempo)
            assertTrue("tempo $tempo: $r", r.accepted)
        }
    }

    @Test
    fun partialPhrasesNeverTrigger() {
        for ((name, part) in listOf("ஏய்" to ey, "எங்க இருக்க" to engaIrukka, "இருக்க" to irukka, "ஏய் எங்க" to eyEnga)) {
            for (sensitivity in listOf(1f, 1.5f)) {
                val r = triggers(part, tempo = 1.0, sensitivity = sensitivity)
                assertFalse("$name @ $sensitivity: $r", r.accepted)
            }
        }
    }

    @Test
    fun partialPhraseSpokenSlowlyToFullLengthStillDoesNotTrigger() {
        for ((name, part) in listOf("எங்க இருக்க" to engaIrukka, "ஏய் எங்க" to eyEnga)) {
            val slowed = triggers(part, tempo = part.size / phrase.size.toDouble())
            assertFalse("$name slowed: $slowed", slowed.accepted)
        }
    }
}
