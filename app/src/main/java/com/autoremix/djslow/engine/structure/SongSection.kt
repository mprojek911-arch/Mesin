package com.autoremix.djslow.engine.structure

/**
 * Tipe seksi lagu sesuai spesifikasi Tahap 4 Arrangement:
 * INTRO, BUILD_UP, GROOVE, VERSE, PRE_DROP, DROP, BREAK, BREAKDOWN,
 * BUILD_UP_2, MAIN_DROP, PEAK, FINAL_BUILD, FINAL_DROP, OUTRO.
 */
enum class SongSectionType(
    val label: String,
    val defaultEnergyPercent: Int,
    val description: String
) {
    INTRO("INTRO", 20, "Atmosfir ringan, pad hangat, pengenalan tema"),
    BUILD_UP("BUILD", 35, "Peningkatan intensitas, snare & perkusi mulai masuk"),
    GROOVE("GROOVE", 50, "Ritme stabil, sinkronisasi vokal & ketukan inti"),
    VERSE("VERSE", 50, "Vokal dominan dengan instrumen pengiring seimbang"),
    PRE_DROP("PRE-DROP", 65, "Tensi memuncak, filter sweep, ancang-ancang drop"),
    DROP("DROP", 75, "Pukulan downbeat penuh, kick & bass punchy berdentum"),
    BREAK("BREAK", 45, "Penurunan elemen ritmis untuk memberi nafas lagu"),
    BREAKDOWN("BREAKDOWN", 55, "Kick & bass dihentikan, pad & vokal dominan"),
    BUILD_UP_2("BUILD 2", 70, "Pembangunan tensi kedua dengan riser & roll intens"),
    MAIN_DROP("DROP 2", 90, "Dentuman utama dengan seluruh elemen instrumen aktif"),
    PEAK("PEAK", 100, "Titik kepadatan aransemen tertinggi & transisi maksimal"),
    FINAL_BUILD("FINAL BUILD", 85, "Eskalasi dramatis terakhir menuju klimaks penutup"),
    FINAL_DROP("FINAL DROP", 100, "Ledakan klimaks penuh: Full kick, bass, lead, pad & FX"),
    OUTRO("OUTRO", 30, "Penurunan bertahap 70% ke 20% dengan reverb tail mulus");

    companion object {
        val BUILD: SongSectionType get() = BUILD_UP
        val BUILD_2: SongSectionType get() = BUILD_UP_2
        val DROP_2: SongSectionType get() = MAIN_DROP
    }
}

/**
 * Representasi seksi lagu terikat pada Master Timeline dan Master Clock.
 */
data class SongSection(
    val sectionType: SongSectionType,
    val startBar: Int,
    val endBar: Int,
    val startSample: Long,
    val endSample: Long,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val targetEnergy: Float, // 0.0f .. 1.0f
    val isVocalPhraseProtected: Boolean = true
) {
    val barCount: Int get() = maxOf(1, endBar - startBar)
    val durationMs: Long get() = maxOf(0L, endTimeMs - startTimeMs)

    fun formattedTimeRange(): String {
        val startMin = startTimeMs / 60000
        val startSec = (startTimeMs % 60000) / 1000
        val endMin = endTimeMs / 60000
        val endSec = (endTimeMs % 60000) / 1000
        return String.format("%02d:%02d - %02d:%02d", startMin, startSec, endMin, endSec)
    }

    fun formattedStartTime(): String {
        val startMin = startTimeMs / 60000
        val startSec = (startTimeMs % 60000) / 1000
        return String.format("%02d:%02d", startMin, startSec)
    }
}
