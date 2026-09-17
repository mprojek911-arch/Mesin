package com.autoremix.djslow.engine.mastering

import com.autoremix.djslow.engine.dsp.BiquadFilter
import com.autoremix.djslow.engine.dsp.DynamicsCompressor
import com.autoremix.djslow.engine.dsp.Limiter
import com.autoremix.djslow.engine.dsp.LoudnessMeter
import com.autoremix.djslow.engine.dsp.StereoEngine
import com.autoremix.djslow.engine.pcm.AudioPcmData
import kotlin.math.abs
import kotlin.math.tanh

/**
 * Processor Mastering berbasis blok yang menjaga state filter, kompresor, dan limiter antar-blok secara mulus.
 */
class MasterBlockProcessor(
    val preset: MasteringPreset = MasteringPreset.DJ_SLOW,
    val sampleRate: Int = 44100,
    val channels: Int = 2
) {
    private val subCutFilter = BiquadFilter(
        type = BiquadFilter.FilterType.HIGH_PASS,
        frequencyHz = preset.eqSubCutHz,
        sampleRate = sampleRate,
        q = 0.7071f
    )
    private val bassFilter = if (abs(preset.eqBassGainDb) > 0.1f) {
        BiquadFilter(
            type = BiquadFilter.FilterType.LOW_SHELF,
            frequencyHz = 85.0f,
            sampleRate = sampleRate,
            gainDb = preset.eqBassGainDb
        )
    } else null

    private val mudFilter = if (abs(preset.eqLowMidCutDb) > 0.1f) {
        BiquadFilter(
            type = BiquadFilter.FilterType.PEAKING_EQ,
            frequencyHz = 300.0f,
            sampleRate = sampleRate,
            q = 1.1f,
            gainDb = preset.eqLowMidCutDb
        )
    } else null

    private val presenceFilter = if (abs(preset.eqPresenceGainDb) > 0.1f) {
        BiquadFilter(
            type = BiquadFilter.FilterType.PEAKING_EQ,
            frequencyHz = 3500.0f,
            sampleRate = sampleRate,
            q = 1.0f,
            gainDb = preset.eqPresenceGainDb
        )
    } else null

    private val airFilter = if (abs(preset.eqAirGainDb) > 0.1f) {
        BiquadFilter(
            type = BiquadFilter.FilterType.HIGH_SHELF,
            frequencyHz = 11000.0f,
            sampleRate = sampleRate,
            gainDb = preset.eqAirGainDb
        )
    } else null

    private val glueComp = DynamicsCompressor(
        thresholdDb = preset.glueThresholdDb,
        ratio = preset.glueRatio,
        attackMs = 30.0f,
        releaseMs = 100.0f,
        kneeWidthDb = 4.0f,
        makeupGainDb = 0.5f,
        sampleRate = sampleRate
    )

    private val limiter = Limiter(
        ceilingDbtp = preset.peakCeilingDbtp,
        lookaheadMs = 2.5f,
        releaseMs = 60.0f,
        sampleRate = sampleRate
    )

    private val monoBassFilter = BiquadFilter(
        type = BiquadFilter.FilterType.LOW_PASS,
        frequencyHz = 120.0f,
        sampleRate = sampleRate,
        q = 0.7071f
    )
    private val monoBassBuffer = FloatArray(16384 * channels)

    fun processBlock(blockSamples: FloatArray) {
        if (blockSamples.isEmpty()) return

        // 1. EQ Tonal Balance
        subCutFilter.processInterleaved(blockSamples, channels)
        bassFilter?.processInterleaved(blockSamples, channels)
        mudFilter?.processInterleaved(blockSamples, channels)
        presenceFilter?.processInterleaved(blockSamples, channels)
        airFilter?.processInterleaved(blockSamples, channels)

        // 2. Glue Compression
        glueComp.processInterleaved(blockSamples, channels)

        // 3. Saturation Ringan
        if (preset.saturationDrive > 0.01f) {
            val drive = preset.saturationDrive
            for (i in blockSamples.indices) {
                val s = blockSamples[i]
                val sat = tanh(s * (1.0f + drive))
                blockSamples[i] = (1.0f - drive) * s + drive * sat
            }
        }

        // 4. Stereo Control
        if (channels == 2) {
            val buf = if (blockSamples.size <= monoBassBuffer.size) monoBassBuffer else FloatArray(blockSamples.size)
            StereoEngine.monoBass(blockSamples, monoBassFilter, buf)
            StereoEngine.adjustWidth(blockSamples, preset.stereoWidth)
        }

        // 5. Lookahead Brickwall Limiter
        limiter.processInterleaved(blockSamples, channels)

        // 6. Peak Protection
        val ceilingLinear = limiter.ceilingLinear
        for (i in blockSamples.indices) {
            val s = blockSamples[i]
            if (s.isNaN() || s.isInfinite()) {
                blockSamples[i] = 0.0f
            } else if (s > ceilingLinear) {
                blockSamples[i] = ceilingLinear
            } else if (s < -ceilingLinear) {
                blockSamples[i] = -ceilingLinear
            }
        }
    }
}

/**
 * Rantai Auto Mastering Studio (Tahap 5).
 * Alur Pemrosesan Nyata:
 * 1. EQ Tonal Balance (Sub cut -> Bass warmth -> Mud dip -> Presence -> Air shelf)
 * 2. Glue Compression Ringan (VCA bus compressor)
 * 3. Saturation Sangat Ringan (Warm analog harmonic saturation)
 * 4. Stereo Control (Mono sub bass < 120 Hz & stereo widening)
 * 5. Lookahead Brickwall Limiter
 * 6. Peak Protection & Pengukuran Loudness Nyata (LUFS, True Peak, RMS)
 */
object AutoMasteringEngine {

    data class MasteringResult(
        val masteredPcm: AudioPcmData,
        val report: LoudnessMeter.LoudnessReport,
        val presetUsed: MasteringPreset
    )

    fun master(
        inputPcm: AudioPcmData,
        preset: MasteringPreset = MasteringPreset.DJ_SLOW,
        inPlace: Boolean = true
    ): Result<MasteringResult> {
        val samples = if (inPlace) inputPcm.samples else inputPcm.samples.copyOf()
        val sampleRate = inputPcm.sampleRate
        val channels = inputPcm.channels

        if (samples.isEmpty()) {
            return Result.failure(IllegalArgumentException("Buffer audio mastering kosong."))
        }

        val processor = MasterBlockProcessor(preset, sampleRate, channels)
        processor.processBlock(samples)

        val masteredPcm = AudioPcmData(samples, sampleRate, channels)
        val report = LoudnessMeter.analyze(masteredPcm)

        return Result.success(
            MasteringResult(
                masteredPcm = masteredPcm,
                report = report,
                presetUsed = preset
            )
        )
    }
}
