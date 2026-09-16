package com.autoremix.djslow.engine.mix

/**
 * Representasi Arsitektur Bus Mixing (Tahap 5).
 * - VOCAL BUS
 * - BEAT BUS
 * - DRUM BUS
 * - BASS BUS
 * - MUSIC BUS (Akor + Melodi + Pad + FX)
 * - MASTER BUS
 */
enum class BusType(val label: String) {
    VOCAL_BUS("Vocal Bus"),
    BEAT_BUS("Beat Bus"),
    DRUM_BUS("Drum Bus"),
    BASS_BUS("Bass Bus"),
    MUSIC_BUS("Music Bus"),
    MASTER_BUS("Master Bus")
}

data class BusSettings(
    val busType: BusType,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val isSolo: Boolean = false
) {
    val effectiveGain: Float
        get() = if (isMuted) 0.0f else volume.coerceIn(0.0f, 2.0f)
}
