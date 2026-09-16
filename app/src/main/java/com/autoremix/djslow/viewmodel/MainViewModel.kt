package com.autoremix.djslow.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autoremix.djslow.engine.AudioDecoder
import com.autoremix.djslow.engine.AudioPlayer
import com.autoremix.djslow.engine.AudioSource
import com.autoremix.djslow.engine.analysis.AutoEnergyAnalyzer
import com.autoremix.djslow.engine.analysis.BeatContentAnalyzer.BeatContentAnalysis
import com.autoremix.djslow.engine.analysis.BpmDetector
import com.autoremix.djslow.engine.arrangement.AutoArranger
import com.autoremix.djslow.engine.arrangement.AutoDjPreset
import com.autoremix.djslow.engine.mix.AudioMixPipeline
import com.autoremix.djslow.engine.mix.MixEngine
import com.autoremix.djslow.engine.mix.MixTrackSettings
import com.autoremix.djslow.engine.music.Chord
import com.autoremix.djslow.engine.music.ChordEngine
import com.autoremix.djslow.engine.music.ChordType
import com.autoremix.djslow.engine.music.MusicKey
import com.autoremix.djslow.engine.music.MusicMode
import com.autoremix.djslow.engine.music.PitchClass
import com.autoremix.djslow.engine.structure.EnergyCurve
import com.autoremix.djslow.engine.structure.SongSection
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
import kotlin.random.Random

class MainViewModel : ViewModel() {

    private val audioPlayer = AudioPlayer(viewModelScope)

    private val _vocalSource = MutableStateFlow<AudioSource?>(null)
    private val _beatSource = MutableStateFlow<AudioSource?>(null)
    private val _statusMessage = MutableStateFlow("TAHAP 4 — ARRANGEMENT: Silakan pilih vokal & beat, lalu klik Analisis & Aransemen.")
    private val _errorMessage = MutableStateFlow<String?>(null)

    // Parameter Mixing Multi-Trek
    private val _vocalMixSettings = MutableStateFlow(MixTrackSettings(volume = 1.0f))
    private val _beatMixSettings = MutableStateFlow(MixTrackSettings(volume = 0.8f))
    private val _drumMixSettings = MutableStateFlow(MixTrackSettings(volume = 0.85f))
    private val _bassMixSettings = MutableStateFlow(MixTrackSettings(volume = 0.85f))
    private val _chordMixSettings = MutableStateFlow(MixTrackSettings(volume = 0.70f))
    private val _melodyMixSettings = MutableStateFlow(MixTrackSettings(volume = 0.80f))
    private val _padMixSettings = MutableStateFlow(MixTrackSettings(volume = 0.75f))
    private val _masterGain = MutableStateFlow(0.90f)
    private val _isAutoMixEnabled = MutableStateFlow(true)

    // Parameter Musik & Harmoni
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

    // Parameter Aransemen Tahap 4
    private val _songSections = MutableStateFlow<List<SongSection>>(emptyList())
    private val _currentPreset = MutableStateFlow(AutoDjPreset.DJ_SLOW)
    private val _melodySeed = MutableStateFlow(42L)
    private val _beatAnalysis = MutableStateFlow<BeatContentAnalysis?>(null)
    private val _energyAnalysis = MutableStateFlow<AutoEnergyAnalyzer.EnergyAnalysisResult?>(null)
    private val _energyCurve = MutableStateFlow<EnergyCurve?>(null)

    // Status Analisis
    private val _isAnalyzing = MutableStateFlow(false)
    private val _analysisProgressFraction = MutableStateFlow(0.0f)
    private val _analysisMessage = MutableStateFlow("")

    // Status Rendering WAV Latar Belakang
    private val _isRendering = MutableStateFlow(false)
    private val _renderProgressFraction = MutableStateFlow(0.0f)
    private val _renderStageText = MutableStateFlow("")
    private val _renderedWavFile = MutableStateFlow<File?>(null)
    private val _validationResult = MutableStateFlow<WavValidator.ValidationResult?>(null)

