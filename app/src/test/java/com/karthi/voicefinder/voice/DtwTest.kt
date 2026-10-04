package com.karthi.voicefinder.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DtwTest {
    private fun seq(vararg v: Float) = Array(v.size) { floatArrayOf(v[it]) }

    @Test
    fun identicalSequencesHaveZeroDistance() {
        val a = seq(1f, 2f, 3f, 2f, 1f)
        assertEquals(0f, Dtw.distance(a, a), 1e-6f)
    }

    @Test
    fun timeStretchedSequenceIsCloserThanDifferentOne() {
        val a = seq(0f, 1f, 2f, 3f, 2f, 1f, 0f)
        val stretched = seq(0f, 0f, 1f, 1f, 2f, 2f, 3f, 3f, 2f, 1f, 0f)
        val different = seq(3f, 3f, 0f, 0f, 3f, 3f, 0f)
        assertTrue(Dtw.distance(a, stretched) < Dtw.distance(a, different))
    }

    @Test
    fun wildlyDifferentLengthsAreRejected() {
        val short = seq(1f, 2f)
        val long = seq(1f, 1f, 1f, 2f, 2f, 2f)
        assertEquals(Float.POSITIVE_INFINITY, Dtw.distance(short, long))
    }
}
