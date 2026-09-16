package com.autoremix.djslow.engine.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * Kompresor Dinamika Audio Berkualitas Studio (Soft-Knee Peak/RMS Compressor).
 * Digunakan untuk:
 * - Kompresi vokal ringan
 * - Bus glue kompresi pada drum dan master
 * - Menjaga konsistensi dynamic range tanpa pumping
 */
class DynamicsCompressor(
    val thresholdDb: Float = -16.0f,
    val ratio: Float = 2.5f,
    val attackMs: Float = 20.0f,
    val releaseMs: Float = 120.0f,
    val kneeWidthDb: Float = 4.0f,
    val makeupGainDb: Float = 0.0f,
    val sampleRate: Int = 44100
) {
    private val attackAlpha = exp(-1.0 / (attackMs * 0.001 * sampleRate)).toFloat()
    private val releaseAlpha = exp(-1.0 / (releaseMs * 0.001 * sampleRate)).toFloat()
    private val makeupLinear = 10.0f.pow(makeupGainDb / 20.0f)

    private var envelopeDb = -96.0f

    /**
     * Menghitung gain reduction untuk level input [inputDb] dengan kurva soft-knee.
     */
    fun computeGainReductionDb(inputDb: Float): Float {
        val halfKnee = kneeWidthDb / 2.0f
        return when {
            inputDb <= thresholdDb - halfKnee -> 0.0f
            inputDb >= thresholdDb + halfKnee -> {
                (inputDb - thresholdDb) * (1.0f - 1.0f / ratio)
            }
            else -> {
                // Soft-knee kuadratik
                val x = inputDb - thresholdDb + halfKnee
                val delta = (x * x) / (2.0f * kneeWidthDb)
                delta * (1.0f - 1.0f / ratio)
            }
        }
    }

    /**
     * Memproses buffer interleaved stereo [samples] di tempat (in-place).
     */
    fun processInterleaved(samples: FloatArray, channels: Int = 2) {
        val totalFrames = samples.size / channels
        var currentEnvDb = envelopeDb

        for (f in 0 until totalFrames) {
            val idxL = f * channels
            val idxR = if (channels > 1) idxL + 1 else idxL

            val peakLinear = max(abs(samples[idxL]), abs(samples[idxR]))
            val peakDb = if (peakLinear > 1e-5f) 20.0f * log10(peakLinear) else -100.0f

            // Envelope detection dengan attack/release terpisah
            currentEnvDb = if (peakDb > currentEnvDb) {
                attackAlpha * currentEnvDb + (1.0f - attackAlpha) * peakDb
            } else {
                releaseAlpha * currentEnvDb + (1.0f - releaseAlpha) * peakDb
            }

            val grDb = computeGainReductionDb(currentEnvDb)
            val grLinear = 10.0f.pow(-grDb / 20.0f) * makeupLinear

            samples[idxL] *= grLinear
            if (channels > 1) {
                samples[idxR] *= grLinear
            }
        }

        envelopeDb = currentEnvDb
    }

    fun reset() {
        envelopeDb = -96.0f
    }
}
