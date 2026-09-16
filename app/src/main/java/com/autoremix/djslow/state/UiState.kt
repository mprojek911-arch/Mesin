package com.autoremix.djslow.state

import com.autoremix.djslow.engine.AudioSource
import com.autoremix.djslow.engine.AudioState
import com.autoremix.djslow.engine.PlaybackEngineState
import com.autoremix.djslow.engine.analysis.AutoEnergyAnalyzer
import com.autoremix.djslow.engine.analysis.BeatContentAnalyzer.BeatContentAnalysis
import com.autoremix.djslow.engine.analysis.BpmDetector
import com.autoremix.djslow.engine.arrangement.AutoDjPreset
import com.autoremix.djslow.engine.mix.MixTrackSettings
import com.autoremix.djslow.engine.music.Chord
import com.autoremix.djslow.engine.music.ChordType
import com.autoremix.djslow.engine.music.MusicKey
import com.autoremix.djslow.engine.music.MusicMode
import com.autoremix.djslow.engine.music.PitchClass
import com.autoremix.djslow.engine.structure.EnergyCurve
import com.autoremix.djslow.engine.structure.SongSection
import com.autoremix.djslow.engine.structure.SongSectionType
import com.autoremix.djslow.engine.synth.BassPatternType
import com.autoremix.djslow.engine.synth.ChordSynthPreset
import com.autoremix.djslow.engine.timeline.MasterTimeline
import com.autoremix.djslow.engine.wav.WavValidator
import java.io.File
import kotlin.math.roundToInt

/**
 * UI State untuk tampilan Beranda AUTO REMIX DJ SLOW:
 * Tahap 1 (Pemilihan & Playback), Tahap 2 (Mixing & WAV Render),
 * Tahap 3 (BPM, Key, Chord, Bass), dan Tahap 4 (Drum, Melodi, Pad, Auto DJ Energy & Structure).
 */
