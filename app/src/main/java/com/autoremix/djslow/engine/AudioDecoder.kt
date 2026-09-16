package com.autoremix.djslow.engine

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
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
            return Result.failure(IllegalStateException("File tidak tersedia."))
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
            return Result.failure(IllegalArgumentException("Format audio belum didukung."))
        }

        // 3. Uji keterbacaan berkas fisik dari storage
        val afd = try {
            contentResolver.openAssetFileDescriptor(uri, "r")
        } catch (e: FileNotFoundException) {
            return Result.failure(FileNotFoundException("File tidak tersedia."))
        } catch (e: SecurityException) {
            return Result.failure(SecurityException("File tidak tersedia."))
        } catch (e: Exception) {
            return Result.failure(IllegalStateException("File audio tidak dapat dibaca."))
        }

        if (afd == null) {
            return Result.failure(IllegalStateException("File tidak tersedia."))
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
            return Result.failure(IllegalStateException("File audio tidak dapat dibaca."))
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

        return Result.success(audioSource)
    }
}
