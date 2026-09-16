package com.autoremix.djslow.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoremix.djslow.engine.AudioDecoder
import com.autoremix.djslow.engine.AudioPlayer
import com.autoremix.djslow.engine.AudioSource
import com.autoremix.djslow.engine.analysis.BpmDetector
import com.autoremix.djslow.engine.mix.AudioMixPipeline
import com.autoremix.djslow.engine.mix.MixEngine
import com.autoremix.djslow.engine.mix.MixTrackSettings
import com.autoremix.djslow.engine.music.Chord
import com.autoremix.djslow.engine.music.ChordEngine
import com.autoremix.djslow.engine.music.ChordType
import com.autoremix.djslow.engine.music.MusicKey
import com.autoremix.djslow.engine.music.MusicMode
import com.autoremix.djslow.engine.music.PitchClass
import com.autoremix.djslow.engine.synth.BassEngine
import com.autoremix.djslow.engine.synth.BassPatternType
import com.autoremix.djslow.engine.synth.ChordSynthPreset
import com.autoremix.djslow.engine.timeline.MasterTimeline
import com.autoremix.djslow.engine.wav.WavValidator
import com.autoremix.djslow.state.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

class MainViewModel : ViewModel() {

    private val audioPlayer = AudioPlayer(viewModelScope)

    private val _vocalSource = MutableStateFlow<AudioSource?>(null)
    private val _beatSource = MutableStateFlow<AudioSource?>(null)
    private val _statusMessage = MutableStateFlow("TAHAP 3 — MUSIK: Silakan pilih vokal & beat, lalu klik Analisis.")
    private val _errorMessage = MutableStateFlow<String?>(null)

    // Parameter Mixing 4-Trek
    private val _vocalMixSettings = MutableStateFlow(MixTrackSettings(volume = 1.0f))
    private val _beatMixSettings = MutableStateFlow(MixTrackSettings(volume = 0.8f))
    private val _chordMixSettings = MutableStateFlow(MixTrackSettings(volume = 0.70f))
    private val _bassMixSettings = MutableStateFlow(MixTrackSettings(volume = 0.85f))
    private val _masterGain = MutableStateFlow(0.90f)
    private val _isAutoMixEnabled = MutableStateFlow(true)

    // Parameter Mesin Musik Tahap 3
    private val _vocalBpm = MutableStateFlow<BpmDetector.BpmResult?>(null)
    private val _beatBpm = MutableStateFlow<BpmDetector.BpmResult?>(null)
    private val _targetBpm = MutableStateFlow(80.0f)
    private val _isBpmEstimated = MutableStateFlow(true)
    private val _bpmConfidence = MutableStateFlow(0.5f)

    private val _detectedKey = MutableStateFlow(MusicKey(PitchClass.A, MusicMode.MINOR))
    private val _isKeyEstimated = MutableStateFlow(true)
    private val _keyConfidence = MutableStateFlow(0.5f)

    private val _chordProgressionSummary = MutableStateFlow<List<Chord>>(
        listOf(
            Chord(PitchClass.A, ChordType.MINOR),
            Chord(PitchClass.F, ChordType.MAJOR),
            Chord(PitchClass.C, ChordType.MAJOR),
            Chord(PitchClass.G, ChordType.MAJOR)
        )
    )
    private val _isChordEstimated = MutableStateFlow(true)
    private val _chordConfidence = MutableStateFlow(0.5f)
    private val _chordPreset = MutableStateFlow(ChordSynthPreset.SOFT_PIANO)
    private val _bassPattern = MutableStateFlow(BassPatternType.DJ_SLOW_BASS)

    private val _masterTimeline = MutableStateFlow<MasterTimeline?>(null)

    // Status Analisis Musik
    private val _isAnalyzing = MutableStateFlow(false)
    private val _analysisProgressFraction = MutableStateFlow(0.0f)
    private val _analysisMessage = MutableStateFlow("")

    // Status Rendering WAV Latar Belakang
    private val _isRendering = MutableStateFlow(false)
    private val _renderProgressFraction = MutableStateFlow(0.0f)
    private val _renderStageText = MutableStateFlow("")
    private val _renderedWavFile = MutableStateFlow<File?>(null)
    private val _validationResult = MutableStateFlow<WavValidator.ValidationResult?>(null)

