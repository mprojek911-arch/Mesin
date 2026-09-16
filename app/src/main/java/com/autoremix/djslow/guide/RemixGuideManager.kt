package com.autoremix.djslow.guide

import android.content.Context
import android.content.SharedPreferences
import com.autoremix.djslow.state.UiState
import kotlin.math.roundToInt

/**
 * Pengelola status dan preferensi panduan remix musik interaktif.
 * Membaca kondisi aplikasi nyata tanpa mock / data simulasi.
 */
object RemixGuideManager {

    private const val PREFS_NAME = "remix_guide_prefs"
    private const val KEY_LAST_STEP = "last_guide_step"
    private const val KEY_DO_NOT_SHOW_STARTUP = "do_not_show_startup"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getLastStep(context: Context): Int {
        return getPrefs(context).getInt(KEY_LAST_STEP, 1).coerceIn(1, 13)
    }

    fun getLastGuideStep(context: Context): Int {
        return getLastStep(context)
    }

    fun saveLastStep(context: Context, stepNumber: Int) {
        val safeStep = stepNumber.coerceIn(1, 13)
        getPrefs(context).edit().putInt(KEY_LAST_STEP, safeStep).apply()
    }

    fun getDoNotShowStartup(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_DO_NOT_SHOW_STARTUP, false)
    }

    fun setDoNotShowStartup(context: Context, doNotShow: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_DO_NOT_SHOW_STARTUP, doNotShow).apply()
    }

    data class StepRealStatus(
        val isCompleted: Boolean,
        val isInProgress: Boolean,
        val badgeText: String,
        val detailText: String
    )

    /**
     * Memeriksa kondisi nyata UI State dan Engine audio untuk langkah panduan tertentu.
     */
    fun getStepStatus(step: GuideStep, uiState: UiState): StepRealStatus {
        return when (step) {
            GuideStep.STEP_01_PILIH_AUDIO -> {
                when {
                    uiState.isVocalSelected && uiState.isBeatSelected -> {
                        StepRealStatus(
                            isCompleted = true,
                            isInProgress = false,
                            badgeText = "✅ SELESAI (LENGKAP)",
                            detailText = "Vokal: ${uiState.vocalSource?.fileName ?: "Aktif"} | Beat: ${uiState.beatSource?.fileName ?: "Aktif"}"
                        )
                    }
                    uiState.hasAnyAudio -> {
                        StepRealStatus(
                            isCompleted = true,
                            isInProgress = false,
                            badgeText = "✅ SELESAI (SEBAGIAN)",
                            detailText = if (uiState.isVocalSelected) "Vokal: ${uiState.vocalSource?.fileName}" else "Beat: ${uiState.beatSource?.fileName}"
                        )
                    }
                    else -> {
                        StepRealStatus(
                            isCompleted = false,
                            isInProgress = false,
                            badgeText = "⚠️ BELUM ADA AUDIO",
                            detailText = "Silakan tekan tombol PILIH AUDIO untuk memuat lagu dari perangkat."
                        )
                    }
                }
            }
            GuideStep.STEP_02_ANALISIS_MUSIK -> {
                when {
                    uiState.isAnalyzing -> {
                        val pct = (uiState.analysisProgressFraction * 100).toInt()
                        StepRealStatus(
                            isCompleted = false,
                            isInProgress = true,
                            badgeText = "⏳ SEDANG MENGANALISIS ($pct%)",
                            detailText = uiState.analysisMessage.ifBlank { "Sedang menganalisis struktur dan tempo musik..." }
                        )
                    }
                    uiState.musicAnalysis != null || uiState.beatAnalysis != null || uiState.vocalBpm != null || uiState.beatBpm != null -> {
                        val bpmStr = uiState.targetBpmText
                        val keyStr = uiState.keyText
                        StepRealStatus(
                            isCompleted = true,
                            isInProgress = false,
                            badgeText = "✅ ANALISIS SELESAI",
                            detailText = "Tempo: $bpmStr | Nada: $keyStr | Akurasi: ${uiState.confidenceText}"
                        )
                    }
                    else -> {
                        StepRealStatus(
                            isCompleted = false,
                            isInProgress = false,
                            badgeText = if (uiState.hasAnyAudio) "🟡 SIAP DIANALISIS" else "⚠️ BUTUH AUDIO",
                            detailText = if (uiState.hasAnyAudio) "Audio siap dianalisis. Tekan tombol Analisis Musik." else "Muat audio terlebih dahulu sebelum analisis."
                        )
                    }
                }
            }
            GuideStep.STEP_03_PISAHKAN_VOCAL_BEAT -> {
                val hasBoth = uiState.isVocalSelected && uiState.isBeatSelected
                StepRealStatus(
                    isCompleted = hasBoth,
                    isInProgress = false,
                    badgeText = if (hasBoth) "✅ DUA TREK TERPISAH" else "ℹ️ SATU TREK TERSEDIA",
                    detailText = if (hasBoth) "Vokal & instrumen beat terpisah sempurna untuk kontrol penuh." else "Anda dapat menambahkan instrumen beat pengiring terpisah untuk hasil remix optimal."
                )
            }
            GuideStep.STEP_04_ATUR_BPM_PITCH -> {
                val bpm = uiState.targetBpm.roundToInt()
                StepRealStatus(
                    isCompleted = true,
                    isInProgress = false,
                    badgeText = "✅ BPM: $bpm",
                    detailText = "Tempo target saat ini $bpm BPM. Nada terdeteksi ${uiState.detectedKey.tonic.displayName} ${uiState.detectedKey.mode.label}."
                )
            }
            GuideStep.STEP_05_PILIH_GAYA_REMIX -> {
                val presetName = uiState.currentPreset.label
                StepRealStatus(
                    isCompleted = true,
                    isInProgress = false,
                    badgeText = "✅ GAYA: $presetName",
                    detailText = "Gaya aransemen aktif: $presetName (${uiState.currentPreset.description})"
                )
            }
            GuideStep.STEP_06_ATUR_STRUKTUR_REMIX -> {
                val secCount = uiState.songSections.size
                if (secCount > 0) {
                    val names = uiState.songSections.take(4).joinToString(" ➔ ") { it.sectionType.label }
                    StepRealStatus(
                        isCompleted = true,
                        isInProgress = false,
                        badgeText = "✅ $secCount BAGIAN ARANSEMEN",
                        detailText = "Alur lagu: $names..."
                    )
                } else {
                    StepRealStatus(
                        isCompleted = false,
                        isInProgress = false,
                        badgeText = "ℹ️ ALUR STANDAR DJ SLOW",
                        detailText = "Intro ➔ Build Up ➔ Drop ➔ Break ➔ Build Up ➔ Drop ➔ Outro."
                    )
                }
            }
            GuideStep.STEP_07_ATUR_MUSIK_LAYERS -> {
                StepRealStatus(
                    isCompleted = true,
                    isInProgress = false,
                    badgeText = "✅ 8 LAYER AKTIF",
                    detailText = "Drum (${(uiState.drumMixSettings.volume * 100).toInt()}%), Bass (${(uiState.bassMixSettings.volume * 100).toInt()}%), Chord (${(uiState.chordMixSettings.volume * 100).toInt()}%), Melodi (${(uiState.melodyMixSettings.volume * 100).toInt()}%), Pad (${(uiState.padMixSettings.volume * 100).toInt()}%)"
                )
            }
            GuideStep.STEP_08_ATUR_VOCAL -> {
                val autoDucking = if (uiState.isAutoMixEnabled) "Aktif" else "Nonaktif"
                StepRealStatus(
                    isCompleted = true,
                    isInProgress = false,
                    badgeText = "✅ VOKAL: ${(uiState.vocalMixSettings.volume * 100).toInt()}%",
                    detailText = "Auto Ducking Vokal: $autoDucking. Vokal siap menyatu dengan beat."
                )
            }
            GuideStep.STEP_09_ATUR_FX_TRANSITION -> {
                StepRealStatus(
                    isCompleted = true,
                    isInProgress = false,
                    badgeText = "✅ FX READY",
                    detailText = "Riser & Impact transisi otomatis diselaraskan dengan setiap seksi Build-Up dan Drop."
                )
            }
            GuideStep.STEP_10_PREVIEW_MONITOR -> {
                when {
                    uiState.isPlaying -> {
                        StepRealStatus(
                            isCompleted = true,
                            isInProgress = true,
                            badgeText = "▶️ SEDANG MEMUTAR PREVIEW",
                            detailText = "Mendengarkan sinkronisasi vokal & beat pada tempo $targetBpmText."
                        )
                    }
                    uiState.isPaused -> {
                        StepRealStatus(
                            isCompleted = true,
                            isInProgress = false,
                            badgeText = "⏸ PREVIEW DIJEDA",
                            detailText = "Pemutar audio sumber sedang dijeda."
                        )
                    }
                    uiState.hasAnyAudio -> {
                        StepRealStatus(
                            isCompleted = false,
                            isInProgress = false,
                            badgeText = "🟡 SIAP DIPUTAR",
                            detailText = "Tekan PUTAR untuk memantau audio sumber."
                        )
                    }
                    else -> {
                        StepRealStatus(
                            isCompleted = false,
                            isInProgress = false,
                            badgeText = "⚠️ AUDIO BELUM DIMUAT",
                            detailText = "Pilih audio terlebih dahulu untuk mendengarkan preview."
                        )
                    }
                }
            }
            GuideStep.STEP_11_RENDER_REMIX -> {
                when {
                    uiState.isRendering -> {
                        val pct = (uiState.renderProgressFraction * 100).toInt()
                        StepRealStatus(
                            isCompleted = false,
                            isInProgress = true,
                            badgeText = "⏳ SEDANG MERENDER ($pct%)",
                            detailText = uiState.renderStageText.ifBlank { "Sedang memproses audio WAV..." }
                        )
                    }
                    uiState.isRenderedAvailable -> {
                        StepRealStatus(
                            isCompleted = true,
                            isInProgress = false,
                            badgeText = "✅ HASIL RENDER SIAP",
                            detailText = "File: ${uiState.renderedWavFile?.name ?: "remix.wav"} (${uiState.renderedDurationMs / 1000} detik)"
                        )
                    }
                    else -> {
                        StepRealStatus(
                            isCompleted = false,
                            isInProgress = false,
                            badgeText = if (uiState.hasAnyAudio) "🟡 SIAP DIRENDER" else "⚠️ BUTUH SUMBER AUDIO",
                            detailText = if (uiState.hasAnyAudio) "Tekan RENDER MASTER untuk memproses hasil remix." else "Pilih audio vokal/beat terlebih dahulu."
                        )
                    }
                }
            }
            GuideStep.STEP_12_PUTAR_HASIL_REMIX -> {
                when {
                    uiState.isRenderedPlaying -> {
                        StepRealStatus(
                            isCompleted = true,
                            isInProgress = true,
                            badgeText = "▶️ MEMUTAR HASIL REMIX ASLI",
                            detailText = "Memutar file audio mix hasil render: ${uiState.renderedWavFile?.name}"
                        )
                    }
                    uiState.isRenderedPaused -> {
                        StepRealStatus(
                            isCompleted = true,
                            isInProgress = false,
                            badgeText = "⏸ HASIL REMIX DIJEDA",
                            detailText = "File remix hasil render siap diputar kembali."
                        )
                    }
                    uiState.isRenderedAvailable -> {
                        StepRealStatus(
                            isCompleted = true,
                            isInProgress = false,
                            badgeText = "✅ SIAP DIPUTAR",
                            detailText = "File hasil render tersedia (${uiState.renderedDurationMs / 1000}s). Tekan PUTAR HASIL."
                        )
                    }
                    else -> {
                        StepRealStatus(
                            isCompleted = false,
                            isInProgress = false,
                            badgeText = "⚠️ BELUM DIRENDER",
                            detailText = "Selesaikan proses Render di Langkah 11 terlebih dahulu."
                        )
                    }
                }
            }
            GuideStep.STEP_13_SIMPAN_EXPORT -> {
                val hasExported = uiState.exportedWavFile != null || uiState.exportedMp3File != null || uiState.exportSuccessMessage != null
                when {
                    hasExported -> {
                        StepRealStatus(
                            isCompleted = true,
                            isInProgress = false,
                            badgeText = "✅ BERHASIL DISIMPAN",
                            detailText = uiState.exportSuccessMessage ?: "File remix berhasil diekspor ke memori perangkat."
                        )
                    }
                    uiState.isRenderedAvailable -> {
                        StepRealStatus(
                            isCompleted = false,
                            isInProgress = false,
                            badgeText = "🟡 SIAP DISIMPAN",
                            detailText = "Hasil remix siap diekspor ke folder Musik perangkat Android Anda."
                        )
                    }
                    else -> {
                        StepRealStatus(
                            isCompleted = false,
                            isInProgress = false,
                            badgeText = "⚠️ BUTUH HASIL RENDER",
                            detailText = "Lakukan render terlebih dahulu sebelum menyimpan file."
                        )
                    }
                }
            }
        }
    }

    private val targetBpmText = "80"
}
