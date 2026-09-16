package com.autoremix.djslow.engine.arrangement

import com.autoremix.djslow.engine.structure.SongSectionType

/**
 * Konfigurasi layer aktif untuk suatu seksi lagu.
 */
data class SectionLayerConfig(
    val isKickActive: Boolean,
    val isSnareClapActive: Boolean,
    val isHatsActive: Boolean,
    val isBassActive: Boolean,
    val isChordActive: Boolean,
    val isMelodyLeadActive: Boolean,
    val isPadActive: Boolean,
    val isVocalActive: Boolean,
    val isFxRiserActive: Boolean,
    val isFxCrashActive: Boolean,
    val description: String
)

/**
 * Auto Layer Engine:
 * Menentukan konfigurasi instrumen aktif per seksi lagu secara cerdas.
 */
object AutoLayerEngine {

    fun getLayerConfigForSection(sectionType: SongSectionType): SectionLayerConfig {
        return when (sectionType) {
            SongSectionType.INTRO -> SectionLayerConfig(
                isKickActive = false,
                isSnareClapActive = false,
                isHatsActive = false,
                isBassActive = false,
                isChordActive = true,
                isMelodyLeadActive = false,
                isPadActive = true,
                isVocalActive = true,
                isFxRiserActive = false,
                isFxCrashActive = false,
                description = "Atmosfir tenang: Pad + Akor Lembut + Vokal"
            )

            SongSectionType.BUILD_UP, SongSectionType.BUILD_UP_2, SongSectionType.FINAL_BUILD -> SectionLayerConfig(
                isKickActive = false,
                isSnareClapActive = true,
                isHatsActive = true,
                isBassActive = true, // Filtered bass
                isChordActive = true,
                isMelodyLeadActive = true,
                isPadActive = true,
                isVocalActive = true,
                isFxRiserActive = true,
                isFxCrashActive = false,
                description = "Pembangunan tensi: Snare Roll + Riser + Melodi Naik"
            )

            SongSectionType.GROOVE, SongSectionType.VERSE -> SectionLayerConfig(
                isKickActive = true,
                isSnareClapActive = true,
                isHatsActive = true,
                isBassActive = true,
                isChordActive = true,
                isMelodyLeadActive = false,
                isPadActive = true,
                isVocalActive = true,
                isFxRiserActive = false,
                isFxCrashActive = true,
                description = "Irama santai stabil: Kick + Bass + Vokal Utama"
            )

            SongSectionType.PRE_DROP -> SectionLayerConfig(
                isKickActive = false,
                isSnareClapActive = true,
                isHatsActive = false,
                isBassActive = false,
                isChordActive = true,
                isMelodyLeadActive = true,
                isPadActive = false,
                isVocalActive = true,
                isFxRiserActive = true,
                isFxCrashActive = false,
                description = "Pre-Drop: Jeda nafas sebelum ledakan"
            )

            SongSectionType.DROP -> SectionLayerConfig(
                isKickActive = true,
                isSnareClapActive = true,
                isHatsActive = true,
                isBassActive = true,
                isChordActive = true,
                isMelodyLeadActive = true,
                isPadActive = true,
                isVocalActive = true,
                isFxRiserActive = false,
                isFxCrashActive = true,
                description = "Drop 1: Dentuman Kick, Bass Jedag-Jedug & Lead"
            )

            SongSectionType.BREAK, SongSectionType.BREAKDOWN -> SectionLayerConfig(
                isKickActive = false,
                isSnareClapActive = false,
                isHatsActive = false,
                isBassActive = false,
                isChordActive = true,
                isMelodyLeadActive = true,
                isPadActive = true,
                isVocalActive = true,
                isFxRiserActive = false,
                isFxCrashActive = false,
                description = "Breakdown: Drum lepas, fokus pada Pad & Vokal emosional"
            )

            SongSectionType.MAIN_DROP, SongSectionType.PEAK -> SectionLayerConfig(
                isKickActive = true,
                isSnareClapActive = true,
                isHatsActive = true,
                isBassActive = true,
                isChordActive = true,
                isMelodyLeadActive = true,
                isPadActive = true,
                isVocalActive = true,
                isFxRiserActive = false,
                isFxCrashActive = true,
                description = "Puncak energi: Seluruh instrumen maksimal & bertenaga"
            )

            SongSectionType.FINAL_DROP -> SectionLayerConfig(
                isKickActive = true,
                isSnareClapActive = true,
                isHatsActive = true,
                isBassActive = true,
                isChordActive = true,
                isMelodyLeadActive = true,
                isPadActive = true,
                isVocalActive = true,
                isFxRiserActive = false,
                isFxCrashActive = true,
                description = "Klimaks penutup: Full power arrangement"
            )

            SongSectionType.OUTRO -> SectionLayerConfig(
                isKickActive = true,
                isSnareClapActive = false,
                isHatsActive = false,
                isBassActive = true,
                isChordActive = true,
                isMelodyLeadActive = true,
                isPadActive = true,
                isVocalActive = true,
                isFxRiserActive = false,
                isFxCrashActive = false,
                description = "Penutup: Peluruhan bertahap menuju hening"
            )
        }
    }
}
