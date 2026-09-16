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
import com.autoremix.djslow.engine.dsp.LoudnessMeter
import com.autoremix.djslow.engine.mastering.MasteringPreset
import com.autoremix.djslow.engine.mix.AudioMixPipeline
import com.autoremix.djslow.engine.mix.BusSettings
import com.autoremix.djslow.engine.mix.BusType
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt
import kotlin.random.Random

class MainViewModel : ViewModel() {

    private val audioPlayer = AudioPlayer(viewModelScope)

    private val _vocalSource = MutableStateFlow<AudioSource?>(null)
    private val _beatSource = MutableStateFlow<AudioSource?>(null)
    private val _statusMessage = MutableStateFlow("TAHAP 5 — KUALITAS AUDIO & MASTERING: Siap meracik audio studio.")
    private val _errorMessage = MutableStateFlow<String?>(null)

    // Parameter Mixer Multi-Track
    private val _vocalMixSettings = MutableStateFlow(MixTrackSettings(volume = 1.0f))
    private val _beatMixSettings = MutableStateFlow(MixTrackSettings(volume = 0.8f))
    private val _drumMixSettings = MutableStateFlow(MixTrackSettings(volume = 0.85f))
    private val _bassMixSettings = MutableStateFlow(MixTrackSettings(volume = 0.85f))
    private val _chordMixSettings = MutableStateFlow(MixTrackSettings(volume = 0.70f))
    private val _melodyMixSettings = MutableStateFlow(MixTrackSettings(volume = 0.80f))
    private val _padMixSettings = MutableStateFlow(MixTrackSettings(volume = 0.75f))
    private val _fxMixSettings = MutableStateFlow(MixTrackSettings(volume = 0.80f))

    // Mix Bus (Tahap 5)
    private val _vocalBusSettings = MutableStateFlow(BusSettings(BusType.VOCAL_BUS, volume = 1.0f))
    private val _beatBusSettings = MutableStateFlow(BusSettings(BusType.BEAT_BUS, volume = 1.0f))
    private val _drumBusSettings = MutableStateFlow(BusSettings(BusType.DRUM_BUS, volume = 1.0f))
    private val _bassBusSettings = MutableStateFlow(BusSettings(BusType.BASS_BUS, volume = 1.0f))
    private val _musicBusSettings = MutableStateFlow(BusSettings(BusType.MUSIC_BUS, volume = 1.0f))

    private val _masterGain = MutableStateFlow(0.90f)
    private val _isAutoMixEnabled = MutableStateFlow(true)

    // Parameter Auto Mastering Studio (Tahap 5)
    private val _masteringPreset = MutableStateFlow(MasteringPreset.DJ_SLOW)
    private val _loudnessReport = MutableStateFlow<LoudnessMeter.LoudnessReport?>(null)

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

    // Status Ekspor MediaStore, MP3, A/B Preview & Proyek (Tahap 6 Final)
    private val _exportedWavFile = MutableStateFlow<File?>(null)
    private val _exportedMp3File = MutableStateFlow<File?>(null)
    private val _isMp3Supported = MutableStateFlow(com.autoremix.djslow.engine.export.AudioExportManager.isMp3EncoderAvailable())
    private val _currentAbMode = MutableStateFlow(com.autoremix.djslow.engine.preview.AbPreviewController.AbMode.MASTERED)
    private val _isLoudnessMatchingEnabled = MutableStateFlow(false)
    private val _unmasteredLufs = MutableStateFlow(-14.0f)
    private val _unmasteredWavFile = MutableStateFlow<File?>(null)
    private val _hasSavedProject = MutableStateFlow(false)
    private val _exportSuccessMessage = MutableStateFlow<String?>(null)

    // Intelligent Remix Engine (Arsitektur 10 Mesin Inti)
    private val _isQuickMode = MutableStateFlow(false)
    private val _remixStyle = MutableStateFlow(com.autoremix.djslow.engine.core.RemixBrain.RemixStyle.DJ_SLOW)
    private val _energyPreference = MutableStateFlow(com.autoremix.djslow.engine.core.RemixBrain.EnergyPreference.MEDIUM)
    private val _focusPreference = MutableStateFlow(com.autoremix.djslow.engine.core.RemixBrain.FocusPreference.BALANCED)
    private val _remixSeed = MutableStateFlow(System.currentTimeMillis())
    private val _musicAnalysis = MutableStateFlow<com.autoremix.djslow.engine.core.MusicUnderstandingEngine.MusicAnalysis?>(null)
    private val _remixPlan = MutableStateFlow<com.autoremix.djslow.engine.core.RemixBrain.RemixPlan?>(null)
    private val _outputStatus = MutableStateFlow(com.autoremix.djslow.engine.core.OutputEngine.OutputStatus.IDLE)
    private val _isGeneratingPreview30s = MutableStateFlow(false)
    private val _preview30sFile = MutableStateFlow<File?>(null)

