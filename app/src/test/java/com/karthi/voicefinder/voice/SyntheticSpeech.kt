package com.karthi.voicefinder.voice

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Vowel-like test audio: harmonics of a pitch, shaped by two formants, gliding through a vowel sequence. */
object SyntheticSpeech {
    private const val RATE = VoiceAudio.SAMPLE_RATE

    data class Voice(val pitchHz: Double, val formantScale: Double)

    val OWNER = Voice(pitchHz = 125.0, formantScale = 1.0)
    val STRANGER = Voice(pitchHz = 210.0, formantScale = 1.18)

    /** "ஏய் எங்க இருக்க" stand-in: e – a – i – u – a */
    val PHRASE = listOf(500.0 to 1900.0, 700.0 to 1200.0, 300.0 to 2300.0, 350.0 to 800.0, 700.0 to 1200.0)
    val OTHER_PHRASE = listOf(300.0 to 800.0, 300.0 to 2300.0, 450.0 to 1000.0, 600.0 to 1700.0, 300.0 to 2300.0)

    fun speak(vowels: List<Pair<Double, Double>>, voice: Voice, tempo: Double = 1.0, seed: Int = 0, noise: Double = 0.003): ShortArray {
        val rnd = Random(seed)
        val vowelSamples = (0.22 * RATE / tempo).toInt()
        val out = ShortArray(vowels.size * vowelSamples)
        var phase = 0.0
        for (i in out.indices) {
            val pos = i.toDouble() / vowelSamples
            val idx = pos.toInt().coerceAtMost(vowels.lastIndex)
            val next = (idx + 1).coerceAtMost(vowels.lastIndex)
            val t = pos - idx
            val f1 = lerp(vowels[idx].first, vowels[next].first, t) * voice.formantScale
            val f2 = lerp(vowels[idx].second, vowels[next].second, t) * voice.formantScale
            val vibrato = 1.0 + 0.02 * sin(2 * PI * 5 * i / RATE)
            phase += 2 * PI * voice.pitchHz * vibrato / RATE
            var s = 0.0
            var k = 1
            while (k * voice.pitchHz < 4000) {
                val f = k * voice.pitchHz
                val amp = bump(f, f1, 90.0) + 0.6 * bump(f, f2, 120.0) + 0.02
                s += amp * sin(k * phase)
                k++
            }
            out[i] = ((0.08 * s + noise * rnd.nextDouble(-1.0, 1.0)) * 32767).toInt().coerceIn(-32768, 32767).toShort()
        }
        return out
    }

    fun silence(ms: Int, seed: Int = 1, level: Double = 0.001): ShortArray {
        val rnd = Random(seed)
        return ShortArray(RATE * ms / 1000) { (level * rnd.nextDouble(-1.0, 1.0) * 32767).toInt().toShort() }
    }

    fun frames(vararg parts: ShortArray): List<ShortArray> {
        val all = parts.reduce { a, b -> a + b }
        return all.toList().chunked(VoiceAudio.FRAME_SAMPLES).filter { it.size == VoiceAudio.FRAME_SAMPLES }.map { it.toShortArray() }
    }

    private fun bump(f: Double, center: Double, width: Double) = exp(-((f - center) * (f - center)) / (2 * width * width))
    private fun lerp(a: Double, b: Double, t: Double) = a + (b - a) * t.coerceIn(0.0, 1.0)
}
