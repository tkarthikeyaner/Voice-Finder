package com.karthi.voicefinder.voice

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mel-frequency cepstral coefficients: 25 ms Hamming windows every 10 ms, 26 mel bands, DCT-II.
 * Returns coefficients 1..[numCoeffs] per frame (c0 is dropped because it only tracks loudness).
 */
class Mfcc(
    private val sampleRate: Int = VoiceAudio.SAMPLE_RATE,
    private val frameSize: Int = 400,
    private val hopSize: Int = 160,
    private val fftSize: Int = 512,
    private val melBands: Int = 26,
    val numCoeffs: Int = 12,
) {
    init {
        require(fftSize and (fftSize - 1) == 0) { "fftSize must be a power of two" }
        require(frameSize <= fftSize) { "frameSize must fit in fftSize" }
        require(numCoeffs < melBands) { "numCoeffs must be below melBands" }
    }

    private val window = FloatArray(frameSize) { (0.54 - 0.46 * cos(2 * PI * it / (frameSize - 1))).toFloat() }
    private val filterBank: Array<FloatArray> = buildFilterBank()
    private val dct: Array<FloatArray> = Array(numCoeffs) { k ->
        val c = k + 1
        FloatArray(melBands) { n -> (sqrt(2.0 / melBands) * cos(PI * c * (n + 0.5) / melBands)).toFloat() }
    }

    fun compute(pcm: ShortArray): Array<FloatArray> {
        if (pcm.size < frameSize) return emptyArray()
        val signal = FloatArray(pcm.size)
        var prev = 0f
        for (i in pcm.indices) {
            val s = pcm[i] / 32768f
            signal[i] = s - 0.97f * prev
            prev = s
        }
        val frameCount = 1 + (signal.size - frameSize) / hopSize
        val re = FloatArray(fftSize)
        val im = FloatArray(fftSize)
        val power = FloatArray(fftSize / 2 + 1)
        val logMel = FloatArray(melBands)
        return Array(frameCount) { f ->
            val start = f * hopSize
            re.fill(0f)
            im.fill(0f)
            for (i in 0 until frameSize) re[i] = signal[start + i] * window[i]
            fft(re, im)
            for (k in power.indices) power[k] = (re[k] * re[k] + im[k] * im[k]) / fftSize
            for (m in 0 until melBands) {
                var e = 0f
                val weights = filterBank[m]
                for (k in power.indices) e += weights[k] * power[k]
                logMel[m] = ln(max(e, 1e-10f))
            }
            FloatArray(numCoeffs) { c ->
                var acc = 0f
                val basis = dct[c]
                for (m in 0 until melBands) acc += basis[m] * logMel[m]
                acc
            }
        }
    }

    private fun buildFilterBank(): Array<FloatArray> {
        fun hzToMel(hz: Double) = 2595.0 * log10(1.0 + hz / 700.0)
        fun melToHz(mel: Double) = 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0)
        val lowMel = hzToMel(20.0)
        val highMel = hzToMel(sampleRate / 2.0)
        val bins = IntArray(melBands + 2) { i ->
            val hz = melToHz(lowMel + (highMel - lowMel) * i / (melBands + 1))
            floor((fftSize + 1) * hz / sampleRate).toInt()
        }
        return Array(melBands) { m ->
            val (left, center, right) = Triple(bins[m], bins[m + 1], bins[m + 2])
            FloatArray(fftSize / 2 + 1) { k ->
                when {
                    k in left until center && center > left -> (k - left).toFloat() / (center - left)
                    k in center..right && right > center -> (right - k).toFloat() / (right - center)
                    else -> 0f
                }
            }
        }
    }

    /** In-place iterative radix-2 Cooley–Tukey FFT. */
    private fun fft(re: FloatArray, im: FloatArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }
        var len = 2
        while (len <= n) {
            val angle = -2 * PI / len
            val wRe = cos(angle).toFloat()
            val wIm = sin(angle).toFloat()
            var i = 0
            while (i < n) {
                var curRe = 1f
                var curIm = 0f
                for (k in 0 until len / 2) {
                    val a = i + k
                    val b = a + len / 2
                    val tRe = re[b] * curRe - im[b] * curIm
                    val tIm = re[b] * curIm + im[b] * curRe
                    re[b] = re[a] - tRe
                    im[b] = im[a] - tIm
                    re[a] += tRe
                    im[a] += tIm
                    val nextRe = curRe * wRe - curIm * wIm
                    curIm = curRe * wIm + curIm * wRe
                    curRe = nextRe
                }
                i += len
            }
            len = len shl 1
        }
    }
}
