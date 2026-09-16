package com.autoremix.djslow.engine

import android.net.Uri

/**
 * Sumber audio nyata dari Storage Access Framework (SAF) dan ContentResolver.
 */
data class AudioSource(
    val uri: Uri,
    val fileName: String,
    val fileSizeBytes: Long = 0L,
    val mimeType: String? = null,
    val durationMs: Long = 0L,
    val sampleRateHz: Int? = null,
    val channelCount: Int? = null,
    val formatExtension: String = ""
) {
    val formattedDuration: String
        get() {
            if (durationMs <= 0) return "Durasi: Tidak diketahui"
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }

    val formattedChannels: String
        get() = when (channelCount) {
            1 -> "Mono (1 ch)"
            2 -> "Stereo (2 ch)"
            null -> "Tidak diketahui"
            else -> "$channelCount ch"
        }

    val formattedSampleRate: String
        get() = if (sampleRateHz != null && sampleRateHz > 0) {
            "$sampleRateHz Hz"
        } else {
            "Tidak diketahui"
        }

    val formattedFormat: String
        get() {
            val ext = formatExtension.uppercase()
            return if (ext.isNotBlank()) ext else (mimeType ?: "Audio")
        }

    val formattedFileSize: String
        get() {
            if (fileSizeBytes <= 0L) return "Tidak diketahui"
            val kb = fileSizeBytes / 1024.0
            if (kb < 1024.0) {
                return String.format(java.util.Locale.US, "%.1f KB", kb)
            }
            val mb = kb / 1024.0
            return String.format(java.util.Locale.US, "%.1f MB", mb)
        }
}
