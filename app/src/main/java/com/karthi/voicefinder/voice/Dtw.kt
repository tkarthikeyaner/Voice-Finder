package com.karthi.voicefinder.voice

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Dynamic time warping between two feature sequences, so the same phrase said faster or slower still lines up. */
object Dtw {
    /**
     * Average per-step Euclidean distance along the best warping path, inside a Sakoe–Chiba band.
     * Returns [Float.POSITIVE_INFINITY] when the lengths differ by more than [maxLengthRatio].
     */
    fun distance(a: Array<FloatArray>, b: Array<FloatArray>, bandFraction: Float = 0.25f, maxLengthRatio: Float = 2f): Float {
        val n = a.size
        val m = b.size
        if (n == 0 || m == 0) return Float.POSITIVE_INFINITY
        if (max(n, m).toFloat() / min(n, m) > maxLengthRatio) return Float.POSITIVE_INFINITY
        val band = max(abs(n - m), (bandFraction * max(n, m)).toInt()) + 1

        val inf = Float.POSITIVE_INFINITY
        var prev = FloatArray(m + 1) { inf }
        var cur = FloatArray(m + 1) { inf }
        // Path length alongside the cost, so long and short phrases are compared on the same scale.
        var prevLen = IntArray(m + 1)
        var curLen = IntArray(m + 1)
        prev[0] = 0f
        for (i in 1..n) {
            cur.fill(inf)
            val center = i.toLong() * m / n
            val from = max(1, (center - band).toInt())
            val to = min(m, (center + band).toInt())
            for (j in from..to) {
                val d = euclidean(a[i - 1], b[j - 1])
                var best = prev[j - 1]
                var bestLen = prevLen[j - 1]
                if (prev[j] < best) { best = prev[j]; bestLen = prevLen[j] }
                if (cur[j - 1] < best) { best = cur[j - 1]; bestLen = curLen[j - 1] }
                if (best == inf) continue
                cur[j] = best + d
                curLen[j] = bestLen + 1
            }
            prev = cur.also { cur = prev }
            prevLen = curLen.also { curLen = prevLen }
        }
        return if (prev[m] == inf) inf else prev[m] / prevLen[m]
    }

    fun euclidean(x: FloatArray, y: FloatArray): Float {
        var sum = 0f
        for (k in x.indices) {
            val d = x[k] - y[k]
            sum += d * d
        }
        return sqrt(sum)
    }
}
