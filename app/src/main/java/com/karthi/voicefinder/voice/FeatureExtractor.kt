package com.karthi.voicefinder.voice

import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * One spoken segment reduced to what matching needs:
 * [frames] — per frame, mean- and variance-normalised MFCCs plus their deltas (how the sound is changing),
 * for the speech only (silence trimmed off both ends). Compared with DTW to recognise *what* was said; its
 * length is the spoken duration in 10 ms steps. Normalising the variance stops the first coefficients, which
 * mostly carry overall tone and loudness, from drowning out the ones that tell words apart;
 * [voiceprint] — per-coefficient mean and spread of the raw MFCCs, a coarse fingerprint of *who* said it.
 */
class Utterance(val frames: Array<FloatArray>, val voiceprint: FloatArray)

class FeatureExtractor(private val mfcc: Mfcc = Mfcc()) {

    fun extract(pcm: ShortArray): Utterance {
        val all = mfcc.compute(pcm)
        require(all.isNotEmpty()) { "Segment too short for feature extraction (${pcm.size} samples)" }
        val raw = trimSilence(pcm, all)
        val dims = mfcc.numCoeffs
        val mean = FloatArray(dims)
        for (frame in raw) for (k in 0 until dims) mean[k] += frame[k]
        for (k in 0 until dims) mean[k] /= raw.size
        val std = FloatArray(dims)
        for (frame in raw) for (k in 0 until dims) {
            val d = frame[k] - mean[k]
            std[k] += d * d
        }
        for (k in 0 until dims) std[k] = sqrt(std[k] / raw.size)

        // Mean normalisation removes the microphone/room colouring; variance normalisation equalises coefficients.
        val normalised = Array(raw.size) { f -> FloatArray(dims) { k -> (raw[f][k] - mean[k]) / max(std[k], MIN_STD) } }
        return Utterance(withDeltas(normalised), mean + std)
    }

    /** Appends regression deltas over ±[DELTA_WINDOW] frames: the direction each sound is moving in. */
    private fun withDeltas(c: Array<FloatArray>): Array<FloatArray> {
        val n = c.size
        val dims = c[0].size
        return Array(n) { t ->
            FloatArray(dims * 2) { k ->
                if (k < dims) {
                    c[t][k]
                } else {
                    val j = k - dims
                    var acc = 0f
                    for (w in 1..DELTA_WINDOW) acc += w * (c[minOf(n - 1, t + w)][j] - c[maxOf(0, t - w)][j])
                    acc / DELTA_NORM
                }
            }
        }
    }

    /**
     * Keeps frames from the first to the last one within [TRIM_DB] of the loudest, so leading/trailing
     * silence doesn't pad a short utterance up to the length of the full phrase.
     */
    private fun trimSilence(pcm: ShortArray, frames: Array<FloatArray>): Array<FloatArray> {
        val energies = FloatArray(frames.size) { f -> frameDb(pcm, f * mfcc.hopSize, mfcc.frameSize) }
        val floor = energies.max() - TRIM_DB
        val first = energies.indexOfFirst { it > floor }
        val last = energies.indexOfLast { it > floor }
        return frames.copyOfRange(first, last + 1)
    }

    private fun frameDb(pcm: ShortArray, start: Int, length: Int): Float {
        var sum = 0.0
        val end = minOf(pcm.size, start + length)
        for (i in start until end) sum += pcm[i].toDouble() * pcm[i]
        val rms = sqrt(sum / max(1, end - start)) / 32768.0
        return (20 * log10(max(rms, 1e-6))).toFloat()
    }

    private companion object {
        const val TRIM_DB = 30f
        const val MIN_STD = 0.3f
        const val DELTA_WINDOW = 2
        const val DELTA_NORM = 10f // 2 × (1² + 2²)
    }
}
