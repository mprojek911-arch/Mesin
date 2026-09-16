package com.autoremix.djslow.engine.dsp

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Filter Biquad Standar Audio (Transposed Direct Form II).
 * Menyediakan implementasi filter berkualitas studio untuk EQ, High Pass, Low Pass, Shelf, dan Notch.
 * Bebas dari NaN dan overflow.
 */
class BiquadFilter(
    private val type: FilterType,
    private val frequencyHz: Float,
    private val sampleRate: Int = 44100,
    private val q: Float = 0.7071f,
    private val gainDb: Float = 0.0f
) {
    enum class FilterType {
        LOW_PASS,
        HIGH_PASS,
        PEAKING_EQ,
        HIGH_SHELF,
        LOW_SHELF,
        NOTCH
    }

    private var b0: Float = 1.0f
    private var b1: Float = 0.0f
    private var b2: Float = 0.0f
    private var a1: Float = 0.0f
    private var a2: Float = 0.0f

    // State buffer per channel (L = 0, R = 1)
    private var z1L = 0.0f
    private var z2L = 0.0f
    private var z1R = 0.0f
    private var z2R = 0.0f

    init {
        recomputeCoefficients()
    }

    private fun recomputeCoefficients() {
        val nyquist = sampleRate / 2.0f
        val clampedFreq = frequencyHz.coerceIn(10.0f, nyquist * 0.99f)
        val omega = (2.0 * PI * clampedFreq / sampleRate).toDouble()
        val sn = sin(omega)
        val cs = cos(omega)
        val effectiveQ = q.coerceAtLeast(0.1f).toDouble()
        val alpha = sn / (2.0 * effectiveQ)
        val a = 10.0.pow((gainDb / 40.0).toDouble()) // sqrt(10^(gainDb/20))

        var a0 = 1.0
        var tb0 = 1.0
        var tb1 = 0.0
        var tb2 = 0.0
        var ta1 = 0.0
        var ta2 = 0.0

        when (type) {
            FilterType.LOW_PASS -> {
                tb0 = (1.0 - cs) / 2.0
                tb1 = 1.0 - cs
                tb2 = (1.0 - cs) / 2.0
                a0 = 1.0 + alpha
                ta1 = -2.0 * cs
                ta2 = 1.0 - alpha
            }
            FilterType.HIGH_PASS -> {
                tb0 = (1.0 + cs) / 2.0
                tb1 = -(1.0 + cs)
                tb2 = (1.0 + cs) / 2.0
                a0 = 1.0 + alpha
                ta1 = -2.0 * cs
                ta2 = 1.0 - alpha
            }
            FilterType.PEAKING_EQ -> {
                tb0 = 1.0 + alpha * a
                tb1 = -2.0 * cs
                tb2 = 1.0 - alpha * a
                a0 = 1.0 + alpha / a
                ta1 = -2.0 * cs
                ta2 = 1.0 - alpha / a
            }
            FilterType.HIGH_SHELF -> {
                val sqrtA = sqrt(a)
                tb0 = a * ((a + 1.0) + (a - 1.0) * cs + 2.0 * sqrtA * alpha)
                tb1 = -2.0 * a * ((a - 1.0) + (a + 1.0) * cs)
                tb2 = a * ((a + 1.0) + (a - 1.0) * cs - 2.0 * sqrtA * alpha)
                a0 = (a + 1.0) - (a - 1.0) * cs + 2.0 * sqrtA * alpha
                ta1 = 2.0 * ((a - 1.0) - (a + 1.0) * cs)
                ta2 = (a + 1.0) - (a - 1.0) * cs - 2.0 * sqrtA * alpha
            }
            FilterType.LOW_SHELF -> {
                val sqrtA = sqrt(a)
                tb0 = a * ((a + 1.0) - (a - 1.0) * cs + 2.0 * sqrtA * alpha)
                tb1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cs)
                tb2 = a * ((a + 1.0) - (a - 1.0) * cs - 2.0 * sqrtA * alpha)
                a0 = (a + 1.0) + (a - 1.0) * cs + 2.0 * sqrtA * alpha
                ta1 = -2.0 * ((a - 1.0) + (a + 1.0) * cs)
                ta2 = (a + 1.0) + (a - 1.0) * cs - 2.0 * sqrtA * alpha
            }
            FilterType.NOTCH -> {
                tb0 = 1.0
                tb1 = -2.0 * cs
                tb2 = 1.0
                a0 = 1.0 + alpha
                ta1 = -2.0 * cs
                ta2 = 1.0 - alpha
            }
        }

        b0 = (tb0 / a0).toFloat()
        b1 = (tb1 / a0).toFloat()
        b2 = (tb2 / a0).toFloat()
        this.a1 = (ta1 / a0).toFloat()
        this.a2 = (ta2 / a0).toFloat()
    }

    /**
     * Memproses satu frame stereo [inL, inR] secara berurutan.
     */
    fun processSample(inL: Float, inR: Float): Pair<Float, Float> {
        val outL = b0 * inL + z1L
        z1L = b1 * inL - a1 * outL + z2L
        z2L = b2 * inL - a2 * outL

        val outR = b0 * inR + z1R
        z1R = b1 * inR - a1 * outR + z2R
        z2R = b2 * inR - a2 * outR

        return Pair(
            if (outL.isNaN() || outL.isInfinite()) inL else outL,
            if (outR.isNaN() || outR.isInfinite()) inR else outR
        )
    }

    /**
     * Memproses buffer interleaved stereo [samples] di tempat (in-place).
     */
    fun processInterleaved(samples: FloatArray, channels: Int = 2) {
        if (channels == 2) {
            val totalFrames = samples.size / 2
            for (f in 0 until totalFrames) {
                val idxL = f * 2
                val idxR = idxL + 1
                val (outL, outR) = processSample(samples[idxL], samples[idxR])
                samples[idxL] = outL
                samples[idxR] = outR
            }
        } else if (channels == 1) {
            for (i in samples.indices) {
                val inS = samples[i]
                val outS = b0 * inS + z1L
                z1L = b1 * inS - a1 * outS + z2L
                z2L = b2 * inS - a2 * outS
                samples[i] = if (outS.isNaN() || outS.isInfinite()) inS else outS
            }
        }
    }

    fun reset() {
        z1L = 0.0f
        z2L = 0.0f
        z1R = 0.0f
        z2R = 0.0f
    }
}
