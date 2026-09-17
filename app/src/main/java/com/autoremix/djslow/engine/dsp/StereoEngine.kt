package com.autoremix.djslow.engine.dsp

import com.autoremix.djslow.engine.pcm.AudioPcmData
import kotlin.math.sin

/**
 * Mesin Stereo Cerdas (Mid-Side Engine & Mono Compatibility).
 * Aturan Tahap 5:
 * - Center: Vokal, Kick, Bass (Sub < 140 Hz di-mono secara ketat).
 * - Stereo Ringan: Akor (+15%), Pad (+25%), Melodi (+15%), FX (+35%).
 * - Menjamin 100% kompatibilitas mono tanpa phase cancellation destruktif.
 */
object StereoEngine {

    /**
     * Memproses pelebaran stereo berbasis Mid/Side.
     * [widthMultiplier]: 1.0f = lebar asli, 0.0f = mono murni, 1.25f = lebar +25%.
     */
    fun adjustWidth(samples: FloatArray, widthMultiplier: Float) {
        val totalFrames = samples.size / 2
        val clampedWidth = widthMultiplier.coerceIn(0.0f, 1.6f)

        for (f in 0 until totalFrames) {
            val idxL = f * 2
            val idxR = idxL + 1

            val l = samples[idxL]
            val r = samples[idxR]

            // Transformasi Mid-Side
            val mid = 0.5f * (l + r)
            val side = 0.5f * (l - r)

            // Modulasi lebar stereo pada kanal Side
            val newSide = side * clampedWidth

            // Transformasi balik ke Left / Right
            samples[idxL] = mid + newSide
            samples[idxR] = mid - newSide
        }
    }

    /**
     * Menyatukan frekuensi rendah (< [crossoverHz]) menjadi mono murni (Elliptical Equalizer).
     * Mencegah masalah fase (phase cancellation) pada subwoofer, speaker mobil, dan speaker HP.
     */
    fun monoBass(samples: FloatArray, sampleRate: Int = 44100, crossoverHz: Float = 140.0f) {
        val totalFrames = samples.size / 2
        if (totalFrames <= 0) return

        // 1. Filter Low-Pass untuk memisahkan konten bass sub
        val lpFilter = BiquadFilter(
            type = BiquadFilter.FilterType.LOW_PASS,
            frequencyHz = crossoverHz,
            sampleRate = sampleRate,
            q = 0.7071f
        )

        // Salin untuk ekstraksi frekuensi rendah
        val lowSamples = samples.copyOf()
        lpFilter.processInterleaved(lowSamples, channels = 2)

        for (f in 0 until totalFrames) {
            val idxL = f * 2
            val idxR = idxL + 1

            val lowL = lowSamples[idxL]
            val lowR = lowSamples[idxR]

            // Mono bass: rata-rata L dan R
            val monoLow = 0.5f * (lowL + lowR)

            // Hitung konten high: sinyal asli dikurangi low stereo asli
            val highL = samples[idxL] - lowL
            val highR = samples[idxR] - lowR

            // Rekonstruksi sinyal: High (stereo asli) + Low (mono terpusat)
            samples[idxL] = highL + monoLow
            samples[idxR] = highR + monoLow
        }
    }

    /**
     * Versi zero-allocation untuk streaming per blok dengan reusable buffer dan stateful filter.
     */
    fun monoBass(
        samples: FloatArray,
        lpFilter: BiquadFilter,
        tempLowBuffer: FloatArray
    ) {
        val totalFrames = samples.size / 2
        if (totalFrames <= 0) return

        System.arraycopy(samples, 0, tempLowBuffer, 0, samples.size)
        lpFilter.processInterleaved(tempLowBuffer, channels = 2)

        for (f in 0 until totalFrames) {
            val idxL = f * 2
            val idxR = idxL + 1

            val lowL = tempLowBuffer[idxL]
            val lowR = tempLowBuffer[idxR]

            val monoLow = 0.5f * (lowL + lowR)
            val highL = samples[idxL] - lowL
            val highR = samples[idxR] - lowR

            samples[idxL] = highL + monoLow
            samples[idxR] = highR + monoLow
        }
    }

    /**
     * Menghasilkan ruang stereo alami (ambience micro-delay stereo) untuk Pad dan Vokal.
     * Menggunakan delay waktu yang sangat kecil (15-30ms) dengan level basah yang halus (10-15%).
     */
    fun applyStereoAmbience(
        samples: FloatArray,
        sampleRate: Int = 44100,
        delayMs: Float = 24.0f,
        feedback: Float = 0.12f,
        wetLevel: Float = 0.14f
    ) {
        val totalFrames = samples.size / 2
        val delaySamples = ((delayMs * 0.001f) * sampleRate).toInt().coerceAtLeast(1)

        val delayBufferL = FloatArray(delaySamples)
        val delayBufferR = FloatArray(delaySamples)
        var writeIdx = 0

        val clampedWet = wetLevel.coerceIn(0.0f, 0.35f)
        val clampedFb = feedback.coerceIn(0.0f, 0.40f)

        for (f in 0 until totalFrames) {
            val idxL = f * 2
            val idxR = idxL + 1

            val inL = samples[idxL]
            val inR = samples[idxR]

            // Baca dari delay buffer terbalik sedikit untuk stereo spatialisasi
            val delayedL = delayBufferL[writeIdx]
            val delayedR = delayBufferR[(writeIdx + delaySamples / 2) % delaySamples]

            // Tulis kembali dengan feedback
            delayBufferL[writeIdx] = inL + delayedL * clampedFb
            delayBufferR[writeIdx] = inR + delayedR * clampedFb

            writeIdx = (writeIdx + 1) % delaySamples

            samples[idxL] = inL * (1.0f - clampedWet * 0.5f) + delayedR * clampedWet
            samples[idxR] = inR * (1.0f - clampedWet * 0.5f) + delayedL * clampedWet
        }
    }

    /**
     * Memeriksa kesesuaian mono (mono compatibility check).
     * Mengembalikan rasio energi ketika di-sum ke mono dibanding stereo.
     * Nilai > 0.85 menandakan tidak ada phase cancellation yang merusak.
     */
    fun checkMonoCompatibility(pcm: AudioPcmData): Float {
        if (pcm.channels < 2) return 1.0f
        val samples = pcm.samples
        var stereoEnergy = 0.0
        var monoEnergy = 0.0

        val totalFrames = samples.size / 2
        val step = maxOf(1, totalFrames / 10000)

        var f = 0
        while (f < totalFrames) {
            val l = samples[f * 2].toDouble()
            val r = samples[f * 2 + 1].toDouble()
            stereoEnergy += (l * l + r * r) * 0.5
            val m = 0.5 * (l + r)
            monoEnergy += m * m
            f += step
        }

        if (stereoEnergy <= 1e-6) return 1.0f
        return (monoEnergy / stereoEnergy).toFloat().coerceIn(0.0f, 1.5f)
    }
}