    private val group1 = combine(_vocalSource, _beatSource, audioPlayer.audioState, _statusMessage, _errorMessage) { vocal, beat, audioState, status, error ->
        Tuple5(vocal, beat, audioState, status, error)
    }
    private val group2 = combine(_vocalMixSettings, _beatMixSettings, _drumMixSettings, _bassMixSettings, _chordMixSettings) { vMix, bMix, drMix, bassMix, cMix ->
        Tuple5(vMix, bMix, drMix, bassMix, cMix)
    }
    private val group3 = combine(_melodyMixSettings, _padMixSettings, _masterGain, _isAutoMixEnabled, _targetBpm) { melMix, padMix, mGain, autoMix, tBpm ->
        Tuple5(melMix, padMix, mGain, autoMix, tBpm)
    }
    private val group4 = combine(_vocalBpm, _beatBpm, _isBpmEstimated, _bpmConfidence, _detectedKey) { vBpm, bBpm, isBpmEst, bpmConf, key ->
        Tuple5(vBpm, bBpm, isBpmEst, bpmConf, key)
    }
    private val group5 = combine(_isKeyEstimated, _keyConfidence, _chordProgressionSummary, _chordPreset, _bassPattern) { isKeyEst, keyConf, chords, cPreset, bPattern ->
        Tuple5(isKeyEst, keyConf, chords, cPreset, bPattern)
    }
    private val group6 = combine(_songSections, _currentPreset, _melodySeed, _energyCurve, _masterTimeline) { sections, preset, seed, curve, timeline ->
        Tuple5(sections, preset, seed, curve, timeline)
    }

    private val group123 = combine(group1, group2, group3) { g1, g2, g3 -> Triple(g1, g2, g3) }
    private val group456 = combine(group4, group5, group6) { g4, g5, g6 -> Triple(g4, g5, g6) }

