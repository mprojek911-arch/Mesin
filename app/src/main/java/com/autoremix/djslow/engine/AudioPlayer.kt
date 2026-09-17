package com.autoremix.djslow.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.autoremix.djslow.engine.wav.WavValidator
import com.autoremix.djslow.logchat.LogChatManager
import com.autoremix.djslow.logchat.LogModule
import com.autoremix.djslow.logchat.PipelineStage
import com.autoremix.djslow.logchat.StepStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
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
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private var vocalMediaPlayer: MediaPlayer? = null
    private var beatMediaPlayer: MediaPlayer? = null
    private var renderedMediaPlayer: MediaPlayer? = null
    private var unmasteredMediaPlayer: MediaPlayer? = null

    private var vocalSource: AudioSource? = null
    private var beatSource: AudioSource? = null

    private val _audioState = MutableStateFlow(AudioState())
    val audioState: StateFlow<AudioState> = _audioState.asStateFlow()

    private var positionTickerJob: Job? = null
    private var sectionLimitJob: Job? = null

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
        // Cegah double playback: hentikan pemutar master jika sedang aktif (Aturan 13 & 14)
        try {
            renderedMediaPlayer?.let { if (it.isPlaying) it.pause() }
            unmasteredMediaPlayer?.let { if (it.isPlaying) it.pause() }
            _audioState.update { it.copy(isRenderedPlaying = false, isRenderedPaused = false) }
        } catch (_: Exception) {}

        var anyPlayed = false
        var errorOccurred = false
        var lastError: Throwable? = null

        try {
            vocalMediaPlayer?.let { player ->
                if (!player.isPlaying) {
                    player.start()
                    anyPlayed = true
                }
            }
        } catch (e: Exception) {
            errorOccurred = true
            lastError = e
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
            lastError = e
        }

        if (errorOccurred) {
            _audioState.update { it.copy(playbackState = PlaybackEngineState.ERROR, errorMessage = "Audio tidak dapat diputar.") }
            LogChatManager.error(LogModule.PLAYBACK, "PLAYBACK_ERROR: Gagal memutar track audio.", lastError, stage = "PLAYBACK")
            LogChatManager.updatePipeline(PipelineStage.PLAYBACK, StepStatus.FAILED, lastError?.message ?: "Playback error")
        } else if (anyPlayed || (vocalMediaPlayer?.isPlaying == true) || (beatMediaPlayer?.isPlaying == true)) {
            _audioState.update { it.copy(playbackState = PlaybackEngineState.MEMUTAR, errorMessage = null) }
            LogChatManager.info(LogModule.PLAYBACK, "PLAYBACK_START: Pemutaran audio sinkron dimulai.", stage = "PLAYBACK")
            LogChatManager.updatePipeline(PipelineStage.PLAYBACK, StepStatus.SUCCESS, "Memutar audio")
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
            LogChatManager.info(LogModule.PLAYBACK, "PLAYBACK_PAUSE: Pemutaran dijeda.", stage = "PLAYBACK")
        } catch (e: Exception) {
            _audioState.update { it.copy(playbackState = PlaybackEngineState.ERROR, errorMessage = "Audio tidak dapat diputar.") }
            LogChatManager.error(LogModule.PLAYBACK, "PLAYBACK_ERROR: Gagal menjeda audio.", e, stage = "PLAYBACK")
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
        LogChatManager.info(LogModule.PLAYBACK, "PLAYBACK_STOP: Pemutaran dihentikan (kembali ke 0).", stage = "PLAYBACK")
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
            renderedMediaPlayer = null

            // 1. Validasi berkas fisik & format WAV sebelum memuat
            if (!file.exists() || file.length() <= 0L) {
                val ex = IllegalStateException("🔴 AUDIO TIDAK VALID: Berkas tidak ditemukan atau kosong (0 byte)")
                LogChatManager.error(
                    module = LogModule.PLAYBACK,
                    message = "PLAYBACK_ERROR: ${ex.message}",
                    throwable = ex,
                    stage = "MONITOR_HASIL_REMIX"
                )
                _audioState.update {
                    it.copy(
                        renderedPlaybackStatus = RenderedPlaybackStatus.ERROR,
                        errorMessage = ex.message
                    )
                }
                return Result.failure(ex)
            }

            val valRes = WavValidator.validate(file, scanContent = false)
            if (!valRes.isValid) {
                val reason = valRes.errorMessage ?: "Format WAV atau integritas audio tidak valid"
                val ex = IllegalStateException("🔴 AUDIO TIDAK VALID: $reason")
                LogChatManager.error(
                    module = LogModule.PLAYBACK,
                    message = "PLAYBACK_ERROR: ${ex.message}",
                    throwable = ex,
                    stage = "MONITOR_HASIL_REMIX",
                    file = file.name
                )
                _audioState.update {
                    it.copy(
                        renderedPlaybackStatus = RenderedPlaybackStatus.ERROR,
                        errorMessage = ex.message
                    )
                }
                return Result.failure(ex)
            }

            // 2. Inisialisasi MediaPlayer Android nyata
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            player.setDataSource(file.absolutePath)
            player.prepare()
            player.isLooping = false
            renderedMediaPlayer = player

            val dur = if (player.duration > 0) player.duration.toLong() else valRes.durationMs
            _audioState.update {
                it.copy(
                    renderedWavPath = file.absolutePath,
                    renderedDurationMs = dur,
                    renderedPositionMs = 0L,
                    isRenderedPlaying = false,
                    isRenderedPaused = false,
                    renderedPlaybackStatus = RenderedPlaybackStatus.SIAP,
                    errorMessage = null
                )
            }

            // Aturan 9: Saat audio selesai -> STATUS = SELESAI, POSITION = TOTAL DURATION, JANGAN LOOP
            player.setOnCompletionListener {
                val totalDur = if (player.duration > 0) player.duration.toLong() else _audioState.value.renderedDurationMs
                _audioState.update {
                    it.copy(
                        isRenderedPlaying = false,
                        isRenderedPaused = false,
                        renderedPositionMs = totalDur,
                        renderedPlaybackStatus = RenderedPlaybackStatus.SELESAI
                    )
                }
                LogChatManager.info(
                    module = LogModule.PLAYBACK,
                    message = "PLAYBACK_COMPLETE: Pemutaran hasil remix selesai (durasi: ${formatTime(totalDur)})",
                    stage = "MONITOR_HASIL_REMIX",
                    file = file.name
                )
            }

            LogChatManager.info(
                module = LogModule.PLAYBACK,
                message = "🎧 HASIL REMIX SIAP DIPUTAR: ${file.name} (Durasi: ${formatTime(dur)})",
                stage = "MONITOR_HASIL_REMIX",
                file = file.name
            )

            Result.success(Unit)
        } catch (e: Exception) {
            _audioState.update {
                it.copy(
                    renderedPlaybackStatus = RenderedPlaybackStatus.ERROR,
                    errorMessage = "Gagal memuat berkas WAV hasil render: ${e.localizedMessage}"
                )
            }
            LogChatManager.error(
                module = LogModule.PLAYBACK,
                message = "PLAYBACK_ERROR: Gagal memuat berkas WAV: ${e.message}",
                throwable = e,
                stage = "MONITOR_HASIL_REMIX",
                file = file.name
            )
            Result.failure(e)
        }
    }

    /**
     * Memutar seluruh hasil remix sebagai satu audio final.
     * Menghentikan source player lain, memverifikasi file, dan memperbarui status pemutaran.
     */
    fun playRendered(): Result<Unit> {
        return try {
            val path = _audioState.value.renderedWavPath
            if (path == null) {
                val ex = IllegalStateException("Belum ada file WAV hasil render.")
                LogChatManager.error(
                    module = LogModule.PLAYBACK,
                    message = "PLAYBACK_ERROR: ${ex.message}",
                    throwable = ex,
                    stage = "MONITOR_HASIL_REMIX"
                )
                return Result.failure(ex)
            }

            val file = java.io.File(path)
            // Validasi sebelum mengizinkan play (Aturan 13)
            if (!file.exists() || file.length() <= 0L) {
                val ex = IllegalStateException("🔴 AUDIO TIDAK VALID: Berkas tidak ditemukan atau kosong")
                LogChatManager.error(
                    module = LogModule.PLAYBACK,
                    message = "PLAYBACK_ERROR: ${ex.message}",
                    throwable = ex,
                    stage = "MONITOR_HASIL_REMIX"
                )
                _audioState.update {
                    it.copy(
                        renderedPlaybackStatus = RenderedPlaybackStatus.ERROR,
                        errorMessage = ex.message
                    )
                }
                return Result.failure(ex)
            }

            // 1. Stop / pause source player lain (Aturan 6 & 13: Single Master Playback)
            stopVocalInternal()
            stopBeatInternal()
            unmasteredMediaPlayer?.let { if (it.isPlaying) it.pause() }
            _audioState.update { it.copy(playbackState = PlaybackEngineState.BERHENTI) }

            // 2. Load hasil remix jika belum
            val player = renderedMediaPlayer ?: run {
                val initRes = setRenderedWav(file)
                if (initRes.isFailure) return initRes
                renderedMediaPlayer ?: return Result.failure(IllegalStateException("Gagal inisialisasi media player."))
            }

            val wasPaused = _audioState.value.isRenderedPaused
            val wasCompleted = _audioState.value.renderedPlaybackStatus == RenderedPlaybackStatus.SELESAI ||
                    player.currentPosition >= (player.duration - 200).coerceAtLeast(0)

            if (wasCompleted) {
                player.seekTo(0)
                _audioState.update { it.copy(renderedPositionMs = 0L) }
            }

            // 3. Start hasil remix (Aturan 17: renderedMediaPlayer.start() -> Audio Output)
            player.start()

            // 4. Update playback state & position
            val currentPos = player.currentPosition.toLong()
            _audioState.update {
                it.copy(
                    isRenderedPlaying = true,
                    isRenderedPaused = false,
                    renderedPositionMs = currentPos,
                    renderedPlaybackStatus = RenderedPlaybackStatus.SEDANG_MEMUTAR,
                    errorMessage = null
                )
            }

            if (wasPaused) {
                LogChatManager.info(
                    module = LogModule.PLAYBACK,
                    message = "PLAYBACK_RESUME: Melanjutkan pemutaran hasil remix dari ${formatTime(currentPos)}",
                    stage = "MONITOR_HASIL_REMIX",
                    file = file.name
                )
            } else {
                LogChatManager.info(
                    module = LogModule.PLAYBACK,
                    message = "PLAYBACK_START: Memulai pemutaran hasil remix full",
                    stage = "MONITOR_HASIL_REMIX",
                    file = file.name
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            _audioState.update {
                it.copy(
                    isRenderedPlaying = false,
                    renderedPlaybackStatus = RenderedPlaybackStatus.ERROR,
                    errorMessage = "Gagal memutar hasil WAV: ${e.localizedMessage}"
                )
            }
            LogChatManager.error(
                module = LogModule.PLAYBACK,
                message = "PLAYBACK_ERROR: Gagal memutar hasil remix: ${e.message}",
                throwable = e,
                stage = "MONITOR_HASIL_REMIX"
            )
            Result.failure(e)
        }
    }

    /**
     * Menjeda pemutaran hasil remix tanpa mereset posisi audio (Aturan 7).
     */
    fun pauseRendered(): Result<Unit> {
        return try {
            renderedMediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.pause()
                }
                val pos = player.currentPosition.toLong()
                _audioState.update {
                    it.copy(
                        isRenderedPlaying = false,
                        isRenderedPaused = true,
                        renderedPositionMs = pos,
                        renderedPlaybackStatus = RenderedPlaybackStatus.DIJEDA
                    )
                }
                LogChatManager.info(
                    module = LogModule.PLAYBACK,
                    message = "PLAYBACK_PAUSE: Pemutaran hasil remix dijeda pada ${formatTime(pos)}",
                    stage = "MONITOR_HASIL_REMIX"
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            LogChatManager.error(
                module = LogModule.PLAYBACK,
                message = "PLAYBACK_ERROR: Gagal menjeda hasil remix: ${e.message}",
                throwable = e,
                stage = "MONITOR_HASIL_REMIX"
            )
            Result.failure(e)
        }
    }

    /**
     * Menghentikan pemutaran hasil remix dan mereset posisi ke 00:00 (Aturan 8).
     */
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
                        renderedPositionMs = 0L,
                        renderedPlaybackStatus = RenderedPlaybackStatus.BERHENTI
                    )
                }
                LogChatManager.info(
                    module = LogModule.PLAYBACK,
                    message = "PLAYBACK_STOP: Pemutaran dihentikan, posisi direset ke 00:00",
                    stage = "MONITOR_HASIL_REMIX"
                )
            }
            Result.success(Unit)
        } catch (e: Exception) {
            LogChatManager.error(
                module = LogModule.PLAYBACK,
                message = "PLAYBACK_ERROR: Gagal menghentikan pemutaran: ${e.message}",
                throwable = e,
                stage = "MONITOR_HASIL_REMIX"
            )
            Result.failure(e)
        }
    }

    /**
     * Menggeser posisi pemutaran hasil remix ke fraksi 0..1 (Aturan 4).
     */
    fun seekRendered(positionFraction: Float) {
        val frac = positionFraction.coerceIn(0f, 1f)
        try {
            val totalDur = (renderedMediaPlayer?.duration?.toLong() ?: _audioState.value.renderedDurationMs).coerceAtLeast(1L)
            val targetMs = (totalDur * frac).toLong()
            seekRenderedMs(targetMs)
        } catch (_: Exception) {}
    }

    /**
     * Menggeser posisi audio langsung dalam milidetik.
     */
    fun seekRenderedMs(targetMs: Long) {
        try {
            val totalDur = (renderedMediaPlayer?.duration?.toLong() ?: _audioState.value.renderedDurationMs).coerceAtLeast(1L)
            val clampedMs = targetMs.coerceIn(0L, totalDur).toInt()
            renderedMediaPlayer?.seekTo(clampedMs)
            unmasteredMediaPlayer?.seekTo(clampedMs)

            val newStatus = if (clampedMs >= totalDur - 100) {
                RenderedPlaybackStatus.SELESAI
            } else if (_audioState.value.isRenderedPlaying) {
                RenderedPlaybackStatus.SEDANG_MEMUTAR
            } else if (_audioState.value.isRenderedPaused) {
                RenderedPlaybackStatus.DIJEDA
            } else {
                _audioState.value.renderedPlaybackStatus
            }

            _audioState.update {
                it.copy(
                    renderedPositionMs = clampedMs.toLong(),
                    renderedPlaybackStatus = newStatus
                )
            }
            LogChatManager.info(
                module = LogModule.PLAYBACK,
                message = "PLAYBACK_SEEK: Menggeser posisi audio ke ${formatTime(clampedMs.toLong())}",
                stage = "MONITOR_HASIL_REMIX"
            )
        } catch (e: Exception) {
            LogChatManager.error(
                module = LogModule.PLAYBACK,
                message = "PLAYBACK_ERROR: Gagal melakukan seek: ${e.message}",
                throwable = e,
                stage = "MONITOR_HASIL_REMIX"
            )
        }
    }

    /**
     * Mundur atau maju relatif (misal -10s atau +10s).
     */
    fun seekRelative(deltaMs: Long) {
        val curPos = renderedMediaPlayer?.currentPosition?.toLong() ?: _audioState.value.renderedPositionMs
        seekRenderedMs(curPos + deltaMs)
    }

    private fun formatTime(ms: Long): String {
        val totalSec = (ms / 1000).coerceAtLeast(0)
        val min = totalSec / 60
        val sec = totalSec % 60
        return String.format(java.util.Locale.US, "%02d:%02d", min, sec)
    }

    /**
     * Memuat berkas WAV unmastered (pre-master mix) untuk perbandingan A/B.
     */
    fun setUnmasteredWav(file: java.io.File): Result<Unit> {
        return try {
            unmasteredMediaPlayer?.stop()
            unmasteredMediaPlayer?.release()

            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build()
            )
            player.setDataSource(file.absolutePath)
            player.prepare()
            unmasteredMediaPlayer = player

            _audioState.update {
                it.copy(
                    unmasteredWavPath = file.absolutePath,
                    isUnmasteredPlaying = false
                )
            }

            player.setOnCompletionListener {
                _audioState.update { it.copy(isUnmasteredPlaying = false) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Memutar audio dengan memilih sumber A/B secara sinkron:
     * - [ORIGINAL]: Trek mentah vokal & beat
     * - [REMIX]: Mix unmastered pre-master
     * - [MASTERED]: Master final dengan Loudness Matching gain
     */
    fun switchAbMode(
        mode: com.autoremix.djslow.engine.preview.AbPreviewController.AbMode,
        gainFactor: Float = 1.0f
    ): Result<Unit> {
        return try {
            val currentPos = renderedMediaPlayer?.currentPosition
                ?: unmasteredMediaPlayer?.currentPosition
                ?: vocalMediaPlayer?.currentPosition
                ?: 0

            _audioState.update {
                it.copy(
                    currentAbMode = mode,
                    loudnessGainFactor = gainFactor
                )
            }

            // Hentikan sumber yang tidak aktif
            when (mode) {
                com.autoremix.djslow.engine.preview.AbPreviewController.AbMode.ORIGINAL -> {
                    renderedMediaPlayer?.pause()
                    unmasteredMediaPlayer?.pause()
                    vocalMediaPlayer?.let {
                        it.seekTo(currentPos)
                        if (!it.isPlaying) it.start()
                    }
                    beatMediaPlayer?.let {
                        it.seekTo(currentPos)
                        if (!it.isPlaying) it.start()
                    }
                    _audioState.update {
                        it.copy(
                            playbackState = PlaybackEngineState.MEMUTAR,
                            isRenderedPlaying = false,
                            isUnmasteredPlaying = false
                        )
                    }
                }
                com.autoremix.djslow.engine.preview.AbPreviewController.AbMode.REMIX -> {
                    stopVocalInternal()
                    stopBeatInternal()
                    renderedMediaPlayer?.pause()
                    unmasteredMediaPlayer?.let { player ->
                        player.seekTo(currentPos)
                        player.setVolume(1.0f, 1.0f)
                        if (!player.isPlaying) player.start()
                        _audioState.update {
                            it.copy(
                                isUnmasteredPlaying = true,
                                isRenderedPlaying = false,
                                playbackState = PlaybackEngineState.MEMUTAR
                            )
                        }
                    }
                }
                com.autoremix.djslow.engine.preview.AbPreviewController.AbMode.MASTERED -> {
                    stopVocalInternal()
                    stopBeatInternal()
                    unmasteredMediaPlayer?.pause()
                    renderedMediaPlayer?.let { player ->
                        val vol = gainFactor.coerceIn(0.1f, 1.0f)
                        player.setVolume(vol, vol)
                        player.seekTo(currentPos)
                        if (!player.isPlaying) player.start()
                        _audioState.update {
                            it.copy(
                                isRenderedPlaying = true,
                                isUnmasteredPlaying = false,
                                playbackState = PlaybackEngineState.MEMUTAR
                            )
                        }
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Memutar Section Preview (15-30 detik) dari posisi tertentu, lalu berhenti otomatis.
     */
    fun playSectionPreview(startMs: Long, durationMs: Long): Result<Unit> {
        sectionLimitJob?.cancel()
        return try {
            val player = renderedMediaPlayer ?: unmasteredMediaPlayer
                ?: return Result.failure(IllegalStateException("Belum ada audio yang dirender untuk preview."))

            stopVocalInternal()
            stopBeatInternal()
            player.seekTo(startMs.toInt())
            player.start()

            _audioState.update {
                it.copy(
                    isRenderedPlaying = true,
                    isRenderedPaused = false,
                    isPreviewingSection = true
                )
            }

            sectionLimitJob = scope.launch(Dispatchers.Main) {
                delay(durationMs)
                if (isActive) {
                    player.pause()
                    _audioState.update {
                        it.copy(
                            isRenderedPlaying = false,
                            isRenderedPaused = true,
                            isPreviewingSection = false
                        )
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun startPositionTicker() {
        positionTickerJob?.cancel()
        positionTickerJob = scope.launch {
            while (isActive) {
                try {
                    val vocalPos = vocalMediaPlayer?.takeIf { it.isPlaying }?.currentPosition?.toLong()
                    val beatPos = beatMediaPlayer?.takeIf { it.isPlaying }?.currentPosition?.toLong()
                    val rendPos = renderedMediaPlayer?.takeIf { it.isPlaying }?.currentPosition?.toLong()
                    val unmasterPos = unmasteredMediaPlayer?.takeIf { it.isPlaying }?.currentPosition?.toLong()

                    _audioState.update { cur ->
                        cur.copy(
                            vocalPositionMs = vocalPos ?: cur.vocalPositionMs,
                            beatPositionMs = beatPos ?: cur.beatPositionMs,
                            renderedPositionMs = rendPos ?: unmasterPos ?: cur.renderedPositionMs
                        )
                    }
                } catch (_: Exception) {}
                delay(100)
            }
        }
    }

    fun release() {
        positionTickerJob?.cancel()
        sectionLimitJob?.cancel()
        try { vocalMediaPlayer?.release() } catch (_: Exception) {}
        try { beatMediaPlayer?.release() } catch (_: Exception) {}
        try { renderedMediaPlayer?.release() } catch (_: Exception) {}
        try { unmasteredMediaPlayer?.release() } catch (_: Exception) {}
        vocalMediaPlayer = null
        beatMediaPlayer = null
        renderedMediaPlayer = null
        unmasteredMediaPlayer = null
    }
}