    val uiState: StateFlow<UiState> = combine(
        combine(_vocalSource, _beatSource, audioPlayer.audioState, _statusMessage, _errorMessage) { vocal, beat, audioState, status, error ->
            Tuple5(vocal, beat, audioState, status, error)
        },
        combine(_vocalMixSettings, _beatMixSettings, _chordMixSettings, _bassMixSettings, _masterGain) { vMix, bMix, cMix, bassMix, mGain ->
            Tuple5(vMix, bMix, cMix, bassMix, mGain)
        },
        combine(_isAutoMixEnabled, _vocalBpm, _beatBpm, _targetBpm, _isBpmEstimated) { autoMix, vBpm, bBpm, tBpm, isBpmEst ->
            Tuple5(autoMix, vBpm, bBpm, tBpm, isBpmEst)
        },
        combine(_bpmConfidence, _detectedKey, _isKeyEstimated, _keyConfidence, _chordProgressionSummary) { bpmConf, key, isKeyEst, keyConf, chords ->
            Tuple5(bpmConf, key, isKeyEst, keyConf, chords)
        },
        combine(
            _isChordEstimated,
            _chordConfidence,
            _chordPreset,
            _bassPattern,
            _masterTimeline
        ) { isChordEst, chordConf, preset, pattern, timeline ->
            Tuple5(isChordEst, chordConf, preset, pattern, timeline)
        }
    ) { group1, group2, group3, group4, group5 ->
        val rend = _isRendering.value
        val prog = _renderProgressFraction.value
        val stage = _renderStageText.value
        val file = _renderedWavFile.value
        val valid = _validationResult.value

        val isAna = _isAnalyzing.value
        val anaProg = _analysisProgressFraction.value
        val anaMsg = _analysisMessage.value

        UiState(
            stageTitle = "TAHAP 3 — MUSIK (BPM + KEY + CHORD + BASS)",
            vocalSource = group1.a,
            beatSource = group1.b,
            audioState = group1.c,
            statusMessage = group1.d,
            errorMessage = group1.e ?: group1.c.errorMessage,
            vocalMixSettings = group2.a,
            beatMixSettings = group2.b,
            chordMixSettings = group2.c,
            bassMixSettings = group2.d,
            masterGain = group2.e,
            isAutoMixEnabled = group3.a,
            vocalBpm = group3.b,
            beatBpm = group3.c,
            targetBpm = group3.d,
            isBpmEstimated = group3.e,
            bpmConfidence = group4.a,
            detectedKey = group4.b,
            isKeyEstimated = group4.c,
            keyConfidence = group4.d,
            chordProgressionSummary = group4.e,
            isChordEstimated = group5.a,
            chordConfidence = group5.b,
            chordPreset = group5.c,
            bassPattern = group5.d,
            masterTimeline = group5.e,
            isAnalyzing = isAna,
            analysisProgressFraction = anaProg,
            analysisMessage = anaMsg,
            isRendering = rend,
            renderProgressFraction = prog,
            renderStageText = stage,
            renderedWavFile = file,
            validationResult = valid
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UiState()
    )

    init {
        // Buat timeline awal dengan nilai default 80 BPM
        rebuildMasterTimeline(80.0f, _detectedKey.value)
    }

    // ---- Pemilihan Berkas (Tahap 1) ----

    fun onVocalSelected(context: Context, uri: Uri) {
        _errorMessage.value = null
        _statusMessage.value = "Membaca file vokal..."

        viewModelScope.launch {
            val decodeResult = AudioDecoder.decode(context, uri)
            decodeResult.onSuccess { source ->
                _vocalSource.value = source
                val loadResult = audioPlayer.setVocalSource(context, source)
                if (loadResult.isSuccess) {
                    _statusMessage.value = "Vokal dipilih: ${source.fileName} (${source.formattedDuration})"
                } else {
                    _errorMessage.value = "Audio tidak dapat diputar."
                }
            }.onFailure { ex ->
                _errorMessage.value = ex.localizedMessage ?: "File audio tidak dapat dibaca."
                _statusMessage.value = "Gagal memuat vokal."
            }
        }
    }

    fun onBeatSelected(context: Context, uri: Uri) {
        _errorMessage.value = null
        _statusMessage.value = "Membaca file beat..."

        viewModelScope.launch {
            val decodeResult = AudioDecoder.decode(context, uri)
            decodeResult.onSuccess { source ->
                _beatSource.value = source
                val loadResult = audioPlayer.setBeatSource(context, source)
                if (loadResult.isSuccess) {
                    _statusMessage.value = "Beat dipilih: ${source.fileName} (${source.formattedDuration})"
                } else {
                    _errorMessage.value = "Audio tidak dapat diputar."
                }
            }.onFailure { ex ->
                _errorMessage.value = ex.localizedMessage ?: "File audio tidak dapat dibaca."
                _statusMessage.value = "Gagal memuat beat."
            }
        }
    }

    // ---- Pemutaran Pratinjau (Tahap 1) ----

    fun onPlay() {
        _errorMessage.value = null
        val currentUi = uiState.value
        if (!currentUi.hasAnyAudio) {
            _errorMessage.value = "File tidak tersedia. Silakan pilih vokal atau beat terlebih dahulu."
            return
        }
        audioPlayer.play()
        _statusMessage.value = "MEMUTAR audio nyata..."
    }

    fun onPause() {
        audioPlayer.pause()
        _statusMessage.value = "DIJEDA."
    }

    fun onStop() {
        audioPlayer.stop()
        _statusMessage.value = "BERHENTI."
    }

    fun onSeek(positionFraction: Float) {
        audioPlayer.seekTo(positionFraction)
    }

    fun onVocalSeek(positionMs: Long) {
        audioPlayer.seekVocalTo(positionMs)
    }

    fun onBeatSeek(positionMs: Long) {
        audioPlayer.seekBeatTo(positionMs)
    }

    // ---- Kontrol Mixing (Tahap 2 & 3) ----

    fun onVocalVolumeChange(volume: Float) {
        audioPlayer.setVocalVolume(volume)
        _vocalMixSettings.value = _vocalMixSettings.value.copy(volume = volume)
    }

    fun onBeatVolumeChange(volume: Float) {
        audioPlayer.setBeatVolume(volume)
        _beatMixSettings.value = _beatMixSettings.value.copy(volume = volume)
    }

    fun onVocalMixVolumeChange(volume: Float) {
        val clamped = volume.coerceIn(0.0f, 1.5f)
        _vocalMixSettings.value = _vocalMixSettings.value.copy(volume = clamped)
        audioPlayer.setVocalVolume(clamped.coerceIn(0f, 1f))
    }

    fun onVocalMuteToggle() {
        val current = _vocalMixSettings.value
        _vocalMixSettings.value = current.copy(isMuted = !current.isMuted)
    }

    fun onVocalSoloToggle() {
        val current = _vocalMixSettings.value
        _vocalMixSettings.value = current.copy(isSolo = !current.isSolo)
    }

    fun onBeatMixVolumeChange(volume: Float) {
        val clamped = volume.coerceIn(0.0f, 1.5f)
        _beatMixSettings.value = _beatMixSettings.value.copy(volume = clamped)
        audioPlayer.setBeatVolume(clamped.coerceIn(0f, 1f))
    }

    fun onBeatMuteToggle() {
        val current = _beatMixSettings.value
        _beatMixSettings.value = current.copy(isMuted = !current.isMuted)
    }

    fun onBeatSoloToggle() {
        val current = _beatMixSettings.value
        _beatMixSettings.value = current.copy(isSolo = !current.isSolo)
    }

    fun onChordMixVolumeChange(volume: Float) {
        _chordMixSettings.value = _chordMixSettings.value.copy(volume = volume.coerceIn(0.0f, 1.5f))
    }

    fun onChordMuteToggle() {
        val current = _chordMixSettings.value
        _chordMixSettings.value = current.copy(isMuted = !current.isMuted)
    }

    fun onChordSoloToggle() {
        val current = _chordMixSettings.value
        _chordMixSettings.value = current.copy(isSolo = !current.isSolo)
    }

    fun onBassMixVolumeChange(volume: Float) {
        _bassMixSettings.value = _bassMixSettings.value.copy(volume = volume.coerceIn(0.0f, 1.5f))
    }

    fun onBassMuteToggle() {
        val current = _bassMixSettings.value
        _bassMixSettings.value = current.copy(isMuted = !current.isMuted)
    }

    fun onBassSoloToggle() {
        val current = _bassMixSettings.value
        _bassMixSettings.value = current.copy(isSolo = !current.isSolo)
    }

    fun onMasterGainChange(gain: Float) {
        _masterGain.value = gain.coerceIn(0.0f, 1.5f)
    }

    fun onAutoMixToggle() {
        _isAutoMixEnabled.value = !_isAutoMixEnabled.value
    }

    // ---- Mesin Musik & Analisis (Tahap 3) ----

    fun onAnalyzeTracks(context: Context) {
        val vocal = _vocalSource.value
        val beat = _beatSource.value

        if (vocal == null && beat == null) {
            _errorMessage.value = "Pilih file vokal atau beat sebelum melakukan analisis."
            return
        }

        if (_isAnalyzing.value) return

        _isAnalyzing.value = true
        _analysisProgressFraction.value = 0.05f
        _analysisMessage.value = "Memulai analisis audio DSP..."
        _errorMessage.value = null
        _statusMessage.value = "Menganalisis BPM, Tangga Nada & Progresi Akor..."

        viewModelScope.launch {
            val result = AudioMixPipeline.analyzeAudio(
                context = context,
                vocalUri = vocal?.uri,
                beatUri = beat?.uri,
                onProgress = { progress, message ->
                    _analysisProgressFraction.value = progress
                    _analysisMessage.value = message
                }
            )

            _isAnalyzing.value = false

            result.onSuccess { analysis ->
                _vocalBpm.value = analysis.vocalBpm
                _beatBpm.value = analysis.beatBpm
                _targetBpm.value = analysis.targetBpm
                _isBpmEstimated.value = analysis.isBpmEstimated
                _bpmConfidence.value = analysis.bpmConfidence

                _detectedKey.value = analysis.key
                _isKeyEstimated.value = analysis.isKeyEstimated
                _keyConfidence.value = analysis.keyConfidence

                _chordProgressionSummary.value = analysis.chordProgression.chordProgressionSummary
                _isChordEstimated.value = analysis.chordProgression.isEstimated
                _chordConfidence.value = analysis.chordProgression.confidence

                _masterTimeline.value = analysis.timeline

                _statusMessage.value = "Analisis selesai: Target ${analysis.targetBpm.roundToInt()} BPM, ${analysis.key.displayName}, Akor: ${analysis.chordProgression.displayProgression}"
            }.onFailure { ex ->
                _errorMessage.value = "Analisis audio gagal: ${ex.message}"
                _statusMessage.value = "Analisis gagal. Silakan coba lagi."
            }
        }
    }

    // Kontrol BPM Manual (Section 3: WAJIB rebuild Master Timeline, Beat Grid, Bar, Chord timing, Bass timing)

    fun onBpmIncrement() {
        val next = (_targetBpm.value + 1.0f).coerceIn(60.0f, 140.0f)
        updateBpmAndRebuild(next)
    }

    fun onBpmDecrement() {
        val prev = (_targetBpm.value - 1.0f).coerceIn(60.0f, 140.0f)
        updateBpmAndRebuild(prev)
    }

    fun onBpmPresetSelected(preset: Int) {
        updateBpmAndRebuild(preset.toFloat())
    }

    fun onBpmManualChange(bpm: Float) {
        val clamped = bpm.coerceIn(60.0f, 140.0f)
        updateBpmAndRebuild(clamped)
    }

    private fun updateBpmAndRebuild(newBpm: Float) {
        _targetBpm.value = newBpm
        _isBpmEstimated.value = false
        _bpmConfidence.value = 1.0f
        rebuildMasterTimeline(newBpm, _detectedKey.value)
        _statusMessage.value = "BPM disesuaikan ke ${newBpm.roundToInt()} BPM. Timeline & ritme diperbarui."
    }

    // Kontrol Key Manual (Section 5: WAJIB rebuild Chord, Bass, Generated Music)

    fun onKeySelected(pitchClass: PitchClass, mode: MusicMode) {
        val newKey = MusicKey(pitchClass, mode, 1.0f)
        _detectedKey.value = newKey
        _isKeyEstimated.value = false
        _keyConfidence.value = 1.0f

        val progression = ChordEngine.getDefaultProgressionForKey(newKey)
        _chordProgressionSummary.value = progression

        rebuildMasterTimeline(_targetBpm.value, newKey)
        _statusMessage.value = "Tangga nada diatur ke ${newKey.displayName}. Akor dan bass diselaraskan."
    }

    fun onChordPresetChanged(preset: ChordSynthPreset) {
        _chordPreset.value = preset
        _statusMessage.value = "Instrumen Akor diubah ke ${preset.label}."
    }

    fun onBassPatternChanged(pattern: BassPatternType) {
        _bassPattern.value = pattern
        val currentTimeline = _masterTimeline.value ?: return
        val updatedEvents = BassEngine.generateBassEvents(currentTimeline, _detectedKey.value, pattern)
        _masterTimeline.value = currentTimeline.copy(bassEvents = updatedEvents)
        _statusMessage.value = "Pola bass diubah ke ${pattern.label}."
    }

    private fun rebuildMasterTimeline(bpm: Float, key: MusicKey) {
        val durationMs = maxOf(
            _vocalSource.value?.durationMs ?: 0L,
            _beatSource.value?.durationMs ?: 0L,
            180000L
        )
        var timeline = MasterTimeline.build(bpm = bpm, totalDurationMs = durationMs)

        val progression = ChordEngine.buildChordProgression(timeline, key, null)
        timeline = timeline.copy(chordEvents = progression.chordEvents)

        val bassEvents = BassEngine.generateBassEvents(timeline, key, _bassPattern.value)
        timeline = timeline.copy(bassEvents = bassEvents)

        _masterTimeline.value = timeline
    }

    // ---- Render WAV Pipeline Latar Belakang (Tahap 2 & 3) ----

    fun startMixAndRender(context: Context) {
        val vocal = _vocalSource.value
        val beat = _beatSource.value

        if (vocal == null && beat == null) {
            _errorMessage.value = "Silakan pilih berkas vokal atau beat terlebih dahulu."
            return
        }

        if (_isRendering.value) return

        _isRendering.value = true
        _renderProgressFraction.value = 0.0f
        _renderStageText.value = "Mempersiapkan pipeline..."
        _errorMessage.value = null
        _statusMessage.value = "Memulai proses rendering WAV 4-trek (Vokal, Beat, Chord, Bass)..."

        val params = MixEngine.MixParams(
            vocalSettings = _vocalMixSettings.value,
            beatSettings = _beatMixSettings.value,
            chordSettings = _chordMixSettings.value,
            bassSettings = _bassMixSettings.value,
            masterGain = _masterGain.value,
            isAutoMixEnabled = _isAutoMixEnabled.value
        )

        viewModelScope.launch {
            val result = AudioMixPipeline.run(
                context = context,
                vocalUri = vocal?.uri,
                beatUri = beat?.uri,
                targetBpm = _targetBpm.value,
                musicKey = _detectedKey.value,
                chordPreset = _chordPreset.value,
                bassPattern = _bassPattern.value,
                params = params
            ) { step, progressFraction, message ->
                _renderProgressFraction.value = progressFraction
                _renderStageText.value = message
                _statusMessage.value = "${step.label}: $message"
            }

            _isRendering.value = false

            result.onSuccess { pipelineResult ->
                _renderedWavFile.value = pipelineResult.wavFile
                _validationResult.value = pipelineResult.validation
                _masterTimeline.value = pipelineResult.timeline
                _statusMessage.value = "WAV 4-TREK BERHASIL DIBUAT & TERVALIDASI (${pipelineResult.durationMs / 1000} detik)."

                // Muat ke audio player hasil render
                val loadWav = audioPlayer.setRenderedWav(pipelineResult.wavFile)
                if (loadWav.isFailure) {
                    _errorMessage.value = "Rendering berhasil tetapi berkas gagal dimuat ke pemutar."
                }
            }.onFailure { ex ->
                _renderedWavFile.value = null
                _validationResult.value = null
                _errorMessage.value = ex.message ?: "Rendering gagal. Silakan ulangi."
                _statusMessage.value = "Rendering gagal. Silakan ulangi."
            }
        }
    }

    // ---- Pemutaran Hasil Render WAV Nyata ----

    fun onPlayRendered() {
        _errorMessage.value = null
        val res = audioPlayer.playRendered()
        if (res.isSuccess) {
            _statusMessage.value = "MEMUTAR HASIL WAV NYATA..."
        }
    }

    fun onPauseRendered() {
        audioPlayer.pauseRendered()
        _statusMessage.value = "HASIL DIJEDA."
    }

    fun onStopRendered() {
        audioPlayer.stopRendered()
        _statusMessage.value = "HASIL BERHENTI."
    }

    fun onSeekRendered(fraction: Float) {
        audioPlayer.seekRendered(fraction)
    }

    fun clearError() {
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        audioPlayer.release()
    }

    private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)
}
