package com.autoremix.djslow.engine.core

import com.autoremix.djslow.engine.dsp.LoudnessMeter
import com.autoremix.djslow.engine.mastering.AutoMasteringEngine
import com.autoremix.djslow.engine.mastering.MasteringPreset
import com.autoremix.djslow.engine.pcm.AudioPcmData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 9. MASTER ENGINE
 * Rantai mastering audio presisi dengan proteksi anti-OOM, anti-clipping, dan pengukuran LUFS nyata:
 * - EQ Tonal Balance (Sub Cut, Bass Warmth, Mud Scoop, Presence, Air)
 * - Glue Compressor
 * - Analog Tape Saturation (Tanh)
 * - Stereo Imaging (Mono Sub < 120 Hz & Side Widening)
 * - True Peak Lookahead Brickwall Limiter (-0.5 dBFS Ceiling)
 * - Loudness Metering Nyata (LUFS, True Peak, RMS)
 */
object MasterEngine {

    data class MasteredOutput(
        val pcmData: AudioPcmData,
        val loudnessReport: LoudnessMeter.LoudnessReport,
        val preset: MasteringPreset
    )

    /**
     * Memproses mastering audio stereo secara aman dengan chunking memory jika durasi panjang.
     */
    suspend fun masterAudio(
        inputPcm: AudioPcmData,
        preset: MasteringPreset = MasteringPreset.DJ_SLOW,
        onProgress: ((Float, String) -> Unit)? = null
    ): Result<MasteredOutput> = withContext(Dispatchers.Default) {
        try {
            if (inputPcm.samples.isEmpty() || inputPcm.isSilent()) {
                return@withContext Result.failure(IllegalArgumentException("Audio input mastering kosong atau hening."))
            }

            onProgress?.invoke(0.20f, "Menerapkan Tonal EQ & Glue Compressor (${preset.label})...")

            // Panggil AutoMasteringEngine DSP nyata
            val result = AutoMasteringEngine.master(inputPcm, preset)
            if (result.isFailure) {
                return@withContext Result.failure(result.exceptionOrNull()!!)
            }

            val masterResult = result.getOrThrow()
            onProgress?.invoke(0.85f, "Memvalidasi True Peak (-0.5 dBFS) & Loudness (${preset.targetLufs} LUFS)...")

            val output = MasteredOutput(
                pcmData = masterResult.masteredPcm,
                loudnessReport = masterResult.report,
                preset = preset
            )

            onProgress?.invoke(1.0f, "Mastering selesai: True Peak ${String.format("%.2f", output.loudnessReport.truePeakDbtp)} dBTP, Loudness ${String.format("%.1f", output.loudnessReport.lufsIntegrated)} LUFS")
            Result.success(output)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Mastering gagal: ${e.localizedMessage ?: e.message}"))
        }
    }
}
