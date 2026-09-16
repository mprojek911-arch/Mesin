package com.autoremix.djslow.engine.preview

import com.autoremix.djslow.engine.structure.SongSection
import kotlin.math.pow

/**
 * Pengontrol Mode Preview A/B Studio & Section Preview (Tahap 6 Final):
 * Menyediakan perbandingan langsung antara sumber suara:
 * 1. [ORIGINAL]   : Trek mentah vokal & beat
 * 2. [REMIX]      : Hasil multi-bus mix (sebelum mastering / pre-master)
 * 3. [MASTERED]   : Hasil akhir mastering (Glue Comp, EQ, Saturation, True Peak Limiter)
 *
 * Mendukung Loudness Matching: Normalisasi volume persepsi antar mode A/B agar pendengar
 * dapat membandingkan kualitas dinamika dan warna nada secara obyektif tanpa bias kenyaringan.
 */
object AbPreviewController {

    enum class AbMode(val label: String, val description: String) {
        ORIGINAL("ORIGINAL", "Trek mentah vokal & beat asli"),
        REMIX("REMIX (PRE-MASTER)", "Hasil mixing multi-bus instrumen"),
        MASTERED("MASTERED (FINAL)", "Hasil master studio ITU-R BS.1770-4 & Limiter")
    }

    /**
     * Menghitung faktor volume untuk Loudness Matching.
     * Jika masteredLufs = -10.5 dan remixLufs = -14.0, selisih = 3.5 dB.
     * Untuk menyamakan level, master diturunkan sebesar 3.5 dB (faktor ~0.67x).
     */
    fun calculateLoudnessGain(
        mode: AbMode,
        isMatchingEnabled: Boolean,
        masteredLufs: Float,
        unmasteredLufs: Float
    ): Float {
        if (!isMatchingEnabled) return 1.0f

        val deltaDb = masteredLufs - unmasteredLufs
        if (deltaDb <= 0.1f) return 1.0f

        return when (mode) {
            AbMode.ORIGINAL -> 1.0f
            AbMode.REMIX -> 1.0f
            AbMode.MASTERED -> {
                // Turunkan gain master agar setara dengan loudness pre-master
                val attenuationFactor = 10.0.pow((-deltaDb / 20.0)).toFloat()
                attenuationFactor.coerceIn(0.2f, 1.0f)
            }
        }
    }

    /**
     * Menghitung rentang waktu sampel untuk Section Preview (15-30 detik).
     */
    fun calculateSectionTimeRange(
        section: SongSection?,
        totalDurationMs: Long,
        previewDurationSeconds: Int = 15
    ): Pair<Long, Long> {
        val previewMs = (previewDurationSeconds * 1000L).coerceIn(5000L, 30000L)

        if (section == null) {
            // Default ke bagian tengah / Drop
            val startMs = (totalDurationMs * 0.35f).toLong().coerceIn(0L, maxOf(0L, totalDurationMs - previewMs))
            val endMs = minOf(totalDurationMs, startMs + previewMs)
            return Pair(startMs, endMs)
        }

        val startMs = section.startTimeMs.coerceIn(0L, totalDurationMs)
        val endMs = minOf(totalDurationMs, startMs + previewMs)
        return Pair(startMs, endMs)
    }
}
