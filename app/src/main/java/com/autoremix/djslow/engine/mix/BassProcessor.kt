package com.autoremix.djslow.engine.mix

import com.autoremix.djslow.engine.dsp.BiquadFilter
import com.autoremix.djslow.engine.dsp.DynamicsCompressor
import com.autoremix.djslow.engine.dsp.Limiter
import com.autoremix.djslow.engine.dsp.StereoEngine
import com.autoremix.djslow.engine.pcm.AudioPcmData
import kotlin.math.tanh

/**
 * Pemroses Sub-Bass Profesional (Tahap 5).
 * - High Pass Filter (30 Hz steep cut): Menghilangkan rumble sub infrasonik yang tidak berguna.
 * - EQ Pemisah Kick/Bass: Memotong frekuensi 75 Hz (kick punch center) sebesar -2.5 dB agar kick & bass tidak bertabrakan.
 * - Low Pass Filter (2.8 kHz): Membuang noise frekuensi tinggi/fizz digital.
 * - Kompresi Stabil: Bass kokoh dan mantap (ratio 3.2:1, attack 12ms, release 70ms).
 * - Saturasi Analog Lunak: Membangkitkan harmoni ke-2 dan ke-3 agar terdengar bulat di speaker HP/laptop.
 * - Limiter Ringan: Mencegah lonjakan bass liar.
 * - Mono Sub (< 140 Hz): Penjamin 100% kompatibilitas mono subwoofer.
 */
object BassProcessor {

    fun process(bassPcm: AudioPcmData): AudioPcmData {
        val samples = bassPcm.samples.copyOf()
        val sampleRate = bassPcm.sampleRate
        val channels = bassPcm.channels

        if (samples.isEmpty() || bassPcm.isSilent()) {
            return bassPcm
        }

        // 1. HPF 30 Hz (Infrasonic rumble cut)
        val hpf30 = BiquadFilter(
            type = BiquadFilter.FilterType.HIGH_PASS,
            frequencyHz = 30.0f,
            sampleRate = sampleRate,
            q = 0.7071f
        )
        hpf30.processInterleaved(samples, channels)

        // 2. EQ Pemisah Kick/Bass: Notch/Dip di 75 Hz (-2.5 dB)
        val kickSeparationDip = BiquadFilter(
            type = BiquadFilter.FilterType.PEAKING_EQ,
            frequencyHz = 75.0f,
            sampleRate = sampleRate,
            q = 1.4f,
            gainDb = -2.5f
        )
        kickSeparationDip.processInterleaved(samples, channels)

        // 3. Sub-bass Warmth Boost di 55 Hz (+1.8 dB)
        val subWarmth = BiquadFilter(
            type = BiquadFilter.FilterType.PEAKING_EQ,
            frequencyHz = 55.0f,
            sampleRate = sampleRate,
            q = 1.2f,
            gainDb = 1.8f
        )
        subWarmth.processInterleaved(samples, channels)

        // 4. LPF Anti-Fizz di 2800 Hz
        val lpfAntiFizz = BiquadFilter(
            type = BiquadFilter.FilterType.LOW_PASS,
            frequencyHz = 2800.0f,
            sampleRate = sampleRate,
            q = 0.7071f
        )
        lpfAntiFizz.processInterleaved(samples, channels)

        // 5. Kompresi Bass Stabil
        val bassComp = DynamicsCompressor(
            thresholdDb = -15.0f,
            ratio = 3.2f,
            attackMs = 12.0f,
            releaseMs = 70.0f,
            kneeWidthDb = 4.0f,
            makeupGainDb = 0.5f,
            sampleRate = sampleRate
        )
        bassComp.processInterleaved(samples, channels)

        // 6. Saturasi Analog Ringan (Harmonics generator untuk speaker HP)
        val satDrive = 0.14f
        for (i in samples.indices) {
            val s = samples[i]
            val sat = tanh(s * (1.0f + satDrive))
            samples[i] = (1.0f - satDrive) * s + satDrive * sat
        }

        // 7. Mono Sub-Bass (< 140 Hz)
        if (channels == 2) {
            StereoEngine.monoBass(samples, sampleRate, 140.0f)
        }

        // 8. Limiter Bass Ringan
        val bassLimiter = Limiter(
            ceilingDbtp = -1.2f,
            lookaheadMs = 2.0f,
            releaseMs = 50.0f,
            sampleRate = sampleRate
        )
        bassLimiter.processInterleaved(samples, channels)

        return AudioPcmData(samples, sampleRate, channels)
    }
}
