package com.autoremix.djslow.engine.mix

/**
 * Pengaturan per-trek untuk mixing audio: Volume, Mute, dan Solo.
 */
data class MixTrackSettings(
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val isSolo: Boolean = false
) {
    /**
     * Menghitung gain efektif dengan memperhitungkan status Mute dan Solo terhadap trek lain.
     */
    fun computeEffectiveGain(isAnySoloActive: Boolean): Float {
        if (isMuted) return 0.0f
        if (isAnySoloActive && !isSolo) return 0.0f
        return volume.coerceIn(0.0f, 2.0f)
    }
}
