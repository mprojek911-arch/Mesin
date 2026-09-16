package com.autoremix.djslow.engine

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.autoremix.djslow.logchat.AudioDiagnosticInfo
import com.autoremix.djslow.logchat.LogChatManager
import com.autoremix.djslow.logchat.LogModule
import com.autoremix.djslow.logchat.PipelineStage
import com.autoremix.djslow.logchat.StepStatus
import java.io.FileNotFoundException

/**
 * Audio Decoder yang membaca dan mendekode metadata audio nyata via ContentResolver.
 */
object AudioDecoder {

    private val SUPPORTED_EXTENSIONS = setOf("mp3", "wav", "m4a", "aac", "ogg", "flac")

    /**
     * Membaca dan memvalidasi file audio dari ContentResolver + URI SAF.
     */
    fun decode(context: Context, uri: Uri): Result<AudioSource> {
        val contentResolver = context.contentResolver
        LogChatManager.info(LogModule.AUDIO, "AUDIO_DECODE_START: Memulai pembacaan URI audio...", stage = "DECODE")
        LogChatManager.updatePipeline(PipelineStage.INPUT, StepStatus.SUCCESS, "URI SAF Diterima")

        // 1. Ekstraksi nama file & ukuran
        var fileName: String? = null
        var fileSize: Long = 0L

        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        fileName = cursor.getString(nameIdx)
                    }
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) {
                        fileSize = cursor.getLong(sizeIdx)
                    }
                }
            }
        } catch (e: SecurityException) {
            val ex = IllegalStateException("File tidak tersedia (SecurityException)")
            LogChatManager.error(LogModule.AUDIO, "AUDIO_DECODE_FAILED: SecurityException akses file", ex, stage = "DECODE")
            LogChatManager.updatePipeline(PipelineStage.DECODE, StepStatus.FAILED, "SecurityException")
            return Result.failure(ex)
        } catch (e: Exception) {
            // Abaikan query cursor jika provider khusus
        }

        val resolvedName = fileName ?: uri.lastPathSegment ?: "audio_file"
        val extension = resolvedName.substringAfterLast('.', "").lowercase()
        val mimeType = contentResolver.getType(uri)

        // 2. Validasi format file yang didukung
        val isMimeAudio = mimeType?.startsWith("audio/") == true ||
                mimeType == "application/ogg" ||
                mimeType == "application/x-ogg"
        val isExtSupported = SUPPORTED_EXTENSIONS.contains(extension)

        if (!isExtSupported && !isMimeAudio && mimeType != null && !mimeType.contains("octet-stream")) {
            val ex = IllegalArgumentException("Format audio belum didukung: $extension (MIME: $mimeType)")
            LogChatManager.error(
                module = LogModule.AUDIO,
                message = "AUDIO_DECODE_FAILED: Gagal membaca file audio.",
                throwable = ex,
                detail = "Unsupported audio format: .$extension. Format yang didukung: MP3, WAV, M4A, AAC, OGG, FLAC.",
                stage = "DECODE",
                file = resolvedName
            )
            LogChatManager.updatePipeline(PipelineStage.DECODE, StepStatus.FAILED, "Unsupported audio format")
            return Result.failure(ex)
        }

        // 3. Uji keterbacaan berkas fisik dari storage
        val afd = try {
            contentResolver.openAssetFileDescriptor(uri, "r")
        } catch (e: FileNotFoundException) {
            val ex = FileNotFoundException("File tidak ditemukan: $resolvedName")
            LogChatManager.error(LogModule.AUDIO, "AUDIO_DECODE_FAILED: File tidak ditemukan", ex, stage = "DECODE", file = resolvedName)
            LogChatManager.updatePipeline(PipelineStage.DECODE, StepStatus.FAILED, "File not found")
            return Result.failure(ex)
        } catch (e: SecurityException) {
            val ex = SecurityException("Izin akses file ditolak: $resolvedName")
            LogChatManager.error(LogModule.AUDIO, "AUDIO_DECODE_FAILED: Izin akses file ditolak", ex, stage = "DECODE", file = resolvedName)
            LogChatManager.updatePipeline(PipelineStage.DECODE, StepStatus.FAILED, "Permission denied")
            return Result.failure(ex)
        } catch (e: Exception) {
            val ex = IllegalStateException("File audio tidak dapat dibaca: ${e.message}")
            LogChatManager.error(LogModule.AUDIO, "AUDIO_DECODE_FAILED: File audio tidak dapat dibaca", ex, stage = "DECODE", file = resolvedName)
            LogChatManager.updatePipeline(PipelineStage.DECODE, StepStatus.FAILED, e.message ?: "Read error")
            return Result.failure(ex)
        }

        if (afd == null) {
            val ex = IllegalStateException("AssetFileDescriptor null untuk: $resolvedName")
            LogChatManager.error(LogModule.AUDIO, "AUDIO_DECODE_FAILED: File descriptor tidak tersedia", ex, stage = "DECODE", file = resolvedName)
            LogChatManager.updatePipeline(PipelineStage.DECODE, StepStatus.FAILED, "Null file descriptor")
            return Result.failure(ex)
        }

        if (fileSize <= 0L && afd.length > 0L) {
            fileSize = afd.length
        }

        var durationMs = 0L
        var sampleRate: Int? = null
        var channelCount: Int? = null

        // 4. Ekstraksi durasi dan format via MediaMetadataRetriever
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (!durationStr.isNullOrBlank()) {
                durationMs = durationStr.toLongOrNull() ?: 0L
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val sampleRateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                if (!sampleRateStr.isNullOrBlank()) {
                    sampleRate = sampleRateStr.toIntOrNull()
                }
            }
        } catch (e: Exception) {
            try { afd.close() } catch (_: Exception) {}
            val ex = IllegalStateException("Gagal mengekstrak metadata audio: ${e.message}")
            LogChatManager.error(LogModule.AUDIO, "AUDIO_DECODE_FAILED: Metadata ekstraksi gagal", ex, stage = "DECODE", file = resolvedName)
            LogChatManager.updatePipeline(PipelineStage.DECODE, StepStatus.FAILED, "Metadata extraction failed")
            return Result.failure(ex)
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }

        // 5. Ekstraksi Sample Rate & Channel Count via MediaExtractor
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            val numTracks = extractor.trackCount
            for (i in 0 until numTracks) {
                val format = extractor.getTrackFormat(i)
                val trackMime = format.getString(MediaFormat.KEY_MIME)
                if (trackMime?.startsWith("audio/") == true) {
                    if (sampleRate == null && format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }
                    if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                    if (durationMs <= 0L && format.containsKey(MediaFormat.KEY_DURATION)) {
                        durationMs = format.getLong(MediaFormat.KEY_DURATION) / 1000L
                    }
                    break
                }
            }
        } catch (e: Exception) {
            // MediaExtractor optional fallback, data dari retriever sudah memadai
        } finally {
            try { extractor.release() } catch (_: Exception) {}
            try { afd.close() } catch (_: Exception) {}
        }

        val audioSource = AudioSource(
            uri = uri,
            fileName = resolvedName,
            fileSizeBytes = fileSize,
            mimeType = mimeType,
            durationMs = durationMs,
            sampleRateHz = sampleRate,
            channelCount = channelCount,
            formatExtension = extension
        )

        LogChatManager.info(
            module = LogModule.AUDIO,
            message = "AUDIO_DECODE_SUCCESS: File audio berhasil didekode.",
            detail = "Nama: $resolvedName | Format: $extension | Durasi: ${durationMs / 1000}s | Sample Rate: ${sampleRate ?: 44100}Hz | Channels: ${channelCount ?: 2}",
            stage = "DECODE",
            file = resolvedName
        )
        LogChatManager.updatePipeline(PipelineStage.DECODE, StepStatus.SUCCESS, "$resolvedName (${durationMs / 1000}s)")
        LogChatManager.updateAudioDiagnostic(
            AudioDiagnosticInfo(
                fileName = resolvedName,
                format = extension,
                durationMs = durationMs,
                sampleRate = sampleRate ?: 44100,
                channels = channelCount ?: 2,
                fileSizeBytes = fileSize,
                lastSuccessfulStep = "Decode"
            )
        )

        return Result.success(audioSource)
    }
}
