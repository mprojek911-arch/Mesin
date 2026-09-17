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

    /**
     * Mengembalikan AudioPcmData dengan volume diskalakan.
     */
    fun scaled(volume: Float): AudioPcmData {
        if (volume == 1.0f) return this
        val newSamples = FloatArray(samples.size)
        for (i in samples.indices) {
            newSamples[i] = samples[i] * volume
        }
        return AudioPcmData(newSamples, sampleRate, channels)
    }

    /**
     * Memotong potongan frame (chunk slicing) untuk streaming dan pemrosesan audio hemat memori.
     */
    fun slice(startFrame: Int, frameCount: Int): AudioPcmData {
        val safeStart = startFrame.coerceIn(0, totalFrames)
        val safeCount = frameCount.coerceIn(0, totalFrames - safeStart)
        val startSample = safeStart * channels
        val sampleCount = safeCount * channels
        val sub = FloatArray(sampleCount)
        System.arraycopy(samples, startSample, sub, 0, sampleCount)
        return AudioPcmData(sub, sampleRate, channels)
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

    companion object {
        fun createEmpty(sampleRate: Int = 44100, channels: Int = 2): AudioPcmData {
            return AudioPcmData(FloatArray(0), sampleRate, channels)
        }
    }
}
