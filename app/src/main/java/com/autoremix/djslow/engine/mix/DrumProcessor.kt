package com.autoremix.djslow.engine.mix

import com.autoremix.djslow.engine.dsp.BiquadFilter
import com.autoremix.djslow.engine.dsp.DynamicsCompressor
import com.autoremix.djslow.engine.dsp.Limiter
import com.autoremix.djslow.engine.pcm.AudioPcmData

/**
 * Pemroses Bus Drum & Perkusi (Tahap 5).
 * - EQ: Kick punchy (70 Hz), kotak low-mid bersih (400 Hz), snare snap renyah (2.4 kHz), hi-hat halus tanpa menusuk
 * - Kompresi Punch: Attack lambat (25ms) untuk meloloskan transient ketukan, release cepat (80ms)
 * - Limiter Ringan: Mencegah lonjakan transient rimshot/snare liar
 */
object DrumProcessor {

    fun process(drumPcm: AudioPcmData): AudioPcmData {
        val samples = drumPcm.samples.copyOf()
        val sampleRate = drumPcm.sampleRate
        val channels = drumPcm.channels

        if (samples.isEmpty() || drumPcm.isSilent()) {
            return drumPcm
        }

        // 1. EQ DRUM
        // a. Kick punch boost (70 Hz, +1.8 dB)
        val kickFilter = BiquadFilter(
            type = BiquadFilter.FilterType.PEAKING_EQ,
            frequencyHz = 70.0f,
            sampleRate = sampleRate,
            q = 1.2f,
            gainDb = 1.8f
        )
        kickFilter.processInterleaved(samples, channels)

        // b. Mud/Boxiness cut (400 Hz, -1.8 dB)
        val boxCut = BiquadFilter(
            type = BiquadFilter.FilterType.PEAKING_EQ,
            frequencyHz = 400.0f,
            sampleRate = sampleRate,
            q = 1.0f,
            gainDb = -1.8f
        )
        boxCut.processInterleaved(samples, channels)

        // c. Snare snap & presence (2400 Hz, +1.5 dB)
        val snareSnap = BiquadFilter(
            type = BiquadFilter.FilterType.PEAKING_EQ,
            frequencyHz = 2400.0f,
            sampleRate = sampleRate,
            q = 1.1f,
            gainDb = 1.5f
        )
        snareSnap.processInterleaved(samples, channels)

        // d. Hi-hat roll-off halus (> 13.5 kHz, -1.5 dB) agar tidak menusuk telinga
        val cymbalTame = BiquadFilter(
            type = BiquadFilter.FilterType.HIGH_SHELF,
            frequencyHz = 13500.0f,
            sampleRate = sampleRate,
            gainDb = -1.5f
        )
        cymbalTame.processInterleaved(samples, channels)

        // 2. KOMPRESI PUNCH (Punch Compressor)
        val punchComp = DynamicsCompressor(
            thresholdDb = -14.0f,
            ratio = 2.8f,
            attackMs = 25.0f, // transient lolos
            releaseMs = 80.0f,
            kneeWidthDb = 4.0f,
            makeupGainDb = 0.5f,
            sampleRate = sampleRate
        )
        punchComp.processInterleaved(samples, channels)

        // 3. LIMITER TRANSIENT RINGAN
        val drumLimiter = Limiter(
            ceilingDbtp = -1.0f,
            lookaheadMs = 2.0f,
            releaseMs = 45.0f,
            sampleRate = sampleRate
        )
        drumLimiter.processInterleaved(samples, channels)

        return AudioPcmData(samples, sampleRate, channels)
    }
}