    private var renderJob: kotlinx.coroutines.Job? = null

    private val group1 = combine(_vocalSource, _beatSource, audioPlayer.audioState, _statusMessage, _errorMessage) { vocal, beat, audioState, status, error ->
        Tuple5(vocal, beat, audioState, status, error)
    }
    private val group2 = combine(_vocalMixSettings, _beatMixSettings, _drumMixSettings, _bassMixSettings, _chordMixSettings) { vMix, bMix, drMix, bassMix, cMix ->
        Tuple5(vMix, bMix, drMix, bassMix, cMix)
    }
    private val group3 = combine(_melodyMixSettings, _padMixSettings, _fxMixSettings, _masterGain, _isAutoMixEnabled) { melMix, padMix, fxMix, mGain, autoMix ->
        Tuple5(melMix, padMix, fxMix, mGain, autoMix)
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
    private val group7 = combine(_masteringPreset, _vocalBusSettings, _beatBusSettings, _drumBusSettings, _bassBusSettings) { mPreset, vBus, bBus, drBus, bassBus ->
        Tuple5(mPreset, vBus, bBus, drBus, bassBus)
    }
    private val group8 = combine(_exportedWavFile, _exportedMp3File, _isMp3Supported, _currentAbMode, _isLoudnessMatchingEnabled) { expWav, expMp3, mp3Sup, abMode, lMatch ->
        Tuple5(expWav, expMp3, mp3Sup, abMode, lMatch)
    }
    private val group9 = combine(_isQuickMode, _remixStyle, _energyPreference, _focusPreference, _remixSeed) { qMode, rStyle, ePref, fPref, rSeed ->
        Tuple5(qMode, rStyle, ePref, fPref, rSeed)
    }

    private val group123 = combine(group1, group2, group3) { g1, g2, g3 -> Triple(g1, g2, g3) }
    private val group4567 = combine(group4, group5, group6, group7) { g4, g5, g6, g7 -> Quad(g4, g5, g6, g7) }
    private val group89 = combine(group8, group9, _musicBusSettings, _targetBpm) { g8, g9, musicBus, tBpm ->
        Quad(g8, g9, musicBus, tBpm)
    }

    val uiState: StateFlow<UiState> = combine(group123, group4567, group89) { g123, g4567, g89 ->
        val g1 = g123.first
        val g2 = g123.second
        val g3 = g123.third

        val g4 = g4567.a
        val g5 = g4567.b
        val g6 = g4567.c
        val g7 = g4567.d

        val g8 = g89.a
        val g9 = g89.b
        val musicBus = g89.c
        val tBpm = g89.d

        val rend = _isRendering.value
        val prog = _renderProgressFraction.value
        val stage = _renderStageText.value
        val file = _renderedWavFile.value
        val valid = _validationResult.value
        val lufsRep = _loudnessReport.value

        val isAna = _isAnalyzing.value
        val anaProg = _analysisProgressFraction.value
        val anaMsg = _analysisMessage.value

        UiState(
            stageTitle = "AUTO REMIX DJ SLOW — INTELLIGENT REMIX ENGINE",
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
            fxMixSettings = g3.c,
            masterGain = g3.d,
            isAutoMixEnabled = g3.e,
            targetBpm = tBpm,
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
            masteringPreset = g7.a,
            vocalBusSettings = g7.b,
            beatBusSettings = g7.c,
            drumBusSettings = g7.d,
            bassBusSettings = g7.e,
            musicBusSettings = musicBus,
            loudnessReport = lufsRep,
            beatAnalysis = _beatAnalysis.value,
            energyAnalysis = _energyAnalysis.value,
            isAnalyzing = isAna,
            analysisProgressFraction = anaProg,
            analysisMessage = anaMsg,
            isRendering = rend,
            renderProgressFraction = prog,
            renderStageText = stage,
            renderedWavFile = file,
            validationResult = valid,
            exportedWavFile = g8.a,
            exportedMp3File = g8.b,
            isMp3Supported = g8.c,
            currentAbMode = g8.d,
            isLoudnessMatchingEnabled = g8.e,
            unmasteredLufs = _unmasteredLufs.value,
            hasSavedProject = _hasSavedProject.value,
            exportSuccessMessage = _exportSuccessMessage.value,
            isQuickMode = g9.a,
            remixStyle = g9.b,
            energyPreference = g9.c,
            focusPreference = g9.d,
            remixSeed = g9.e,
            musicAnalysis = _musicAnalysis.value,
            remixPlan = _remixPlan.value,
            outputStatus = _outputStatus.value,
            isGeneratingPreview30s = _isGeneratingPreview30s.value,
            preview30sFile = _preview30sFile.value
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
            val result = AudioDecoder.decode(context, uri)
            result.onSuccess { source ->
                _vocalSource.value = source
                val loadRes = audioPlayer.setVocalSource(context, source)
                if (loadRes.isSuccess) {
                    _statusMessage.value = "Vokal '${source.fileName}' siap dimainkan."
                    rebuildMasterTimeline(_targetBpm.value, _detectedKey.value)
                } else {
                    _errorMessage.value = loadRes.exceptionOrNull()?.message ?: "Gagal memuat vokal ke pemutar."
                }
            }.onFailure { ex ->
                _errorMessage.value = "Format vokal tidak didukung atau berkas rusak: ${ex.message}"
            }
        }
    }

    fun onBeatSelected(context: Context, uri: Uri) {
        _errorMessage.value = null
        _statusMessage.value = "Membaca file beat..."

        viewModelScope.launch {
            val result = AudioDecoder.decode(context, uri)
            result.onSuccess { source ->
                _beatSource.value = source
                val loadRes = audioPlayer.setBeatSource(context, source)
                if (loadRes.isSuccess) {
                    _statusMessage.value = "Beat '${source.fileName}' siap dimainkan."
                    rebuildMasterTimeline(_targetBpm.value, _detectedKey.value)
                } else {
                    _errorMessage.value = loadRes.exceptionOrNull()?.message ?: "Gagal memuat beat ke pemutar."
                }
            }.onFailure { ex ->
                _errorMessage.value = "Format beat tidak didukung atau berkas rusak: ${ex.message}"
            }
        }
    }

    // ---- Kontrol Pemutar Pratinjau Asli ----

    fun onPlay() {
        _errorMessage.value = null
        audioPlayer.play()
        _statusMessage.value = "MEMUTAR AUDIO SINKRON..."
    }

    fun onPause() {
        audioPlayer.pause()
        _statusMessage.value = "AUDIO DIJEDA."
    }

    fun onStop() {
        audioPlayer.stop()
        _statusMessage.value = "AUDIO BERHENTI (Posisi awal)."
    }

    fun onSeek(fraction: Float) {
        audioPlayer.seekTo(fraction)
    }

    fun onVocalVolumeChange(volume: Float) = onVocalMixVolumeChange(volume)
    fun onBeatVolumeChange(volume: Float) = onBeatMixVolumeChange(volume)

    // ---- Kontrol Mixer Per Trek (Tahap 5) ----

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

    fun onFxMixVolumeChange(volume: Float) {
        _fxMixSettings.value = _fxMixSettings.value.copy(volume = volume.coerceIn(0.0f, 1.5f))
    }

    fun onFxMuteToggle() {
        val current = _fxMixSettings.value
        _fxMixSettings.value = current.copy(isMuted = !current.isMuted)
    }

    fun onFxSoloToggle() {
        val current = _fxMixSettings.value
        _fxMixSettings.value = current.copy(isSolo = !current.isSolo)
    }

    // ---- Kontrol Mix Bus (Tahap 5) ----

    fun onBusVolumeChange(busType: BusType, volume: Float) {
        val clamped = volume.coerceIn(0.0f, 2.0f)
        when (busType) {
            BusType.VOCAL_BUS -> _vocalBusSettings.value = _vocalBusSettings.value.copy(volume = clamped)
            BusType.BEAT_BUS -> _beatBusSettings.value = _beatBusSettings.value.copy(volume = clamped)
            BusType.DRUM_BUS -> _drumBusSettings.value = _drumBusSettings.value.copy(volume = clamped)
            BusType.BASS_BUS -> _bassBusSettings.value = _bassBusSettings.value.copy(volume = clamped)
            BusType.MUSIC_BUS -> _musicBusSettings.value = _musicBusSettings.value.copy(volume = clamped)
            BusType.MASTER_BUS -> _masterGain.value = clamped.coerceIn(0.0f, 1.5f)
        }
    }

    fun onBusMuteToggle(busType: BusType) {
        when (busType) {
            BusType.VOCAL_BUS -> _vocalBusSettings.value = _vocalBusSettings.value.copy(isMuted = !_vocalBusSettings.value.isMuted)
            BusType.BEAT_BUS -> _beatBusSettings.value = _beatBusSettings.value.copy(isMuted = !_beatBusSettings.value.isMuted)
            BusType.DRUM_BUS -> _drumBusSettings.value = _drumBusSettings.value.copy(isMuted = !_drumBusSettings.value.isMuted)
            BusType.BASS_BUS -> _bassBusSettings.value = _bassBusSettings.value.copy(isMuted = !_bassBusSettings.value.isMuted)
            BusType.MUSIC_BUS -> _musicBusSettings.value = _musicBusSettings.value.copy(isMuted = !_musicBusSettings.value.isMuted)
            BusType.MASTER_BUS -> {}
        }
    }

    fun onMasterGainChange(gain: Float) {
        _masterGain.value = gain.coerceIn(0.0f, 1.5f)
    }

    fun onAutoMixToggle() {
        _isAutoMixEnabled.value = !_isAutoMixEnabled.value
    }

    // ---- Kontrol Auto Mastering Preset (Tahap 5) ----

    fun onMasteringPresetChanged(preset: MasteringPreset) {
        _masteringPreset.value = preset
        _statusMessage.value = "Preset Mastering diatur ke: ${preset.label} (Target ${preset.targetLufs} LUFS, Ceiling ${preset.peakCeilingDbtp} dBTP)."
    }

    // ---- Kontrol Aransemen Tahap 4 ----

    fun onPresetChanged(preset: AutoDjPreset) {
        _currentPreset.value = preset
        _targetBpm.value = preset.defaultBpm
        rebuildMasterTimeline(preset.defaultBpm, _detectedKey.value)
        _statusMessage.value = "Preset aransemen: ${preset.label} (${preset.defaultBpm.roundToInt()} BPM)."
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
                _musicAnalysis.value = analysis.musicAnalysis
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
                _musicAnalysis.value = null
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

    // ---- Render WAV & Auto Mastering Studio (Tahap 5) ----

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
        _renderStageText.value = "Mempersiapkan pipeline mastering..."
        _errorMessage.value = null
        _statusMessage.value = "Memulai proses mixing studio & mastering..."

        val params = MixEngine.MixParams(
            vocalSettings = _vocalMixSettings.value,
            beatSettings = _beatMixSettings.value,
            drumSettings = _drumMixSettings.value,
            bassSettings = _bassMixSettings.value,
            chordSettings = _chordMixSettings.value,
            melodySettings = _melodyMixSettings.value,
            padSettings = _padMixSettings.value,
            fxSettings = _fxMixSettings.value,
            vocalBusSettings = _vocalBusSettings.value,
            beatBusSettings = _beatBusSettings.value,
            drumBusSettings = _drumBusSettings.value,
            bassBusSettings = _bassBusSettings.value,
            musicBusSettings = _musicBusSettings.value,
            masterGain = _masterGain.value,
            isAutoMixEnabled = _isAutoMixEnabled.value
        )

        renderJob = viewModelScope.launch {
            val result = AudioMixPipeline.run(
                context = context,
                vocalUri = vocal?.uri,
                beatUri = beat?.uri,
                targetBpm = _targetBpm.value,
                musicKey = _detectedKey.value,
                chordPreset = _chordPreset.value,
                bassPattern = _bassPattern.value,
                preset = _currentPreset.value,
                masteringPreset = _masteringPreset.value,
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
                _unmasteredWavFile.value = pipelineResult.unmasteredWavFile
                _unmasteredLufs.value = pipelineResult.unmasteredLufs
                _validationResult.value = pipelineResult.validation
                _masterTimeline.value = pipelineResult.timeline
                _loudnessReport.value = pipelineResult.loudnessReport
                pipelineResult.arrangementPlan?.let { plan ->
                    _songSections.value = plan.sections
                    _energyCurve.value = plan.energyCurve
                }
                val lufsText = pipelineResult.validation.formattedLufs
                val truePeakText = pipelineResult.validation.formattedTruePeak
                _statusMessage.value = "MASTER AUDIO BERHASIL: ${pipelineResult.durationMs / 1000}s | $lufsText | $truePeakText (Bebas Clipping)."

                val loadWav = audioPlayer.setRenderedWav(pipelineResult.wavFile)
                pipelineResult.unmasteredWavFile?.let { audioPlayer.setUnmasteredWav(it) }
                if (loadWav.isFailure) {
                    _errorMessage.value = "Mastering berhasil tetapi berkas gagal dimuat ke pemutar."
                }
            }.onFailure { ex ->
                if (ex is kotlinx.coroutines.CancellationException) {
                    _statusMessage.value = "Render dibatalkan oleh pengguna. File sementara dibersihkan."
                } else {
                    _renderedWavFile.value = null
                    _unmasteredWavFile.value = null
                    _validationResult.value = null
                    _loudnessReport.value = null
                    _errorMessage.value = ex.message ?: "Rendering gagal. Silakan ulangi."
                    _statusMessage.value = "Rendering gagal. Silakan ulangi."
                }
            }
        }
    }

    /**
     * Membatalkan proses render yang sedang berjalan dan membersihkan seluruh berkas sementara.
     */
    fun cancelRender(context: Context) {
        if (_isRendering.value) {
            renderJob?.cancel()
            renderJob = null
            com.autoremix.djslow.engine.temp.TempFileManager.cleanAllTempFiles(context)
            _isRendering.value = false
            _renderProgressFraction.value = 0.0f
            _renderStageText.value = ""
            _statusMessage.value = "Render dibatalkan. Kembali ke status siap."
        }
    }

    // =========================================================================
    // INTELLIGENT REMIX ENGINE (ALUR 10 MESIN INTI, 2 MODE PENGGUNA, SEED SYSTEM)
    // =========================================================================

    fun onToggleQuickMode() {
        _isQuickMode.value = !_isQuickMode.value
    }

    fun onStyleSelected(style: com.autoremix.djslow.engine.core.RemixBrain.RemixStyle) {
        _remixStyle.value = style
        _currentPreset.value = style.legacyPreset
        _targetBpm.value = style.recommendedBpmRange.start
        _statusMessage.value = "Gaya remix dipilih: ${style.label} (${style.description})"
    }

    fun onEnergyPreferenceSelected(energy: com.autoremix.djslow.engine.core.RemixBrain.EnergyPreference) {
        _energyPreference.value = energy
        _statusMessage.value = "Preferensi energi: ${energy.label}"
    }

    fun onFocusPreferenceSelected(focus: com.autoremix.djslow.engine.core.RemixBrain.FocusPreference) {
        _focusPreference.value = focus
        _statusMessage.value = "Fokus remix: ${focus.label}"
    }

    /**
     * ONE-CLICK AUTO REMIX: Menjalankan alur 10 mesin secara lengkap.
     */
    fun onAutoRemix(context: Context) {
        val vocal = _vocalSource.value
        val beat = _beatSource.value

        if (vocal == null && beat == null) {
            _errorMessage.value = "Pilih file vokal atau beat sebelum memulai Auto Remix."
            return
        }

        if (_isRendering.value) return

        _isRendering.value = true
        _errorMessage.value = null
        _outputStatus.value = com.autoremix.djslow.engine.core.OutputEngine.OutputStatus.ANALYZING

        renderJob = viewModelScope.launch {
            try {
                _renderStageText.value = "Mendekode berkas audio sumber..."
                _renderProgressFraction.value = 0.05f

                val vocalPcm = vocal?.uri?.let {
                    com.autoremix.djslow.engine.pcm.AudioPcmDecoder.decodeToPcm(context, it).getOrNull()
                }
                val beatPcm = beat?.uri?.let {
                    com.autoremix.djslow.engine.pcm.AudioPcmDecoder.decodeToPcm(context, it).getOrNull()
                }

                val result = com.autoremix.djslow.engine.core.RemixWorkflowEngine.executeAutoRemix(
                    context = context,
                    vocalPcm = vocalPcm,
                    beatPcm = beatPcm,
                    style = _remixStyle.value,
                    targetBpmOverride = _targetBpm.value,
                    energyPreference = _energyPreference.value,
                    focusPreference = _focusPreference.value,
                    seed = _remixSeed.value,
                    generate30sPreviewOnly = false,
                    onStatusChanged = { status, prog, msg ->
                        _outputStatus.value = status
                        _renderProgressFraction.value = prog
                        _renderStageText.value = msg
                        _statusMessage.value = "${status.label}: $msg"
                    }
                )

                _isRendering.value = false

                result.onSuccess { workflowResult ->
                    _musicAnalysis.value = workflowResult.analysis
                    _remixPlan.value = workflowResult.remixPlan
                    _targetBpm.value = workflowResult.remixPlan.targetBpm
                    _detectedKey.value = workflowResult.remixPlan.targetKey
                    _songSections.value = workflowResult.arrangement.sections.map { it.toSongSection() }
                    _renderedWavFile.value = workflowResult.masterWavFile
                    _outputStatus.value = com.autoremix.djslow.engine.core.OutputEngine.OutputStatus.READY
                    _loudnessReport.value = workflowResult.loudnessReport

                    val valRes = WavValidator.validate(workflowResult.masterWavFile)
                    _validationResult.value = valRes
                    val loadWav = audioPlayer.setRenderedWav(workflowResult.masterWavFile)
                    if (loadWav.isFailure) {
                        _errorMessage.value = "Master audio berhasil dibuat tapi gagal dimuat ke pemutar."
                    } else {
                        _statusMessage.value = "AUTO REMIX BERHASIL: Siap diputar (${valRes.formattedLufs}, ${valRes.formattedTruePeak})."
                    }
                }.onFailure { ex ->
                    if (ex is kotlinx.coroutines.CancellationException) {
                        _statusMessage.value = "Auto Remix dibatalkan."
                    } else {
                        _errorMessage.value = ex.message ?: "Auto Remix gagal."
                        _statusMessage.value = "Auto Remix gagal. Silakan coba lagi."
                    }
                }
            } catch (e: Exception) {
                _isRendering.value = false
                _errorMessage.value = e.localizedMessage ?: e.message
            }
        }
    }

    /**
     * 30 SECOND PREVIEW: Menghasilkan preview 30 detik pada bagian Build & Drop.
     */
    fun onGenerate30sPreview(context: Context) {
        val vocal = _vocalSource.value
        val beat = _beatSource.value

        if (vocal == null && beat == null) {
            _errorMessage.value = "Pilih file vokal atau beat sebelum membuat pratinjau 30 detik."
            return
        }

        if (_isGeneratingPreview30s.value || _isRendering.value) return

        _isGeneratingPreview30s.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val vocalPcm = vocal?.uri?.let {
                    com.autoremix.djslow.engine.pcm.AudioPcmDecoder.decodeToPcm(context, it).getOrNull()
                }
                val beatPcm = beat?.uri?.let {
                    com.autoremix.djslow.engine.pcm.AudioPcmDecoder.decodeToPcm(context, it).getOrNull()
                }

                val result = com.autoremix.djslow.engine.core.RemixWorkflowEngine.executeAutoRemix(
                    context = context,
                    vocalPcm = vocalPcm,
                    beatPcm = beatPcm,
                    style = _remixStyle.value,
                    targetBpmOverride = _targetBpm.value,
                    energyPreference = _energyPreference.value,
                    focusPreference = _focusPreference.value,
                    seed = _remixSeed.value,
                    generate30sPreviewOnly = true,
                    onStatusChanged = { status, prog, msg ->
                        _statusMessage.value = "Pratinjau 30s (${(prog * 100).toInt()}%): $msg"
                    }
                )

                _isGeneratingPreview30s.value = false

                result.onSuccess { workflowResult ->
                    _preview30sFile.value = workflowResult.preview30sFile
                    workflowResult.preview30sFile?.let { pFile ->
                        _renderedWavFile.value = pFile
                        val valRes = WavValidator.validate(pFile)
                        _validationResult.value = valRes
                        _loudnessReport.value = workflowResult.loudnessReport
                        audioPlayer.setRenderedWav(pFile)
                        _statusMessage.value = "PRATINJAU 30 DETIK SIAP DIPUTAR (Build & Drop)!"
                    }
                }.onFailure { ex ->
                    _errorMessage.value = "Gagal membuat pratinjau 30s: ${ex.message}"
                }
            } catch (e: Exception) {
                _isGeneratingPreview30s.value = false
                _errorMessage.value = e.localizedMessage ?: e.message
            }
        }
    }

    /**
     * BUAT VERSI LAIN: Menghasilkan seed baru, mempertahankan vokal, kunci, BPM & gaya,
     * tetapi memvariasikan pola drum, bass, melodi, dan aransemen secara nyata.
     */
    fun onRegenerateVersion(context: Context) {
        val newSeed = Random.nextLong(1, 999999)
        _remixSeed.value = newSeed
        _melodySeed.value = newSeed
        _statusMessage.value = "Seed variasi baru diterapkan (#$newSeed). Memulai render versi lain..."
        onAutoRemix(context)
    }

    // ---- Ekspor MediaStore & MP3 (Tahap 6 Final) ----

    fun exportWavToMediaStore(context: Context) {
        val srcFile = _renderedWavFile.value
        if (srcFile == null || !srcFile.exists()) {
            _errorMessage.value = "Belum ada hasil render WAV untuk diekspor."
            return
        }

        viewModelScope.launch {
            _statusMessage.value = "Mengekspor WAV ke MediaStore (Music/Auto Remix/)..."
            val exportResult = com.autoremix.djslow.engine.export.AudioExportManager.exportWavToMediaStore(
                context = context,
                sourceWavFile = srcFile
            )

            exportResult.onSuccess { exportedFile ->
                _exportedWavFile.value = exportedFile
                // Validasi ulang integritas berkas di tujuan
                val valRes = WavValidator.validate(exportedFile)
                if (valRes.isValid) {
                    _statusMessage.value = "EKSPOR WAV BERHASIL: Tersimpan di Music/Auto Remix/${exportedFile.name}. Siap diputar."
                    _exportSuccessMessage.value = "WAV berhasil diekspor ke Music/Auto Remix/${exportedFile.name} (LUFS: ${valRes.formattedLufs})!"
                    // Muat ke pemutar agar tombol [ ▶ PUTAR ] langsung memutar file MediaStore
                    audioPlayer.setRenderedWav(exportedFile)
                } else {
                    _errorMessage.value = "Validasi berkas ekspor gagal: ${valRes.errorMessage}"
                }
            }.onFailure { ex ->
                _errorMessage.value = "Ekspor WAV gagal: ${ex.message}"
            }
        }
    }

    fun exportMp3(context: Context) {
        val srcFile = _renderedWavFile.value
        if (srcFile == null || !srcFile.exists()) {
            _errorMessage.value = "Belum ada hasil render WAV untuk dikonversi ke MP3."
            return
        }

        if (!com.autoremix.djslow.engine.export.AudioExportManager.isMp3EncoderAvailable()) {
            _errorMessage.value = "Encoder MP3 perangkat tidak tersedia. Gunakan ekspor MediaStore WAV kualitas tinggi."
            return
        }

        viewModelScope.launch {
            _statusMessage.value = "Mengompres audio ke MP3 (MediaCodec)..."
            val mp3Result = com.autoremix.djslow.engine.export.AudioExportManager.exportMp3(
                context = context,
                sourceWavFile = srcFile
            )

            mp3Result.onSuccess { mp3File ->
                _exportedMp3File.value = mp3File
                _statusMessage.value = "EKSPOR MP3 BERHASIL: Tersimpan di Music/Auto Remix/${mp3File.name}."
                _exportSuccessMessage.value = "MP3 berhasil dibuat: Music/Auto Remix/${mp3File.name} (${mp3File.length() / 1024} KB)!"
            }.onFailure { ex ->
                _errorMessage.value = "Ekspor MP3 gagal: ${ex.message}"
            }
        }
    }

    fun shareAudio(context: Context) {
        val target = _exportedWavFile.value ?: _renderedWavFile.value ?: _exportedMp3File.value
        if (target == null || !target.exists()) {
            _errorMessage.value = "Belum ada berkas audio untuk dibagikan."
            return
        }
        val shareRes = com.autoremix.djslow.engine.export.AudioExportManager.shareAudioFile(context, target)
        if (shareRes.isFailure) {
            _errorMessage.value = "Gagal membagikan berkas: ${shareRes.exceptionOrNull()?.message}"
        }
    }

    // ---- A/B Preview & Loudness Matching (Tahap 6 Final) ----

    fun onSwitchAbMode(mode: com.autoremix.djslow.engine.preview.AbPreviewController.AbMode) {
        _currentAbMode.value = mode
        val masteredLufs = _loudnessReport.value?.lufsIntegrated ?: -14.0f
        val unmasteredLufs = _unmasteredLufs.value
        val gain = com.autoremix.djslow.engine.preview.AbPreviewController.calculateLoudnessGain(
            mode = mode,
            isMatchingEnabled = _isLoudnessMatchingEnabled.value,
            masteredLufs = masteredLufs,
            unmasteredLufs = unmasteredLufs
        )
        audioPlayer.switchAbMode(mode, gain)
        _statusMessage.value = "Mode Preview: ${mode.name} (Loudness Matching: ${if (_isLoudnessMatchingEnabled.value) "AKTIF" else "NONAKTIF"})"
    }

    fun onToggleLoudnessMatching() {
        val newEnabled = !_isLoudnessMatchingEnabled.value
        _isLoudnessMatchingEnabled.value = newEnabled
        val masteredLufs = _loudnessReport.value?.lufsIntegrated ?: -14.0f
        val unmasteredLufs = _unmasteredLufs.value
        val gain = com.autoremix.djslow.engine.preview.AbPreviewController.calculateLoudnessGain(
            mode = _currentAbMode.value,
            isMatchingEnabled = newEnabled,
            masteredLufs = masteredLufs,
            unmasteredLufs = unmasteredLufs
        )
        audioPlayer.switchAbMode(_currentAbMode.value, gain)
        _statusMessage.value = "Loudness Matching ${if (newEnabled) "DIAKTIFKAN (Level volume diselaraskan)" else "DINONAKTIFKAN"}."
    }

    // ---- Section Preview 15-30 Detik (Tahap 6 Final) ----

    fun onPreviewSection(section: SongSection?, durationSec: Int = 15) {
        val totalMs = _masterTimeline.value?.totalDurationMs ?: 180000L
        val (startMs, durMs) = com.autoremix.djslow.engine.preview.AbPreviewController.calculateSectionTimeRange(
            section = section,
            totalDurationMs = totalMs,
            previewDurationSeconds = durationSec
        )
        val res = audioPlayer.playSectionPreview(startMs, durMs)
        if (res.isSuccess) {
            val secName = section?.sectionType?.label ?: "Intro/Drop Utama"
            _statusMessage.value = "Preview Bagian: $secName (${durMs / 1000}s dari ${startMs / 1000}s)"
        } else {
            _errorMessage.value = "Gagal memutar preview bagian: ${res.exceptionOrNull()?.message}"
        }
    }

    // ---- Simpan & Muat Proyek Lokal Offline (Tahap 6 Final) ----

    fun saveCurrentProject(context: Context) {
        val res = com.autoremix.djslow.storage.ProjectStateRepository.saveProject(
            context = context,
            vocalUri = _vocalSource.value?.uri?.toString(),
            vocalFileName = _vocalSource.value?.fileName,
            beatUri = _beatSource.value?.uri?.toString(),
            beatFileName = _beatSource.value?.fileName,
            bpm = _targetBpm.value,
            key = _detectedKey.value,
            chordPreset = _chordPreset.value,
            bassPattern = _bassPattern.value,
            djPreset = _currentPreset.value,
            masteringPreset = _masteringPreset.value,
            melodySeed = _melodySeed.value,
            chords = _chordProgressionSummary.value,
            vocalVolume = _vocalMixSettings.value.volume,
            beatVolume = _beatMixSettings.value.volume,
            masterGain = _masterGain.value
        )
        if (res.isSuccess) {
            _hasSavedProject.value = true
            _statusMessage.value = "PROYEK DISIMPAN: Parameter sesi disimpan offline secara lokal."
        } else {
            _errorMessage.value = "Gagal menyimpan proyek: ${res.exceptionOrNull()?.message}"
        }
    }

    fun loadSavedProject(context: Context) {
        val res = com.autoremix.djslow.storage.ProjectStateRepository.loadProject(context)
        res.onSuccess { state ->
            _targetBpm.value = state.bpm
            try {
                val tonic = PitchClass.valueOf(state.keyPitch)
                val mode = MusicMode.valueOf(state.keyMode)
                _detectedKey.value = MusicKey(tonic, mode)
            } catch (_: Exception) {}
            try { _chordPreset.value = ChordSynthPreset.valueOf(state.chordPresetName) } catch (_: Exception) {}
            try { _bassPattern.value = BassPatternType.valueOf(state.bassPatternName) } catch (_: Exception) {}
            try { _currentPreset.value = AutoDjPreset.valueOf(state.djPresetName) } catch (_: Exception) {}
            try { _masteringPreset.value = MasteringPreset.valueOf(state.masteringPresetName) } catch (_: Exception) {}
            _melodySeed.value = state.melodySeed
            _masterGain.value = state.masterGain

            val parsedChords = com.autoremix.djslow.storage.ProjectStateRepository.parseChords(state.chordsJson)
            if (parsedChords.isNotEmpty()) {
                _chordProgressionSummary.value = parsedChords
            }

            _vocalMixSettings.update { it.copy(volume = state.vocalVolume) }
            _beatMixSettings.update { it.copy(volume = state.beatVolume) }

            rebuildMasterTimeline(_targetBpm.value, _detectedKey.value)
            _statusMessage.value = "PROYEK DIMUAT: Parameter sesi offline berhasil dipulihkan."
        }.onFailure { ex ->
            _errorMessage.value = "Gagal memuat proyek: ${ex.message}"
        }
    }

    fun checkSavedProject(context: Context) {
        _hasSavedProject.value = com.autoremix.djslow.storage.ProjectStateRepository.hasSavedProject(context)
    }

    // ---- Pemutaran Hasil Render WAV Nyata ----

    fun onPlayRendered() {
        _errorMessage.value = null
        val res = audioPlayer.playRendered()
        if (res.isSuccess) {
            _statusMessage.value = "MEMUTAR MASTER HASIL AUDIO NYATA..."
        }
    }

    fun onPauseRendered() {
        audioPlayer.pauseRendered()
        _statusMessage.value = "MASTER DIJEDA."
    }

    fun onStopRendered() {
        audioPlayer.stopRendered()
        _statusMessage.value = "MASTER BERHENTI."
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
    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
