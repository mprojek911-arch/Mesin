package com.autoremix.djslow.engine.arrangement

/**
 * Preset Auto DJ Slow Tahap 4:
 * DJ SLOW, DJ SLOW BASS, DJ PARTY, DJ FULL POWER, DJ EMOTIONAL, CUSTOM.
 * Mengontrol karakter aransemen, tempo acuan, kepadatan drum, ketebalan bass, melodi, dan pad.
 */
enum class AutoDjPreset(
    val label: String,
    val description: String,
    val defaultBpm: Float,
    val bassMultiplier: Float,
    val drumDensityMultiplier: Float,
    val melodyPresence: Float,
    val padWarmth: Float,
    val energyOffset: Float
) {
    DJ_SLOW(
        label = "DJ SLOW",
        description = "Gaya remix santai, tempo 80 BPM, bass empuk & ketukan berbobot",
        defaultBpm = 80.0f,
        bassMultiplier = 1.0f,
        drumDensityMultiplier = 0.85f,
        melodyPresence = 0.75f,
        padWarmth = 0.90f,
        energyOffset = 0.0f
    ),
    DJ_SLOW_BASS(
        label = "DJ SLOW BASS",
        description = "Sub-bass sangat bertenaga, sidechain mendalam untuk jedag-jedug santai",
        defaultBpm = 78.0f,
        bassMultiplier = 1.35f,
        drumDensityMultiplier = 0.90f,
        melodyPresence = 0.65f,
        padWarmth = 0.80f,
        energyOffset = 0.05f
    ),
    DJ_PARTY(
        label = "DJ PARTY",
        description = "Tempo enerjik, drum roll bersemangat, melodi riang dan drop membahana",
        defaultBpm = 90.0f,
        bassMultiplier = 1.10f,
        drumDensityMultiplier = 1.25f,
        melodyPresence = 1.10f,
        padWarmth = 0.70f,
        energyOffset = 0.15f
    ),
    DJ_FULL_POWER(
        label = "DJ FULL POWER",
        description = "Maksimal di semua lini: Kick keras, lead tajam, transisi padat",
        defaultBpm = 85.0f,
        bassMultiplier = 1.25f,
        drumDensityMultiplier = 1.35f,
        melodyPresence = 1.25f,
        padWarmth = 0.75f,
        energyOffset = 0.20f
    ),
    DJ_EMOTIONAL(
        label = "DJ EMOTIONAL",
        description = "Pad atmosferik luas, progresi akor mendalam, melodi lembut menyentuh",
        defaultBpm = 75.0f,
        bassMultiplier = 0.85f,
        drumDensityMultiplier = 0.65f,
        melodyPresence = 0.90f,
        padWarmth = 1.30f,
        energyOffset = -0.10f
    ),
    CUSTOM(
        label = "CUSTOM",
        description = "Pengaturan manual parameter aransemen sesuai preferensi",
        defaultBpm = 80.0f,
        bassMultiplier = 1.0f,
        drumDensityMultiplier = 1.0f,
        melodyPresence = 1.0f,
        padWarmth = 1.0f,
        energyOffset = 0.0f
    )
}
