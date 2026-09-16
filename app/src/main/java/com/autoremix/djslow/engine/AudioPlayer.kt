package com.autoremix.djslow.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
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
 * Audio Player untuk pemutaran audio nyata (Vokal dan Beat) menggunakan Android MediaPlayer.
 */
class AudioPlayer(
    private val scope: CoroutineScope
) {
    private var vocalMediaPlayer: MediaPlayer? = null
    private var beatMediaPlayer: MediaPlayer? = null
    private var renderedMediaPlayer: MediaPlayer? = null

    private var vocalSource: AudioSource? = null
    private var beatSource: AudioSource? = null

    private val _audioState = MutableStateFlow(AudioState())
    val audioState: StateFlow<AudioState> = _audioState.asStateFlow()

    private var positionTickerJob: Job? = null

    init {
        startPositionTicker()
    }

    fun setVocalSource(context: Context, source: AudioSource): Result<Unit> {
        return try {
            vocalSource = source
            stopVocalInternal()
            vocalMediaPlayer?.release()

            val player = initMediaPlayer(context, source)
            vocalMediaPlayer = player

            val dur = if (player.duration > 0) player.duration.toLong() else source.durationMs
            _audioState.update { current ->
                current.copy(
                    vocalState = VocalTrackState.VOKAL_DIPILIH,
                    playbackState = if (current.playbackState == PlaybackEngineState.MEMUTAR) current.playbackState else PlaybackEngineState.SIAP_DIPUTAR,
                    vocalDurationMs = dur,
                    vocalPositionMs = 0L,
                    errorMessage = null
                )
            }
            player.setOnCompletionListener {
                _audioState.update { it.copy(playbackState = PlaybackEngineState.BERHENTI, vocalPositionMs = 0L) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            _audioState.update { it.copy(playbackState = PlaybackEngineState.ERROR, errorMessage = "Audio tidak dapat diputar.") }
            Result.failure(e)
        }
    }

    fun setBeatSource(context: Context, source: AudioSource): Result<Unit> {
        return try {
            beatSource = source
            stopBeatInternal()
            beatMediaPlayer?.release()

            val player = initMediaPlayer(context, source)
            beatMediaPlayer = player

            val dur = if (player.duration > 0) player.duration.toLong() else source.durationMs
            _audioState.update { current ->
                current.copy(
                    beatState = BeatTrackState.BEAT_DIPILIH,
                    playbackState = if (current.playbackState == PlaybackEngineState.MEMUTAR) current.playbackState else PlaybackEngineState.SIAP_DIPUTAR,
                    beatDurationMs = dur,
                    beatPositionMs = 0L,
                    errorMessage = null
                )
            }
            player.setOnCompletionListener {
                _audioState.update { it.copy(playbackState = PlaybackEngineState.BERHENTI, beatPositionMs = 0L) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            _audioState.update { it.copy(playbackState = PlaybackEngineState.ERROR, errorMessage = "Audio tidak dapat diputar.") }
            Result.failure(e)
        }
    }

    private fun initMediaPlayer(context: Context, source: AudioSource): MediaPlayer {
        val player = MediaPlayer()
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build()
        )
        context.contentResolver.openAssetFileDescriptor(source.uri, "r")?.use { afd ->
            player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        } ?: player.setDataSource(context, source.uri)

        player.prepare()
        return player
    }

    fun play() {
        var anyPlayed = false
        var errorOccurred = false

        try {
            vocalMediaPlayer?.let { player ->
                if (!player.isPlaying) {
                    player.start()
                    anyPlayed = true
                }
            }
        } catch (e: Exception) {
            errorOccurred = true
        }

        try {
            beatMediaPlayer?.let { player ->
                if (!player.isPlaying) {
                    player.start()
                    anyPlayed = true
                }
            }
        } catch (e: Exception) {
            errorOccurred = true
        }

        if (errorOccurred) {
            _audioState.update { it.copy(playbackState = PlaybackEngineState.ERROR, errorMessage = "Audio tidak dapat diputar.") }
        } else if (anyPlayed || (vocalMediaPlayer?.isPlaying == true) || (beatMediaPlayer?.isPlaying == true)) {
            _audioState.update { it.copy(playbackState = PlaybackEngineState.MEMUTAR, errorMessage = null) }
        }
    }

    fun pause() {
        var anyPaused = false
        try {
            vocalMediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    anyPaused = true
                }
            }
            beatMediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                    anyPaused = true
                }
            }
            _audioState.update { it.copy(playbackState = PlaybackEngineState.DIJEDA) }
        } catch (e: Exception) {
            _audioState.update { it.copy(playbackState = PlaybackEngineState.ERROR, errorMessage = "Audio tidak dapat diputar.") }
        }
    }

    fun stop() {
        stopVocalInternal()
        stopBeatInternal()
        _audioState.update {
            it.copy(
                playbackState = PlaybackEngineState.BERHENTI,
                vocalPositionMs = 0L,
                beatPositionMs = 0L
            )
        }
    }

    private fun stopVocalInternal() {
        try {
            vocalMediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                }
                it.seekTo(0)
            }
        } catch (_: Exception) {}
    }

    private fun stopBeatInternal() {
        try {
            beatMediaPlayer?.let {
                if (it.isPlaying) {
                    it.pause()
                }
                it.seekTo(0)
            }
        } catch (_: Exception) {}
    }

    fun seekTo(positionFraction: Float) {
        val frac = positionFraction.coerceIn(0f, 1f)
        try {
            vocalMediaPlayer?.let { player ->
                val targetMs = (player.duration * frac).toInt()
                player.seekTo(targetMs)
                _audioState.update { it.copy(vocalPositionMs = targetMs.toLong()) }
            }
            beatMediaPlayer?.let { player ->
                val targetMs = (player.duration * frac).toInt()
                player.seekTo(targetMs)
                _audioState.update { it.copy(beatPositionMs = targetMs.toLong()) }
            }
        } catch (e: Exception) {
            _audioState.update { it.copy(errorMessage = "Gagal memindahkan posisi audio.") }
        }
    }

    fun seekVocalTo(positionMs: Long) {
        try {
            vocalMediaPlayer?.let { player ->
                val target = positionMs.coerceIn(0L, player.duration.toLong()).toInt()
                player.seekTo(target)
                _audioState.update { it.copy(vocalPositionMs = target.toLong()) }
            }
        } catch (_: Exception) {}
    }

    fun seekBeatTo(positionMs: Long) {
        try {
            beatMediaPlayer?.let { player ->
                val target = positionMs.coerceIn(0L, player.duration.toLong()).toInt()
                player.seekTo(target)
                _audioState.update { it.copy(beatPositionMs = target.toLong()) }
            }
        } catch (_: Exception) {}
    }

    fun setVocalVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        try {
            vocalMediaPlayer?.setVolume(clamped, clamped)
            _audioState.update { it.copy(vocalVolume = clamped) }
        } catch (_: Exception) {}
    }

    fun setBeatVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        try {
            beatMediaPlayer?.setVolume(clamped, clamped)
            _audioState.update { it.copy(beatVolume = clamped) }
        } catch (_: Exception) {}
    }

    /**
     * Memuat berkas WAV hasil render nyata ke dalam MediaPlayer khusus hasil.
     */
    fun setRenderedWav(file: java.io.File): Result<Unit> {
        return try {
            renderedMediaPlayer?.stop()
            renderedMediaPlayer?.release()

            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            player.setDataSource(file.absolutePath)
            player.prepare()
            renderedMediaPlayer = player

            val dur = player.duration.toLong()
            _audioState.update {
                it.copy(
                    renderedWavPath = file.absolutePath,
                    renderedDurationMs = dur,
                    renderedPositionMs = 0L,
                    isRenderedPlaying = false,
                    isRenderedPaused = false
                )
            }

            player.setOnCompletionListener {
                _audioState.update {
                    it.copy(
                        isRenderedPlaying = false,
                        isRenderedPaused = false,
                        renderedPositionMs = 0L
                    )
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            _audioState.update { it.copy(errorMessage = "Gagal memuat berkas WAV hasil render: ${e.localizedMessage}") }
            Result.failure(e)
        }
    }

    fun playRendered(): Result<Unit> {
        return try {
            // Hentikan pemutaran preview vokal dan beat agar tidak tumpang tindih
            stopVocalInternal()
            stopBeatInternal()

            val player = renderedMediaPlayer
                ?: return Result.failure(IllegalStateException("Belum ada file WAV hasil render."))

            player.start()
            _audioState.update {
                it.copy(
                    isRenderedPlaying = true,
                    isRenderedPaused = false
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            _audioState.update { it.copy(errorMessage = "Gagal memutar hasil WAV: ${e.localizedMessage}") }
            Result.failure(e)
        }
    }

    fun pauseRendered(): Result<Unit> {
        return try {
            renderedMediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                }
                _audioState.update {
                    it.copy(
                        isRenderedPlaying = false,
                        isRenderedPaused = true,
                        renderedPositionMs = player.currentPosition.toLong()
                    )
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun stopRendered(): Result<Unit> {
        return try {
            renderedMediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                    player.prepare()
                }
                player.seekTo(0)
                _audioState.update {
                    it.copy(
                        isRenderedPlaying = false,
                        isRenderedPaused = false,
                        renderedPositionMs = 0L
                    )
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun seekRendered(positionFraction: Float) {
        val frac = positionFraction.coerceIn(0f, 1f)
        try {
            renderedMediaPlayer?.let { player ->
                val targetMs = (player.duration * frac).toInt()
                player.seekTo(targetMs)
                _audioState.update { it.copy(renderedPositionMs = targetMs.toLong()) }
            }
        } catch (_: Exception) {}
    }

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                try {
                    val vocalPos = vocalMediaPlayer?.takeIf { it.isPlaying }?.currentPosition?.toLong()
                    val beatPos = beatMediaPlayer?.takeIf { it.isPlaying }?.currentPosition?.toLong()
                    val rendPos = renderedMediaPlayer?.takeIf { it.isPlaying }?.currentPosition?.toLong()

                    _audioState.update { cur ->
                        cur.copy(
                            vocalPositionMs = vocalPos ?: cur.vocalPositionMs,
                            beatPositionMs = beatPos ?: cur.beatPositionMs,
                            renderedPositionMs = rendPos ?: cur.renderedPositionMs
                        )
                    }
                } catch (_: Exception) {}
                delay(100)
            }
        }
    }

    fun release() {
        positionTickerJob?.cancel()
        try { vocalMediaPlayer?.release() } catch (_: Exception) {}
        try { beatMediaPlayer?.release() } catch (_: Exception) {}
        try { renderedMediaPlayer?.release() } catch (_: Exception) {}
        vocalMediaPlayer = null
        beatMediaPlayer = null
        renderedMediaPlayer = null
    }
}
