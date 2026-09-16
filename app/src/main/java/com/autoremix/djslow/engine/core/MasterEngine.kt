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
     * Mendukung inPlace mastering untuk mencegah OOM (OutOfMemoryError) pada lagu 3-5 menit.
     */
    suspend fun masterAudio(
        inputPcm: AudioPcmData,
        preset: MasteringPreset = MasteringPreset.DJ_SLOW,
        inPlace: Boolean = true,
        onProgress: ((Float, String) -> Unit)? = null
    ): Result<MasteredOutput> = withContext(Dispatchers.Default) {
        try {
            if (inputPcm.samples.isEmpty() || inputPcm.isSilent()) {
                return@withContext Result.failure(IllegalArgumentException("Audio input mastering kosong atau hening."))
            }

            // Monitor memori runtime sebelum alokasi besar
            val runtime = Runtime.getRuntime()
            val availableMemoryMb = (runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())) / (1024 * 1024)
            if (availableMemoryMb < 32) {
                System.gc() // Bersihkan objek tak terpakai sebelum pemrosesan DSP
            }

            onProgress?.invoke(0.15f, "1. Tonal EQ Balance (${preset.label})...")
            onProgress?.invoke(0.35f, "2. Glue Compressor & Tape Saturation...")
            onProgress?.invoke(0.55f, "3. Stereo Mono-Sub (<120Hz) & Width...")
            onProgress?.invoke(0.75f, "4. True Peak Lookahead Brickwall Limiter (-0.5 dBFS)...")

            // Panggil AutoMasteringEngine DSP nyata dengan inPlace processing
            val result = AutoMasteringEngine.master(inputPcm, preset, inPlace = inPlace)
            if (result.isFailure) {
                return@withContext Result.failure(result.exceptionOrNull()!!)
            }

            val masterResult = result.getOrThrow()
            onProgress?.invoke(0.90f, "5. Mengukur Loudness (${masterResult.report.formattedLufs}) & True Peak (${masterResult.report.formattedTruePeak})...")

            val output = MasteredOutput(
                pcmData = masterResult.masteredPcm,
                loudnessReport = masterResult.report,
                preset = preset
            )

            onProgress?.invoke(1.0f, "Mastering selesai: True Peak ${output.loudnessReport.formattedTruePeak}, Loudness ${output.loudnessReport.formattedLufs}")
            Result.success(output)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Mastering gagal: ${e.localizedMessage ?: e.message}"))
        }
    }
}
