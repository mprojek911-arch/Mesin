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
        preset: MasteringPreset = MasteringPreset.DJ_SLOW
    ): Result<MasteringResult> {
        val samples = inputPcm.samples.copyOf()
        val sampleRate = inputPcm.sampleRate
        val channels = inputPcm.channels

        if (samples.isEmpty()) {
            return Result.failure(IllegalArgumentException("Buffer audio mastering kosong."))
        }

        // ==========================================
        // 1. EQ TONAL BALANCE
        // ==========================================
        // a. Pemotong sub-rumble frekuensi tidak terdengar (< preset.eqSubCutHz)
        val subCutFilter = BiquadFilter(
            type = BiquadFilter.FilterType.HIGH_PASS,
            frequencyHz = preset.eqSubCutHz,
            sampleRate = sampleRate,
            q = 0.7071f
        )
        subCutFilter.processInterleaved(samples, channels)

        // b. Low Shelf Bass Warmth (85 Hz)
        if (abs(preset.eqBassGainDb) > 0.1f) {
            val bassFilter = BiquadFilter(
                type = BiquadFilter.FilterType.LOW_SHELF,
                frequencyHz = 85.0f,
                sampleRate = sampleRate,
                gainDb = preset.eqBassGainDb
            )
            bassFilter.processInterleaved(samples, channels)
        }

        // c. Low-Mid Mud Scoop (300 Hz) untuk membersihkan kekeruhan
        if (abs(preset.eqLowMidCutDb) > 0.1f) {
            val mudFilter = BiquadFilter(
                type = BiquadFilter.FilterType.PEAKING_EQ,
                frequencyHz = 300.0f,
                sampleRate = sampleRate,
                q = 1.1f,
                gainDb = preset.eqLowMidCutDb
            )
            mudFilter.processInterleaved(samples, channels)
        }

        // d. Presence Boost (3.5 kHz) untuk kejelasan vokal & instrumen
        if (abs(preset.eqPresenceGainDb) > 0.1f) {
            val presenceFilter = BiquadFilter(
                type = BiquadFilter.FilterType.PEAKING_EQ,
                frequencyHz = 3500.0f,
                sampleRate = sampleRate,
                q = 1.0f,
                gainDb = preset.eqPresenceGainDb
            )
            presenceFilter.processInterleaved(samples, channels)
        }

        // e. High Shelf Air (11 kHz) untuk kilau frekuensi tinggi tanpa menusuk telinga
        if (abs(preset.eqAirGainDb) > 0.1f) {
            val airFilter = BiquadFilter(
                type = BiquadFilter.FilterType.HIGH_SHELF,
                frequencyHz = 11000.0f,
                sampleRate = sampleRate,
                gainDb = preset.eqAirGainDb
            )
            airFilter.processInterleaved(samples, channels)
        }

        // ==========================================
        // 2. GLUE COMPRESSION RINGAN
        // ==========================================
        val glueComp = DynamicsCompressor(
            thresholdDb = preset.glueThresholdDb,
            ratio = preset.glueRatio,
            attackMs = 30.0f,
            releaseMs = 100.0f,
            kneeWidthDb = 4.0f,
            makeupGainDb = 0.5f,
            sampleRate = sampleRate
        )
        glueComp.processInterleaved(samples, channels)

        // ==========================================
        // 3. SATURATION SANGAT RINGAN (Warm Harmonics)
        // ==========================================
        if (preset.saturationDrive > 0.01f) {
            val drive = preset.saturationDrive
            for (i in samples.indices) {
                val s = samples[i]
                // Saturasi tanh lunak berbobot rendah: out = (1-drive)*s + drive*tanh(s*1.2)
                val sat = tanh(s * (1.0f + drive))
                samples[i] = (1.0f - drive) * s + drive * sat
            }
        }

        // ==========================================
        // 4. STEREO CONTROL
        // ==========================================
        if (channels == 2) {
            // Mono-kan bass di bawah 120 Hz untuk stabilitas mono & speaker club
            StereoEngine.monoBass(samples, sampleRate = sampleRate, crossoverHz = 120.0f)
            // Sesuaikan lebar stereo master
            StereoEngine.adjustWidth(samples, preset.stereoWidth)
        }

        // ==========================================
        // 5. BRICKWALL LOOKAHEAD LIMITER
        // ==========================================
        val limiter = Limiter(
            ceilingDbtp = preset.peakCeilingDbtp,
            lookaheadMs = 2.5f,
            releaseMs = 60.0f,
            sampleRate = sampleRate
        )
        limiter.processInterleaved(samples, channels)

        // ==========================================
        // 6. PEAK PROTECTION & PENGUKURAN LOUDNESS
        // ==========================================
        // Hard peak guarantee
        val ceilingLinear = limiter.ceilingLinear
        for (i in samples.indices) {
            val s = samples[i]
            if (s.isNaN() || s.isInfinite()) {
                samples[i] = 0.0f
            } else if (s > ceilingLinear) {
                samples[i] = ceilingLinear
            } else if (s < -ceilingLinear) {
                samples[i] = -ceilingLinear
            }
        }

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