data class UiState(
    val stageTitle: String = "TAHAP 4 — ARRANGEMENT (DRUM + MELODY + PAD + DJ STRUCTURE)",
    val vocalSource: AudioSource? = null,
    val beatSource: AudioSource? = null,
    val audioState: AudioState = AudioState(),
    val statusMessage: String = "Siap. Silakan pilih vokal & beat, lalu klik Analisis & Aransemen.",
    val errorMessage: String? = null,

    // Parameter Mixer Multi-Track (Vokal, Beat, Drum, Bass, Chord, Melodi, Pad)
    val vocalMixSettings: MixTrackSettings = MixTrackSettings(volume = 1.0f),
    val beatMixSettings: MixTrackSettings = MixTrackSettings(volume = 0.8f),
    val drumMixSettings: MixTrackSettings = MixTrackSettings(volume = 0.85f),
    val bassMixSettings: MixTrackSettings = MixTrackSettings(volume = 0.85f),
    val chordMixSettings: MixTrackSettings = MixTrackSettings(volume = 0.70f),
    val melodyMixSettings: MixTrackSettings = MixTrackSettings(volume = 0.80f),
    val padMixSettings: MixTrackSettings = MixTrackSettings(volume = 0.75f),
    val masterGain: Float = 0.90f,
    val isAutoMixEnabled: Boolean = true,

    // Parameter Mesin Musik Tahap 3
    val vocalBpm: BpmDetector.BpmResult? = null,
    val beatBpm: BpmDetector.BpmResult? = null,
    val targetBpm: Float = 80.0f,
    val isBpmEstimated: Boolean = true,
    val bpmConfidence: Float = 0.5f,

    val detectedKey: MusicKey = MusicKey(PitchClass.A, MusicMode.MINOR),
    val isKeyEstimated: Boolean = true,
    val keyConfidence: Float = 0.5f,

    val chordProgressionSummary: List<Chord> = listOf(
        Chord(PitchClass.A, ChordType.MINOR),
        Chord(PitchClass.F, ChordType.MAJOR),
        Chord(PitchClass.C, ChordType.MAJOR),
        Chord(PitchClass.G, ChordType.MAJOR)
    ),
    val isChordEstimated: Boolean = true,
    val chordConfidence: Float = 0.5f,
    val chordPreset: ChordSynthPreset = ChordSynthPreset.SOFT_PIANO,
    val bassPattern: BassPatternType = BassPatternType.DJ_SLOW_BASS,

    val masterTimeline: MasterTimeline? = null,

    // Parameter Aransemen Tahap 4
    val songSections: List<SongSection> = emptyList(),
    val currentPreset: AutoDjPreset = AutoDjPreset.DJ_SLOW,
    val melodySeed: Long = 42L,
    val beatAnalysis: BeatContentAnalysis? = null,
    val energyAnalysis: AutoEnergyAnalyzer.EnergyAnalysisResult? = null,
    val energyCurve: EnergyCurve? = null,

    // Status Analisis Musik & Aransemen
    val isAnalyzing: Boolean = false,
    val analysisProgressFraction: Float = 0.0f,
    val analysisMessage: String = "",

    // Status Rendering WAV Latar Belakang
    val isRendering: Boolean = false,
    val renderProgressFraction: Float = 0.0f,
    val renderStageText: String = "",
    val renderedWavFile: File? = null,
    val validationResult: WavValidator.ValidationResult? = null
) {
    val isVocalSelected: Boolean
        get() = vocalSource != null

    val isBeatSelected: Boolean
        get() = beatSource != null

    val hasAnyAudio: Boolean
        get() = isVocalSelected || isBeatSelected

    val isPlaying: Boolean
        get() = audioState.playbackState == PlaybackEngineState.MEMUTAR

    val isPaused: Boolean
        get() = audioState.playbackState == PlaybackEngineState.DIJEDA

    val maxDurationMs: Long
        get() = maxOf(audioState.vocalDurationMs, audioState.beatDurationMs)

    val currentPositionMs: Long
        get() = maxOf(audioState.vocalPositionMs, audioState.beatPositionMs)

    val playbackProgress: Float
        get() = if (maxDurationMs > 0) {
            (currentPositionMs.toFloat() / maxDurationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    // Playback Hasil WAV
    val isRenderedAvailable: Boolean
        get() = renderedWavFile != null && validationResult?.isValid == true

    val isRenderedPlaying: Boolean
        get() = audioState.isRenderedPlaying

    val isRenderedPaused: Boolean
        get() = audioState.isRenderedPaused

    val renderedDurationMs: Long
        get() = audioState.renderedDurationMs.takeIf { it > 0 } ?: (validationResult?.durationMs ?: 0L)

    val renderedPositionMs: Long
        get() = audioState.renderedPositionMs

    val renderedProgressFraction: Float
        get() = if (renderedDurationMs > 0) {
            (renderedPositionMs.toFloat() / renderedDurationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    // Teks Bantuan UI
    val vocalBpmText: String
        get() = vocalBpm?.let {
            if (it.isEstimated) "Perkiraan: ${it.bpm.roundToInt()} BPM" else "${it.bpm.roundToInt()} BPM"
        } ?: "Belum dianalisis"

    val beatBpmText: String
        get() = beatBpm?.let {
            if (it.isEstimated) "Perkiraan: ${it.bpm.roundToInt()} BPM" else "${it.bpm.roundToInt()} BPM"
        } ?: "Belum dianalisis"

    val targetBpmText: String
        get() = if (isBpmEstimated) "Perkiraan: ${targetBpm.roundToInt()} BPM" else "${targetBpm.roundToInt()} BPM"

    val keyText: String
        get() = if (isKeyEstimated) "Perkiraan: ${detectedKey.displayName}" else detectedKey.displayName

    val chordText: String
        get() = chordProgressionSummary.joinToString(" - ") { it.name }

    val confidenceText: String
        get() = when {
            keyConfidence >= 0.70f && bpmConfidence >= 0.70f -> "Tinggi (${(bpmConfidence * 100).toInt()}%)"
            keyConfidence >= 0.45f -> "Sedang (${(keyConfidence * 100).toInt()}%)"
            else -> "Perkiraan"
        }
}
