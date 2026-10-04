package com.karthi.voicefinder.voice

import kotlin.math.sqrt

/**
 * One spoken segment reduced to what matching needs:
 * [frames] — mean-normalised MFCCs, compared with DTW to recognise *what* was said;
 * [voiceprint] — per-coefficient mean and spread of the raw MFCCs, a coarse fingerprint of *who* said it.
 */
class Utterance(val frames: Array<FloatArray>, val voiceprint: FloatArray)

class FeatureExtractor(private val mfcc: Mfcc = Mfcc()) {

    fun extract(pcm: ShortArray): Utterance {
        val raw = mfcc.compute(pcm)
        require(raw.isNotEmpty()) { "Segment too short for feature extraction (${pcm.size} samples)" }
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

        // Cepstral mean normalisation removes the microphone/room colouring before DTW.
        val normalised = Array(raw.size) { f -> FloatArray(dims) { k -> raw[f][k] - mean[k] } }
        return Utterance(normalised, mean + std)
    }
}
