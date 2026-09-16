package com.autoremix.djslow.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.autoremix.djslow.model.AudioTrackInfo
import com.autoremix.djslow.model.TrackType

/**
 * Validasi dan ekstraksi metadata berkas audio nyata via Storage Access Framework (SAF).
 */
object AudioValidator {

    private val SUPPORTED_EXTENSIONS = listOf(
        "mp3", "wav", "m4a", "aac", "flac", "ogg", "opus", "3gp", "amr"
    )

    /**
     * Memvalidasi URI berkas audio yang dipilih pengguna dan mengekstrak metadatanya.
     */
    fun validateAndExtract(
        context: Context,
        uri: Uri,
        trackType: TrackType
    ): Result<AudioTrackInfo> {
        val contentResolver = context.contentResolver

        // 1. Ekstraksi nama dan ukuran berkas dari OpenableColumns
        var displayName: String? = null
        var fileSize: Long = 0L

        try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        displayName = cursor.getString(nameIndex)
                    }
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                        fileSize = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            // Lanjutkan jika query gagal, gunakan path segmen terakhir sebagai fallback
        }

        if (displayName.isNullOrBlank()) {
            displayName = uri.lastPathSegment ?: "${trackType.label}_audio"
        }

        val resolvedName = displayName ?: "audio_${System.currentTimeMillis()}"

        // 2. Validasi ekstensi dan MIME type
        val contentMimeType = contentResolver.getType(uri)
        val fileExtension = resolvedName.substringAfterLast('.', "").lowercase()

        val isAudioMime = contentMimeType?.startsWith("audio/") == true ||
                contentMimeType == "application/ogg" ||
                contentMimeType == "application/x-ogg"

        val hasAudioExtension = SUPPORTED_EXTENSIONS.contains(fileExtension)

        if (!isAudioMime && !hasAudioExtension && contentMimeType != null && !contentMimeType.contains("octet-stream")) {
            return Result.failure(
                IllegalArgumentException(
                    "Format berkas '$resolvedName' tidak didukung. Harap pilih berkas audio (MP3, WAV, M4A, AAC, FLAC, OGG)."
                )
            )
        }

        // 3. Validasi keterbacaan berkas nyata dari ContentResolver
        try {
            contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                if (fileSize <= 0L && afd.length > 0L) {
                    fileSize = afd.length
                }
            } ?: return Result.failure(
                IllegalStateException("Tidak dapat membuka berkas '$resolvedName' dari penyimpanan.")
            )
        } catch (e: SecurityException) {
            return Result.failure(
                SecurityException("Izin akses penyimpanan ke berkas '$resolvedName' ditolak atau kedaluwarsa.")
            )
        } catch (e: Exception) {
            return Result.failure(
                IllegalStateException("Gagal membaca berkas '$resolvedName': ${e.localizedMessage ?: "Berkas rusak"}")
            )
        }

        if (fileSize <= 0L) {
            // Coba buka inputStream untuk memastikan tidak benar-benar kosong
            try {
                contentResolver.openInputStream(uri)?.use { stream ->
                    val available = stream.available()
                    if (available > 0) {
                        fileSize = available.toLong()
                    }
                }
            } catch (_: Exception) { }
        }

        // 4. Ekstraksi metadata riil via MediaMetadataRetriever
        var durationMs = 0L
        var resolvedMime: String? = contentMimeType
        var sampleRate: Int? = null
        var bitrate: Int? = null
        var channelCount: Int? = null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            if (!durationStr.isNullOrBlank()) {
                durationMs = durationStr.toLongOrNull() ?: 0L
            }

            val extractedMime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            if (!extractedMime.isNullOrBlank()) {
                resolvedMime = extractedMime
            }

            val bitrateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            if (!bitrateStr.isNullOrBlank()) {
                bitrate = bitrateStr.toIntOrNull()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val sampleRateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                if (!sampleRateStr.isNullOrBlank()) {
                    sampleRate = sampleRateStr.toIntOrNull()
                }
            }

            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            // Jika ada info HAS_AUDIO dan nilainya bukan "yes" (pada video tanpa audio)
            if (hasAudio != null && hasAudio != "yes") {
                return Result.failure(
                    IllegalArgumentException("Berkas '$resolvedName' tidak memiliki trek audio yang valid.")
                )
            }
        } catch (e: Exception) {
            // Jika MediaMetadataRetriever gagal membaca berkas, berkas kemungkinan bukan audio valid
            return Result.failure(
                IllegalArgumentException(
                    "Berkas '$resolvedName' tidak dapat didekode sebagai audio. Pastikan berkas tidak rusak."
                )
            )
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) { }
        }

        val trackInfo = AudioTrackInfo(
            trackType = trackType,
            uri = uri,
            fileName = resolvedName,
            fileSizeBytes = fileSize,
            durationMs = durationMs,
            mimeType = resolvedMime ?: "audio/*",
            sampleRateHz = sampleRate,
            bitrateBps = bitrate,
            channelCount = channelCount,
            isAnalyzed = false,
            analysisDetails = null
        )

        return Result.success(trackInfo)
    }

    /**
     * Menjalankan analisis teknis dasar nyata pada berkas audio (tanpa memalsukan BPM atau Key).
     */
    fun performBasicAnalysis(context: Context, trackInfo: AudioTrackInfo): AudioTrackInfo {
        val sb = StringBuilder()
        sb.append("Analisis Teknis Berkas:\n")
        sb.append("• Nama: ${trackInfo.fileName}\n")
        sb.append("• Format / MIME: ${trackInfo.formattedMimeType}\n")
        sb.append("• Durasi: ${trackInfo.formattedDuration} (${trackInfo.durationMs} ms)\n")
        sb.append("• Ukuran: ${trackInfo.formattedFileSize}\n")

        if (trackInfo.sampleRateHz != null) {
            sb.append("• Sample Rate: ${trackInfo.sampleRateHz} Hz\n")
        }
        if (trackInfo.bitrateBps != null) {
            val kbps = trackInfo.bitrateBps / 1000
            sb.append("• Bitrate: $kbps kbps\n")
        }
        sb.append("• Validasi Audio: Lolos (Trek siap diputar)\n")
        sb.append("• BPM: Belum dianalisis\n")
        sb.append("• Key: Belum dianalisis")

        return trackInfo.copy(
            isAnalyzed = true,
            analysisDetails = sb.toString()
        )
    }
}
