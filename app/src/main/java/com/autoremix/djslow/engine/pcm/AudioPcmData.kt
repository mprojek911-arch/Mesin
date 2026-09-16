package com.autoremix.djslow.engine.pcm

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Representasi buffer audio internal PCM Float32 stereo 44.1 kHz.
 * Sampel bernilai normal dalam rentang [-1.0f, 1.0f].
 * Susunan interleaved: [L0, R0, L1, R1, L2, R2, ...].
 */
data class AudioPcmData(
    val samples: FloatArray,
    val sampleRate: Int = 44100,
    val channels: Int = 2
) {
    val totalFrames: Int
        get() = samples.size / channels

    val durationMs: Long
        get() = if (sampleRate > 0 && channels > 0) {
            (totalFrames.toLong() * 1000L) / sampleRate.toLong()
        } else {
            0L
        }

    /**
     * Menghitung nilai puncak maksimum (peak amplitude).
     */
    fun calculatePeak(): Float {
        var peak = 0.0f
        for (i in samples.indices) {
            val s = abs(samples[i])
            if (s > peak) peak = s
        }
        return peak
    }

    /**
     * Menghitung Root Mean Square (RMS) untuk estimasi tingkat keras (loudness).
     */
    fun calculateRms(): Float {
        if (samples.isEmpty()) return 0.0f
        var sumSquares = 0.0
        for (i in samples.indices) {
            val s = samples[i].toDouble()
            sumSquares += s * s
        }
        return sqrt(sumSquares / samples.size).toFloat()
    }

    /**
     * Memeriksa apakah audio berisi keheningan total atau sangat hening (peak < threshold).
     */
    fun isSilent(threshold: Float = 0.001f): Boolean {
        return calculatePeak() < threshold
    }

    /**
     * Memeriksa apakah data mengandung nilai NaN atau tak terhingga (Infinity).
     */
    fun hasInvalidValues(): Boolean {
        for (i in samples.indices) {
            val s = samples[i]
            if (s.isNaN() || s.isInfinite()) return true
        }
        return false
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioPcmData

        if (sampleRate != other.sampleRate) return false
        if (channels != other.channels) return false
        if (!samples.contentEquals(other.samples)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = samples.contentHashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channels
        return result
    }
}
