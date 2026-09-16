package com.autoremix.djslow.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import com.autoremix.djslow.model.PlaybackStatus
import com.autoremix.djslow.model.TrackPlaybackState
import com.autoremix.djslow.model.TrackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pengelola audio playback nyata menggunakan Android MediaPlayer dan ContentResolver.
 * Mendukung pemutaran, jeda, stop, seek, dan pengaturan volume untuk Vokal dan Beat secara independen.
 */
class RealAudioPlayer(
    private val scope: CoroutineScope
) {
    private var vocalPlayer: MediaPlayer? = null
    private var beatPlayer: MediaPlayer? = null

    private var vocalUri: Uri? = null
    private var beatUri: Uri? = null

    private val _vocalState = MutableStateFlow(TrackPlaybackState())
    val vocalState: StateFlow<TrackPlaybackState> = _vocalState.asStateFlow()

    private val _beatState = MutableStateFlow(TrackPlaybackState())
    val beatState: StateFlow<TrackPlaybackState> = _beatState.asStateFlow()

    private var progressJob: Job? = null

    init {
        startProgressTracker()
    }

    /**
     * Memuat berkas audio nyata dari URI pengguna melalui ContentResolver.
     */
    fun loadTrack(context: Context, trackType: TrackType, uri: Uri, durationMs: Long): Boolean {
        return try {
            when (trackType) {
                TrackType.VOCAL -> {
                    vocalUri = uri
                    stop(TrackType.VOCAL)
                    vocalPlayer?.release()
                    val player = createConfiguredPlayer(context, uri) { errorMsg ->
                        _vocalState.update {
                            it.copy(
                                status = PlaybackStatus.ERROR,
                                errorMessage = errorMsg
                            )
                        }
                    }
                    vocalPlayer = player
                    val detectedDuration = if (player.duration > 0) player.duration.toLong() else durationMs
                    _vocalState.update {
                        it.copy(
                            status = PlaybackStatus.IDLE,
                            currentPositionMs = 0L,
                            durationMs = detectedDuration,
                            errorMessage = null
                        )
                    }
                    setupCompletionListener(player, TrackType.VOCAL)
                    true
                }
                TrackType.BEAT -> {
                    beatUri = uri
                    stop(TrackType.BEAT)
                    beatPlayer?.release()
                    val player = createConfiguredPlayer(context, uri) { errorMsg ->
                        _beatState.update {
                            it.copy(
                                status = PlaybackStatus.ERROR,
                                errorMessage = errorMsg
                            )
                        }
                    }
                    beatPlayer = player
                    val detectedDuration = if (player.duration > 0) player.duration.toLong() else durationMs
                    _beatState.update {
                        it.copy(
                            status = PlaybackStatus.IDLE,
                            currentPositionMs = 0L,
                            durationMs = detectedDuration,
                            errorMessage = null
                        )
                    }
                    setupCompletionListener(player, TrackType.BEAT)
                    true
                }
            }
        } catch (e: Exception) {
            val errorMsg = "Gagal memuat audio: ${e.localizedMessage ?: "Format tidak dapat diputar"}"
            when (trackType) {
                TrackType.VOCAL -> _vocalState.update {
                    it.copy(status = PlaybackStatus.ERROR, errorMessage = errorMsg)
                }
                TrackType.BEAT -> _beatState.update {
                    it.copy(status = PlaybackStatus.ERROR, errorMessage = errorMsg)
                }
            }
            false
        }
    }

    private fun createConfiguredPlayer(
        context: Context,
        uri: Uri,
        onError: (String) -> Unit
    ): MediaPlayer {
        val player = MediaPlayer()
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
        )

        // Buka via openAssetFileDescriptor untuk memastikan kompatibilitas penuh SAF
        context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
            player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        } ?: player.setDataSource(context, uri)

        player.setOnErrorListener { _, what, extra ->
            onError("Kesalahan playback media ($what, $extra)")
            true
        }

        player.prepare()
        return player
    }

    private fun setupCompletionListener(player: MediaPlayer, trackType: TrackType) {
        player.setOnCompletionListener {
            when (trackType) {
                TrackType.VOCAL -> _vocalState.update {
                    it.copy(status = PlaybackStatus.STOPPED, currentPositionMs = 0L)
                }
                TrackType.BEAT -> _beatState.update {
                    it.copy(status = PlaybackStatus.STOPPED, currentPositionMs = 0L)
                }
            }
        }
    }

    fun play(trackType: TrackType) {
        when (trackType) {
            TrackType.VOCAL -> {
                vocalPlayer?.let { player ->
                    try {
                        if (!player.isPlaying) {
                            player.start()
                            _vocalState.update {
                                it.copy(status = PlaybackStatus.PLAYING, errorMessage = null)
                            }
                        }
                    } catch (e: Exception) {
                        _vocalState.update {
                            it.copy(status = PlaybackStatus.ERROR, errorMessage = e.localizedMessage)
                        }
                    }
                }
            }
            TrackType.BEAT -> {
                beatPlayer?.let { player ->
                    try {
                        if (!player.isPlaying) {
                            player.start()
                            _beatState.update {
                                it.copy(status = PlaybackStatus.PLAYING, errorMessage = null)
                            }
                        }
                    } catch (e: Exception) {
                        _beatState.update {
                            it.copy(status = PlaybackStatus.ERROR, errorMessage = e.localizedMessage)
                        }
                    }
                }
            }
        }
    }

    fun pause(trackType: TrackType) {
        when (trackType) {
            TrackType.VOCAL -> {
                vocalPlayer?.let { player ->
                    try {
                        if (player.isPlaying) {
                            player.pause()
                            _vocalState.update {
                                it.copy(status = PlaybackStatus.PAUSED)
                            }
                        }
                    } catch (e: Exception) {
                        _vocalState.update {
                            it.copy(status = PlaybackStatus.ERROR, errorMessage = e.localizedMessage)
                        }
                    }
                }
            }
            TrackType.BEAT -> {
                beatPlayer?.let { player ->
                    try {
                        if (player.isPlaying) {
                            player.pause()
                            _beatState.update {
                                it.copy(status = PlaybackStatus.PAUSED)
                            }
                        }
                    } catch (e: Exception) {
                        _beatState.update {
                            it.copy(status = PlaybackStatus.ERROR, errorMessage = e.localizedMessage)
                        }
                    }
                }
            }
        }
    }

    fun stop(trackType: TrackType) {
        when (trackType) {
            TrackType.VOCAL -> {
                vocalPlayer?.let { player ->
                    try {
                        if (player.isPlaying) {
                            player.pause()
                        }
                        player.seekTo(0)
                        _vocalState.update {
                            it.copy(status = PlaybackStatus.STOPPED, currentPositionMs = 0L)
                        }
                    } catch (e: Exception) {
                        _vocalState.update {
                            it.copy(status = PlaybackStatus.ERROR, errorMessage = e.localizedMessage)
                        }
                    }
                }
            }
            TrackType.BEAT -> {
                beatPlayer?.let { player ->
                    try {
                        if (player.isPlaying) {
                            player.pause()
                        }
                        player.seekTo(0)
                        _beatState.update {
                            it.copy(status = PlaybackStatus.STOPPED, currentPositionMs = 0L)
                        }
                    } catch (e: Exception) {
                        _beatState.update {
                            it.copy(status = PlaybackStatus.ERROR, errorMessage = e.localizedMessage)
                        }
                    }
                }
            }
        }
    }

    fun seekTo(trackType: TrackType, positionMs: Long) {
        when (trackType) {
            TrackType.VOCAL -> {
                vocalPlayer?.let { player ->
                    try {
                        val safePos = positionMs.coerceIn(0L, player.duration.toLong()).toInt()
                        player.seekTo(safePos)
                        _vocalState.update {
                            it.copy(currentPositionMs = safePos.toLong())
                        }
                    } catch (e: Exception) {
                        _vocalState.update {
                            it.copy(errorMessage = "Gagal memindahkan posisi: ${e.localizedMessage}")
                        }
                    }
                }
            }
            TrackType.BEAT -> {
                beatPlayer?.let { player ->
                    try {
                        val safePos = positionMs.coerceIn(0L, player.duration.toLong()).toInt()
                        player.seekTo(safePos)
                        _beatState.update {
                            it.copy(currentPositionMs = safePos.toLong())
                        }
                    } catch (e: Exception) {
                        _beatState.update {
                            it.copy(errorMessage = "Gagal memindahkan posisi: ${e.localizedMessage}")
                        }
                    }
                }
            }
        }
    }

    fun setVolume(trackType: TrackType, volume: Float) {
        val clampedVol = volume.coerceIn(0f, 1f)
        when (trackType) {
            TrackType.VOCAL -> {
                vocalPlayer?.setVolume(clampedVol, clampedVol)
                _vocalState.update { it.copy(volume = clampedVol) }
            }
            TrackType.BEAT -> {
                beatPlayer?.setVolume(clampedVol, clampedVol)
                _beatState.update { it.copy(volume = clampedVol) }
            }
        }
    }

    fun playBoth() {
        play(TrackType.VOCAL)
        play(TrackType.BEAT)
    }

    fun pauseBoth() {
        pause(TrackType.VOCAL)
        pause(TrackType.BEAT)
    }

    fun stopBoth() {
        stop(TrackType.VOCAL)
        stop(TrackType.BEAT)
    }

    private fun startProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                try {
                    vocalPlayer?.let { player ->
                        if (player.isPlaying) {
                            val pos = player.currentPosition.toLong()
                            _vocalState.update { it.copy(currentPositionMs = pos) }
                        }
                    }
                    beatPlayer?.let { player ->
                        if (player.isPlaying) {
                            val pos = player.currentPosition.toLong()
                            _beatState.update { it.copy(currentPositionMs = pos) }
                        }
                    }
                } catch (_: Exception) { }
                delay(100)
            }
        }
    }

    fun release() {
        progressJob?.cancel()
        try {
            vocalPlayer?.release()
        } catch (_: Exception) { }
        try {
            beatPlayer?.release()
        } catch (_: Exception) { }
        vocalPlayer = null
        beatPlayer = null
    }
}
