package com.autoremix.djslow.engine.mix

import com.autoremix.djslow.engine.dsp.BiquadFilter
import com.autoremix.djslow.engine.dsp.DynamicsCompressor
import com.autoremix.djslow.engine.dsp.StereoEngine
import com.autoremix.djslow.engine.pcm.AudioPcmData
import kotlin.math.abs
import kotlin.math.max

/**
 * Pemroses Vokal Profesional (Tahap 5).
 * Alur:
 * 1. High Pass Filter (85 Hz) untuk membuang low-rumble & DC offset
 * 2. EQ Vokal (Mud scoop di 320 Hz, presence di 3.4 kHz, air shelf di 10.5 kHz)
 * 3. Kompresi Vokal Ringan (ratio 2.5:1, dynamic leveling tanpa membuat vokal gepeng)
 * 4. De-esser Lembut (menjinakkan sibilansi frekuensi 6.5 - 9 kHz)
 * 5. Reverb / Ambience Halus (memberikan kedalaman ruang tanpa menenggelamkan vokal dari posisi tengah)
 */
object VocalProcessor {

    fun process(vocalPcm: AudioPcmData): AudioPcmData {
        val samples = vocalPcm.samples.copyOf()
        val sampleRate = vocalPcm.sampleRate
        val channels = vocalPcm.channels

        if (samples.isEmpty() || vocalPcm.isSilent()) {
            return vocalPcm
        }

        // 1. High Pass Filter (85 Hz)
        val hpf = BiquadFilter(
            type = BiquadFilter.FilterType.HIGH_PASS,
            frequencyHz = 85.0f,
            sampleRate = sampleRate,
            q = 0.7071f
        )
        hpf.processInterleaved(samples, channels)

        // 2. EQ Vokal
        // a. Mud cut di 320 Hz (-2.0 dB)
        val mudFilter = BiquadFilter(
            type = BiquadFilter.FilterType.PEAKING_EQ,
            frequencyHz = 320.0f,
            sampleRate = sampleRate,
            q = 1.0f,
            gainDb = -2.0f
        )
        mudFilter.processInterleaved(samples, channels)

        // b. Presence boost di 3400 Hz (+2.2 dB)
        val presenceFilter = BiquadFilter(
            type = BiquadFilter.FilterType.PEAKING_EQ,
            frequencyHz = 3400.0f,
            sampleRate = sampleRate,
            q = 1.2f,
            gainDb = 2.2f
        )
        presenceFilter.processInterleaved(samples, channels)

        // c. Air shelf di 10500 Hz (+1.8 dB)
        val airFilter = BiquadFilter(
            type = BiquadFilter.FilterType.HIGH_SHELF,
            frequencyHz = 10500.0f,
            sampleRate = sampleRate,
            gainDb = 1.8f
        )
        airFilter.processInterleaved(samples, channels)

        // 3. Kompresi Vokal Ringan
        val vocalComp = DynamicsCompressor(
            thresholdDb = -18.0f,
            ratio = 2.4f,
            attackMs = 15.0f,
            releaseMs = 100.0f,
            kneeWidthDb = 4.0f,
            makeupGainDb = 0.8f,
            sampleRate = sampleRate
        )
        vocalComp.processInterleaved(samples, channels)

        // 4. De-esser Lembut
        applyDeEsser(samples, sampleRate, channels)

        // 5. Reverb / Ambience Halus (wet 12%)
        if (channels == 2) {
            StereoEngine.applyStereoAmbience(
                samples = samples,
                sampleRate = sampleRate,
                delayMs = 28.0f,
                feedback = 0.10f,
                wetLevel = 0.12f
            )
        }

        return AudioPcmData(samples, sampleRate, channels)
    }

    /**
     * De-esser berbasis deteksi energi sibilansi frekuensi tinggi (7 kHz).
     */
    private fun applyDeEsser(samples: FloatArray, sampleRate: Int, channels: Int) {
        val totalFrames = samples.size / channels
        val sibilanceFilter = BiquadFilter(
            type = BiquadFilter.FilterType.PEAKING_EQ,
            frequencyHz = 7200.0f,
            sampleRate = sampleRate,
            q = 2.0f,
            gainDb = 6.0f // boost untuk deteksi sensitif
        )
        val detectionCopy = samples.copyOf()
        sibilanceFilter.processInterleaved(detectionCopy, channels)

        var deEssGain = 1.0f
        val attack = 0.20f
        val release = 0.05f

        for (f in 0 until totalFrames) {
            val idxL = f * channels
            val idxR = if (channels > 1) idxL + 1 else idxL

            val sibLevel = max(abs(detectionCopy[idxL]), abs(detectionCopy[idxR]))
            val targetGain = if (sibLevel > 0.40f) {
                // Reduksi sibilansi maksimal ~3.5 dB
                val over = (sibLevel - 0.40f) * 1.5f
                (1.0f - over.coerceIn(0.0f, 0.32f))
            } else {
                1.0f
            }

            deEssGain = if (targetGain < deEssGain) {
                (1.0f - attack) * deEssGain + attack * targetGain
            } else {
                (1.0f - release) * deEssGain + release * targetGain
            }

            samples[idxL] *= deEssGain
            if (channels > 1) {
                samples[idxR] *= deEssGain
            }
        }
    }
}
