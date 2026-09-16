package com.autoremix.djslow.engine

/**
 * State status trek vokal.
 */
enum class VocalTrackState(val label: String) {
    BELUM_ADA_VOKAL("BELUM ADA VOKAL"),
    VOKAL_DIPILIH("Vokal Dipilih")
}

/**
 * State status trek beat.
 */
enum class BeatTrackState(val label: String) {
    BELUM_ADA_BEAT("BELUM ADA BEAT"),
    BEAT_DIPILIH("Beat Dipilih")
}

/**
 * State status pemutaran audio nyata.
 */
enum class PlaybackEngineState(val label: String) {
    SIAP_DIPUTAR("SIAP DIPUTAR"),
    MEMUTAR("MEMUTAR"),
    DIJEDA("DIJEDA"),
    BERHENTI("BERHENTI"),
    ERROR("ERROR")
}

/**
 * Snapshot state lengkap Audio Engine.
 */
data class AudioState(
    val vocalState: VocalTrackState = VocalTrackState.BELUM_ADA_VOKAL,
    val beatState: BeatTrackState = BeatTrackState.BELUM_ADA_BEAT,
    val playbackState: PlaybackEngineState = PlaybackEngineState.BERHENTI,
    val vocalPositionMs: Long = 0L,
    val beatPositionMs: Long = 0L,
    val vocalDurationMs: Long = 0L,
    val beatDurationMs: Long = 0L,
    val vocalVolume: Float = 1.0f,
    val beatVolume: Float = 1.0f,
    val activeTrackTarget: ActiveTarget = ActiveTarget.BOTH,
    val renderedWavPath: String? = null,
    val renderedDurationMs: Long = 0L,
    val renderedPositionMs: Long = 0L,
    val isRenderedPlaying: Boolean = false,
    val isRenderedPaused: Boolean = false,
    val errorMessage: String? = null
) {
    enum class ActiveTarget {
        VOCAL, BEAT, BOTH
    }
}