    val uiState: StateFlow<UiState> = combine(group123, group456) { (g1, g2, g3), (g4, g5, g6) ->
        val rend = _isRendering.value
        val prog = _renderProgressFraction.value
        val stage = _renderStageText.value
        val file = _renderedWavFile.value
        val valid = _validationResult.value

        val isAna = _isAnalyzing.value
        val anaProg = _analysisProgressFraction.value
        val anaMsg = _analysisMessage.value

        UiState(
            stageTitle = "TAHAP 4 — ARRANGEMENT (DRUM + MELODY + PAD + DJ STRUCTURE)",
            vocalSource = g1.a,
            beatSource = g1.b,
            audioState = g1.c,
            statusMessage = g1.d,
            errorMessage = g1.e ?: g1.c.errorMessage,
            vocalMixSettings = g2.a,
            beatMixSettings = g2.b,
            drumMixSettings = g2.c,
            bassMixSettings = g2.d,
            chordMixSettings = g2.e,
            melodyMixSettings = g3.a,
            padMixSettings = g3.b,
            masterGain = g3.c,
            isAutoMixEnabled = g3.d,
            targetBpm = g3.e,
            vocalBpm = g4.a,
            beatBpm = g4.b,
            isBpmEstimated = g4.c,
            bpmConfidence = g4.d,
            detectedKey = g4.e,
            isKeyEstimated = g5.a,
            keyConfidence = g5.b,
            chordProgressionSummary = g5.c,
            chordPreset = g5.d,
            bassPattern = g5.e,
            songSections = g6.a,
            currentPreset = g6.b,
            melodySeed = g6.c,
            energyCurve = g6.d,
            masterTimeline = g6.e,
            beatAnalysis = _beatAnalysis.value,
            energyAnalysis = _energyAnalysis.value,
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

    // ---- Kontrol Mixing Multi-Trek (Tahap 2, 3, 4) ----

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

    fun onDrumMixVolumeChange(volume: Float) {
        _drumMixSettings.value = _drumMixSettings.value.copy(volume = volume.coerceIn(0.0f, 1.5f))
    }

    fun onDrumMuteToggle() {
        val current = _drumMixSettings.value
        _drumMixSettings.value = current.copy(isMuted = !current.isMuted)
    }

    fun onDrumSoloToggle() {
        val current = _drumMixSettings.value
        _drumMixSettings.value = current.copy(isSolo = !current.isSolo)
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

    fun onMelodyMixVolumeChange(volume: Float) {
        _melodyMixSettings.value = _melodyMixSettings.value.copy(volume = volume.coerceIn(0.0f, 1.5f))
    }

    fun onMelodyMuteToggle() {
        val current = _melodyMixSettings.value
        _melodyMixSettings.value = current.copy(isMuted = !current.isMuted)
    }

    fun onMelodySoloToggle() {
        val current = _melodyMixSettings.value
        _melodyMixSettings.value = current.copy(isSolo = !current.isSolo)
    }

    fun onPadMixVolumeChange(volume: Float) {
        _padMixSettings.value = _padMixSettings.value.copy(volume = volume.coerceIn(0.0f, 1.5f))
    }

    fun onPadMuteToggle() {
        val current = _padMixSettings.value
        _padMixSettings.value = current.copy(isMuted = !current.isMuted)
    }

    fun onPadSoloToggle() {
        val current = _padMixSettings.value
        _padMixSettings.value = current.copy(isSolo = !current.isSolo)
    }

    fun onMasterGainChange(gain: Float) {
        _masterGain.value = gain.coerceIn(0.0f, 1.5f)
    }

    fun onAutoMixToggle() {
        _isAutoMixEnabled.value = !_isAutoMixEnabled.value
    }

    // ---- Kontrol Aransemen Tahap 4 ----

    fun onPresetChanged(preset: AutoDjPreset) {
        _currentPreset.value = preset
        _targetBpm.value = preset.defaultBpm
        rebuildMasterTimeline(preset.defaultBpm, _detectedKey.value)
        _statusMessage.value = "Preset diubah ke ${preset.label} (${preset.defaultBpm.roundToInt()} BPM)."
    }

    fun onRegenerateMelodySeed() {
        val newSeed = Random.nextLong(1, 999999)
        _melodySeed.value = newSeed
        _statusMessage.value = "Melodi baru dibuat (Seed #$newSeed). Karakter melodi disegarkan."
    }

    // ---- Analisis Musik & Struktur Lagu (Tahap 3 & 4) ----

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
        _analysisMessage.value = "Memulai analisis audio & deteksi struktur..."
        _errorMessage.value = null
        _statusMessage.value = "Menganalisis BPM, Tangga Nada, Energi & Seksi Lagu..."

        viewModelScope.launch {
            val result = AudioMixPipeline.analyzeAudio(
                context = context,
                vocalUri = vocal?.uri,
                beatUri = beat?.uri,
                manualBpm = null,
                manualKey = null,
                preset = _currentPreset.value,
                melodySeed = _melodySeed.value,
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

                analysis.arrangementPlan?.let { plan ->
                    _songSections.value = plan.sections
                    _energyCurve.value = plan.energyCurve
                    _beatAnalysis.value = plan.beatAnalysis
                    _energyAnalysis.value = plan.energyAnalysis
                }

                _statusMessage.value = "Analisis selesai: ${analysis.targetBpm.roundToInt()} BPM, ${analysis.key.displayName}, ${_songSections.value.size} Seksi Lagu terdeteksi."
            }.onFailure { ex ->
                _errorMessage.value = "Analisis audio gagal: ${ex.message}"
                _statusMessage.value = "Analisis gagal. Silakan coba lagi."
            }
        }
    }

    // Kontrol BPM Manual

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

    // Kontrol Tangga Nada (Key) Manual

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

    // ---- Render WAV Pipeline Latar Belakang (Tahap 4) ----

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
        _renderStageText.value = "Mempersiapkan pipeline aransemen..."
        _errorMessage.value = null
        _statusMessage.value = "Memulai proses rendering aransemen lengkap..."

        val params = MixEngine.MixParams(
            vocalSettings = _vocalMixSettings.value,
            beatSettings = _beatMixSettings.value,
            drumSettings = _drumMixSettings.value,
            bassSettings = _bassMixSettings.value,
            chordSettings = _chordMixSettings.value,
            melodySettings = _melodyMixSettings.value,
            padSettings = _padMixSettings.value,
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
                preset = _currentPreset.value,
                melodySeed = _melodySeed.value,
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
                pipelineResult.arrangementPlan?.let { plan ->
                    _songSections.value = plan.sections
                    _energyCurve.value = plan.energyCurve
                }
                _statusMessage.value = "ARANSEMEN WAV LENGKAP BERHASIL DIBUAT & TERVALIDASI (${pipelineResult.durationMs / 1000} detik)."

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
