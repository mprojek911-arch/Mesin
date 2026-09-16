package com.autoremix.djslow.engine.mastering

/**
 * Preset Auto Mastering Cerdas (Tahap 5).
 * Setiap preset benar-benar mengubah parameter DSP:
 * EQ curves, target LUFS, compression ratio/threshold, stereo width, bass warmth, dan limiter ceiling.
 */
enum class MasteringPreset(
    val label: String,
    val description: String,
    val targetLufs: Float,
    val peakCeilingDbtp: Float,
    val eqSubCutHz: Float,
    val eqBassGainDb: Float,
    val eqLowMidCutDb: Float,
    val eqPresenceGainDb: Float,
    val eqAirGainDb: Float,
    val glueThresholdDb: Float,
    val glueRatio: Float,
    val saturationDrive: Float,
    val stereoWidth: Float,
    val vocalPocketDepthDb: Float
) {
    /**
     * Preset Default: Khas DJ Slow remix dengan sub-bass hangat, drum punchy, vokal jernih, dan lem perekat (glue) musikal.
     */
    DJ_SLOW(
        label = "DJ Slow (Default)",
        description = "Sub-bass hangat, drum bertenaga, vokal menonjol di tengah, lem perekat analog halus.",
        targetLufs = -11.5f,
        peakCeilingDbtp = -1.0f,
        eqSubCutHz = 28.0f,
        eqBassGainDb = 1.8f,
        eqLowMidCutDb = -1.5f,
        eqPresenceGainDb = 1.6f,
        eqAirGainDb = 1.8f,
        glueThresholdDb = -15.0f,
        glueRatio = 2.0f,
        saturationDrive = 0.15f,
        stereoWidth = 1.15f,
        vocalPocketDepthDb = -3.0f
    ),

    /**
     * Preset Natural: Dinamika terbuka, dynamic range tinggi, kurva EQ transparan, distorsi minimal.
     */
    NATURAL(
        label = "Natural",
        description = "Dinamika lebar & organik, kurva frekuensi datar seimbang, kompresi transparan.",
        targetLufs = -14.0f,
        peakCeilingDbtp = -1.0f,
        eqSubCutHz = 30.0f,
        eqBassGainDb = 0.0f,
        eqLowMidCutDb = -0.5f,
        eqPresenceGainDb = 0.5f,
        eqAirGainDb = 0.8f,
        glueThresholdDb = -18.0f,
        glueRatio = 1.6f,
        saturationDrive = 0.05f,
        stereoWidth = 1.05f,
        vocalPocketDepthDb = -2.0f
    ),

    /**
     * Preset Bass Strong: Menonjolkan sub-bass dan kick punch, hangat di low-end, cocok untuk party/club audio.
     */
    BASS_STRONG(
        label = "Bass Strong",
        description = "Sub-bass punchy & berbobot (50-80 Hz +3dB), kick tebal, saturasi analog bulat.",
        targetLufs = -11.0f,
        peakCeilingDbtp = -0.8f,
        eqSubCutHz = 25.0f,
        eqBassGainDb = 3.2f,
        eqLowMidCutDb = -2.0f,
        eqPresenceGainDb = 1.2f,
        eqAirGainDb = 1.2f,
        glueThresholdDb = -14.0f,
        glueRatio = 2.2f,
        saturationDrive = 0.22f,
        stereoWidth = 1.10f,
        vocalPocketDepthDb = -3.5f
    ),

    /**
     * Preset Vocal Clear: Membuka frekuensi vokal, memotong frekuensi musik di kantong vokal, udara atas berkilau.
     */
    VOCAL_CLEAR(
        label = "Vocal Clear",
        description = "Vokal super jernih di depan, kantong frekuensi vokal diperdalam, vokal bebas tertutup musik.",
        targetLufs = -12.0f,
        peakCeilingDbtp = -1.0f,
        eqSubCutHz = 32.0f,
        eqBassGainDb = 0.8f,
        eqLowMidCutDb = -2.5f,
        eqPresenceGainDb = 2.8f,
        eqAirGainDb = 2.2f,
        glueThresholdDb = -16.0f,
        glueRatio = 1.8f,
        saturationDrive = 0.10f,
        stereoWidth = 1.12f,
        vocalPocketDepthDb = -4.5f
    ),

    /**
     * Preset Loud Clean: Keras maksimal tanpa pecah, density RMS padat, limiter cepat dengan proteksi true peak.
     */
    LOUD_CLEAN(
        label = "Loud Clean",
        description = "Volume komersial tinggi & padat (-10 LUFS), transien terkontrol ketat, bebas pecah digital.",
        targetLufs = -10.0f,
        peakCeilingDbtp = -0.5f,
        eqSubCutHz = 30.0f,
        eqBassGainDb = 1.5f,
        eqLowMidCutDb = -1.8f,
        eqPresenceGainDb = 2.0f,
        eqAirGainDb = 2.0f,
        glueThresholdDb = -12.5f,
        glueRatio = 2.6f,
        saturationDrive = 0.20f,
        stereoWidth = 1.20f,
        vocalPocketDepthDb = -3.2f
    );

    val displayName: String get() = label
}
