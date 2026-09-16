package com.autoremix.djslow.engine.core

import android.content.Context
import android.content.Intent
import com.autoremix.djslow.engine.export.AudioExportManager
import com.autoremix.djslow.engine.pcm.AudioPcmData
import com.autoremix.djslow.engine.structure.SongSectionType
import com.autoremix.djslow.engine.wav.WavRenderer
import com.autoremix.djslow.engine.wav.WavValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 10. OUTPUT ENGINE
 * Menangani seluruh output remix:
 * Preview 30 detik (Intro -> Build -> Pre-Drop -> Drop), Render WAV Master, Ekspor MP3,
 * MediaStore System Audio ("Music/Auto Remix/"), Berbagi (Share Intent), dan Simpan Proyek.
 *
 * Status Resmi Workflow:
 * IDLE, ANALYZING, PLANNING, GENERATING, MIXING, MASTERING, RENDERING, VALIDATING, READY, ERROR, CANCELLED.
 */
object OutputEngine {

    enum class OutputStatus(val label: String) {
        IDLE("Siap"),
        ANALYZING("1/6 Menganalisis Audio..."),
        PLANNING("2/6 Menyusun Rencana Remix Brain..."),
        GENERATING("3/6 Menyintesis Instrumen & Track..."),
        MIXING("4/6 Intelligent Multi-Bus Mixing..."),
        MASTERING("5/6 Auto Mastering & True Peak Protection..."),
        RENDERING("6/6 Merender Audio Nyata (WAV)..."),
        VALIDATING("Memvalidasi Integritas Audio..."),
        READY("Selesai & Siap Diputar"),
        ERROR("Gagal"),
        CANCELLED("Dibatalkan")
    }

    /**
     * Memotong dan mengambil cuplikan representatif 30 detik terbaik (fokus pada Build & Drop).
     */
    fun extract30SecondPreviewPcm(
        masterPcm: AudioPcmData,
        arrangement: ArrangementEngine.FullArrangement
    ): AudioPcmData {
        val sampleRate = masterPcm.sampleRate
        val channels = masterPcm.channels
        val previewDurationSamples = sampleRate * 30 // 30 Detik
        val totalFrames = masterPcm.totalFrames

        if (totalFrames <= previewDurationSamples) {
            return masterPcm // Jika lagu sudah kurang dari 30s, gunakan seluruhnya
        }

        // Cari seksi BUILD atau DROP untuk memulai cuplikan terbaik
        val dropSection = arrangement.sections.firstOrNull { it.sectionType == SongSectionType.DROP }
        val startFrame = if (dropSection != null) {
            // Mulai 8 detik sebelum drop jika mungkin (untuk build-up ke drop)
            val leadIn = sampleRate * 8
            maxOf(0L, dropSection.startSample - leadIn).toInt()
        } else {
            0
        }

        val endFrame = minOf(startFrame + previewDurationSamples, totalFrames)
        val actualFrames = endFrame - startFrame
        val outSamples = FloatArray(actualFrames * channels)

        System.arraycopy(masterPcm.samples, startFrame * channels, outSamples, 0, actualFrames * channels)

        // Terapkan fade-in 50ms & fade-out 250ms agar mulus
        val fadeInFrames = minOf(sampleRate / 20, actualFrames / 10)
        for (f in 0 until fadeInFrames) {
            val gain = f.toFloat() / fadeInFrames.toFloat()
            outSamples[f * channels] *= gain
            outSamples[f * channels + 1] *= gain
        }

        val fadeOutFrames = minOf(sampleRate / 4, actualFrames / 10)
        for (f in 0 until fadeOutFrames) {
            val gain = (fadeOutFrames - f).toFloat() / fadeOutFrames.toFloat()
            val idx = (actualFrames - 1 - f) * channels
            outSamples[idx] *= gain
            outSamples[idx + 1] *= gain
        }

        return AudioPcmData(outSamples, sampleRate, channels)
    }

    /**
     * Merender data PCM ke berkas master WAV lokal dan memvalidasi keabsahannya.
     */
    suspend fun renderWavFile(
        context: Context,
        pcm: AudioPcmData,
        targetFile: File,
        onProgress: ((Float, String) -> Unit)? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            onProgress?.invoke(0.20f, "Menulis header RIFF WAV Float32 ke 16-bit PCM...")
            val renderRes = WavRenderer.render(pcm, targetFile) { frac, desc ->
                onProgress?.invoke(0.20f + frac * 0.60f, desc)
            }

            if (renderRes.isFailure) {
                return@withContext Result.failure(renderRes.exceptionOrNull()!!)
            }

            onProgress?.invoke(0.85f, "Memvalidasi berkas audio WAV dengan WavValidator...")
            val validation = WavValidator.validate(targetFile)
            if (!validation.isValid) {
                return@withContext Result.failure(IllegalStateException("Validasi berkas WAV gagal: ${validation.errorMessage}"))
            }

            onProgress?.invoke(1.0f, "Berkas WAV master berhasil dirender (${targetFile.length() / 1024} KB)")
            Result.success(targetFile)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Gagal merender WAV: ${e.localizedMessage ?: e.message}"))
        }
    }

    /**
     * Ekspor WAV ke MediaStore "Music/Auto Remix/".
     */
    fun exportToMediaStore(context: Context, wavFile: File): Result<File> {
        return AudioExportManager.exportWavToMediaStore(context, wavFile)
    }

    /**
     * Ekspor berkas MP3 menggunakan MediaCodec lokal.
     */
    fun exportToMp3(context: Context, wavFile: File): Result<File> {
        return AudioExportManager.exportMp3(context, wavFile)
    }

    /**
     * Membuka Share Sheet Android untuk mengirim berkas audio nyata.
     */
    fun shareAudioFile(context: Context, audioFile: File): Result<Unit> {
        return AudioExportManager.shareAudioFile(context, audioFile)
    }

    /**
     * Membuat berkas target sementara di cache directory.
     */
    fun createTempWavFile(context: Context, prefix: String = "Master_Remix"): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return File(context.cacheDir, "${prefix}_${timeStamp}.wav")
    }
}
