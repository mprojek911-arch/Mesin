package com.autoremix.djslow.model

import android.net.Uri
import java.util.Locale

/**
 * Tipe lintasan audio yang didukung pada Fondasi Tahap 1.
 */
enum class TrackType(val label: String, val emoji: String) {
    VOCAL("Vokal", "🎤"),
    BEAT("Beat", "🥁")
}

/**
 * Status pemutaran audio nyata.
 */
enum class PlaybackStatus(val label: String) {
    IDLE("Siap"),
    LOADING("Memuat audio..."),
    PLAYING("Sedang memutar"),
    PAUSED("Jeda"),
    STOPPED("Berhenti"),
    ERROR("Terjadi kesalahan")
}

/**
 * Metadata dan informasi berkas audio nyata.
 */
data class AudioTrackInfo(
    val trackType: TrackType,
    val uri: Uri,
    val fileName: String,
    val fileSizeBytes: Long = 0L,
    val durationMs: Long = 0L,
    val mimeType: String? = null,
    val sampleRateHz: Int? = null,
    val bitrateBps: Int? = null,
    val channelCount: Int? = null,
    val isAnalyzed: Boolean = false,
    val analysisDetails: String? = null
) {
    val formattedDuration: String
        get() {
            if (durationMs <= 0) return "Durasi tidak diketahui"
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }

    val formattedFileSize: String
        get() {
            if (fileSizeBytes <= 0) return "Ukuran tidak diketahui"
            val kb = fileSizeBytes / 1024.0
            val mb = kb / 1024.0
            return if (mb >= 1.0) {
                String.format(Locale.getDefault(), "%.2f MB", mb)
            } else {
                String.format(Locale.getDefault(), "%.1f KB", kb)
            }
        }

    val formattedMimeType: String
        get() = mimeType ?: "Format tidak diketahui"
}

/**
 * Status dinamis playback per lintasan audio (Vokal / Beat).
 */
data class TrackPlaybackState(
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val volume: Float = 1.0f,
    val errorMessage: String? = null
) {
    val isPlaying: Boolean
        get() = status == PlaybackStatus.PLAYING

    val progressFraction: Float
        get() = if (durationMs > 0) {
            (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val formattedCurrentPosition: String
        get() {
            val totalSeconds = currentPositionMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }

    val formattedTotalDuration: String
        get() {
            if (durationMs <= 0) return "00:00"
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
        }
}
