package com.autoremix.djslow.engine.mix

import com.autoremix.djslow.engine.dsp.BiquadFilter
import com.autoremix.djslow.engine.dsp.DynamicsCompressor
import com.autoremix.djslow.engine.dsp.Limiter
import com.autoremix.djslow.engine.pcm.AudioPcmData

/**
 * State DSP Drum yang persisten untuk pemrosesan berbasis blok streaming.
 */
class DrumBlockProcessor(val sampleRate: Int = 44100, val channels: Int = 2) {
    private val kickFilter = BiquadFilter(
        type = BiquadFilter.FilterType.PEAKING_EQ,
        frequencyHz = 70.0f,
        sampleRate = sampleRate,
        q = 1.2f,
        gainDb = 1.8f
    )
    private val boxCut = BiquadFilter(
        type = BiquadFilter.FilterType.PEAKING_EQ,
        frequencyHz = 400.0f,
        sampleRate = sampleRate,
        q = 1.0f,
        gainDb = -1.8f
    )
    private val snareSnap = BiquadFilter(
        type = BiquadFilter.FilterType.PEAKING_EQ,
        frequencyHz = 2400.0f,
        sampleRate = sampleRate,
        q = 1.1f,
        gainDb = 1.5f
    )
    private val cymbalTame = BiquadFilter(
        type = BiquadFilter.FilterType.HIGH_SHELF,
        frequencyHz = 13500.0f,
        sampleRate = sampleRate,
        gainDb = -1.5f
    )
    private val punchComp = DynamicsCompressor(
        thresholdDb = -14.0f,
        ratio = 2.8f,
        attackMs = 25.0f,
        releaseMs = 80.0f,
        kneeWidthDb = 4.0f,
        makeupGainDb = 0.5f,
        sampleRate = sampleRate
    )
    private val drumLimiter = Limiter(
        ceilingDbtp = -1.0f,
        lookaheadMs = 2.0f,
        releaseMs = 45.0f,
        sampleRate = sampleRate
    )

    fun processBlock(blockSamples: FloatArray) {
        if (blockSamples.isEmpty()) return
        kickFilter.processInterleaved(blockSamples, channels)
        boxCut.processInterleaved(blockSamples, channels)
        snareSnap.processInterleaved(blockSamples, channels)
        cymbalTame.processInterleaved(blockSamples, channels)
        punchComp.processInterleaved(blockSamples, channels)
        drumLimiter.processInterleaved(blockSamples, channels)
    }
}

/**
 * Pemroses Bus Drum & Perkusi (Tahap 5).
 * - EQ: Kick punchy (70 Hz), kotak low-mid bersih (400 Hz), snare snap renyah (2.4 kHz), hi-hat halus tanpa menusuk
 * - Kompresi Punch: Attack lambat (25ms) untuk meloloskan transient ketukan, release cepat (80ms)
 * - Limiter Ringan: Mencegah lonjakan transient rimshot/snare liar
 */
object DrumProcessor {

    fun process(drumPcm: AudioPcmData, inPlace: Boolean = true): AudioPcmData {
        val samples = if (inPlace) drumPcm.samples else drumPcm.samples.copyOf()
        val sampleRate = drumPcm.sampleRate
        val channels = drumPcm.channels

        if (samples.isEmpty() || drumPcm.isSilent()) {
            return drumPcm
        }

        val processor = DrumBlockProcessor(sampleRate, channels)
        processor.processBlock(samples)

        return AudioPcmData(samples, sampleRate, channels)
    }
}
