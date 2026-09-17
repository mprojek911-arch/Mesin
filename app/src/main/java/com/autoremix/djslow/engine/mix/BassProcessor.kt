package com.autoremix.djslow.engine.mix

import com.autoremix.djslow.engine.dsp.BiquadFilter
import com.autoremix.djslow.engine.dsp.DynamicsCompressor
import com.autoremix.djslow.engine.dsp.Limiter
import com.autoremix.djslow.engine.dsp.StereoEngine
import com.autoremix.djslow.engine.pcm.AudioPcmData
import kotlin.math.tanh

/**
 * State DSP Bass yang persisten untuk pemrosesan streaming per blok.
 */
class BassBlockProcessor(val sampleRate: Int = 44100, val channels: Int = 2) {
    private val hpf30 = BiquadFilter(
        type = BiquadFilter.FilterType.HIGH_PASS,
        frequencyHz = 30.0f,
        sampleRate = sampleRate,
        q = 0.7071f
    )
    private val kickSeparationDip = BiquadFilter(
        type = BiquadFilter.FilterType.PEAKING_EQ,
        frequencyHz = 75.0f,
        sampleRate = sampleRate,
        q = 1.4f,
        gainDb = -2.5f
    )
    private val subWarmth = BiquadFilter(
        type = BiquadFilter.FilterType.PEAKING_EQ,
        frequencyHz = 55.0f,
        sampleRate = sampleRate,
        q = 1.2f,
        gainDb = 1.8f
    )
    private val lpfAntiFizz = BiquadFilter(
        type = BiquadFilter.FilterType.LOW_PASS,
        frequencyHz = 2800.0f,
        sampleRate = sampleRate,
        q = 0.7071f
    )
    private val bassComp = DynamicsCompressor(
        thresholdDb = -15.0f,
        ratio = 3.2f,
        attackMs = 12.0f,
        releaseMs = 70.0f,
        kneeWidthDb = 4.0f,
        makeupGainDb = 0.5f,
        sampleRate = sampleRate
    )
    private val bassLimiter = Limiter(
        ceilingDbtp = -1.2f,
        lookaheadMs = 2.0f,
        releaseMs = 50.0f,
        sampleRate = sampleRate
    )

    fun processBlock(blockSamples: FloatArray) {
        if (blockSamples.isEmpty()) return

        hpf30.processInterleaved(blockSamples, channels)
        kickSeparationDip.processInterleaved(blockSamples, channels)
        subWarmth.processInterleaved(blockSamples, channels)
        lpfAntiFizz.processInterleaved(blockSamples, channels)
        bassComp.processInterleaved(blockSamples, channels)

        // Saturasi Analog Ringan
        val satDrive = 0.14f
        for (i in blockSamples.indices) {
            val s = blockSamples[i]
            val sat = tanh(s * (1.0f + satDrive))
            blockSamples[i] = (1.0f - satDrive) * s + satDrive * sat
        }

        // Mono Sub-Bass (< 140 Hz)
        if (channels == 2) {
            StereoEngine.monoBass(blockSamples, sampleRate, 140.0f)
        }

        bassLimiter.processInterleaved(blockSamples, channels)
    }
}

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

    fun process(bassPcm: AudioPcmData, inPlace: Boolean = true): AudioPcmData {
        val samples = if (inPlace) bassPcm.samples else bassPcm.samples.copyOf()
        val sampleRate = bassPcm.sampleRate
        val channels = bassPcm.channels

        if (samples.isEmpty() || bassPcm.isSilent()) {
            return bassPcm
        }

        val processor = BassBlockProcessor(sampleRate, channels)
        processor.processBlock(samples)

        return AudioPcmData(samples, sampleRate, channels)
    }
}
