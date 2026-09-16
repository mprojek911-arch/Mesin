package com.autoremix.djslow.engine.core

import com.autoremix.djslow.engine.drum.DrumEngine
import com.autoremix.djslow.engine.drum.DrumEvent
import com.autoremix.djslow.engine.melody.MelodyEngine
import com.autoremix.djslow.engine.melody.MelodyEvent
import com.autoremix.djslow.engine.pad.PadEngine
import com.autoremix.djslow.engine.pcm.AudioPcmData
import com.autoremix.djslow.engine.structure.EnergyCurve
import com.autoremix.djslow.engine.structure.SongSection
import com.autoremix.djslow.engine.structure.SongSectionType
import com.autoremix.djslow.engine.synth.BassEngine
import com.autoremix.djslow.engine.synth.ChordSynthEngine
import com.autoremix.djslow.engine.timeline.MasterTimeline
import com.autoremix.djslow.engine.arrangement.SectionEngines
import com.autoremix.djslow.engine.arrangement.TransitionFxEvent
import com.autoremix.djslow.engine.music.MusicKey
import com.autoremix.djslow.engine.timeline.BeatGridPoint
import com.autoremix.djslow.engine.timeline.TimelineEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 6. MUSIC GENERATOR ENGINE
 * Satukan generator: DRUM, BASS, CHORD, MELODY, PAD, FX.
 * Semua menerima: MASTER BPM, KEY, CHORD TIMELINE, BEAT GRID, BAR GRID, ARRANGEMENT, ENERGY, STYLE, SEED.
 * Semua menggunakan MASTER MUSICAL CLOCK (44.1 kHz).
 *
 * Perilaku Seksi:
 * - DROP: KICK = STRONG, BASS = FULL, DRUM = FULL, CHORD = WIDE, MELODY = HOOK, PAD = SUPPORT, FX = IMPACT
 * - BREAK: KICK = REDUCED, BASS = LIGHT, DRUM = REDUCED, CHORD = SOFT, PAD = SUPPORT, FX = AMBIENCE
 * - BUILD: DRUM = RISING, BASS = CONTROLLED, FX = RISER, ENERGY = INCREASING
 * - PRE-DROP: REDUCE LOW END, VOCAL CHOP / FX, SILENCE OR TENSION MOMENT, THEN DROP
 */
object MusicGeneratorEngine {

    /**
     * Konteks parameter terpadu Master Musical Clock (Fase 3 - Bagian C).
     * Seluruh generator instrumen wajib merujuk pada parameter tersinkronisasi ini.
     */
    data class GeneratorContext(
        val masterBpm: Float,
        val targetKey: MusicKey,
        val beatGrid: List<BeatGridPoint>,
        val barGrid: Int,
        val chordTimeline: List<TimelineEvent.ChordEvent>,
        val arrangement: ArrangementEngine.FullArrangement,
        val energyCurve: EnergyCurve,
        val style: RemixBrain.RemixStyle,
        val seed: Long
    )

    data class GeneratedMusic(
        val drumPcm: AudioPcmData,
        val bassPcm: AudioPcmData,
        val chordPcm: AudioPcmData,
        val melodyPcm: AudioPcmData,
        val padPcm: AudioPcmData,
        val fxPcm: AudioPcmData,
        val drumEvents: List<DrumEvent>,
        val bassEvents: List<com.autoremix.djslow.engine.timeline.TimelineEvent.BassEvent>,
        val melodyEvents: List<MelodyEvent>,
        val transitionEvents: List<TransitionFxEvent>
    )

    /**
     * Membuat GeneratorContext dari timeline, remixPlan, dan arrangement.
     */
    fun createGeneratorContext(
        timeline: MasterTimeline,
        remixPlan: RemixBrain.RemixPlan,
        arrangement: ArrangementEngine.FullArrangement
    ): GeneratorContext {
        val sections = arrangement.sections.map { it.toSongSection() }
        val energyCurve = EnergyCurve(sections, timeline.totalFrames)
        return GeneratorContext(
            masterBpm = remixPlan.targetBpm,
            targetKey = remixPlan.targetKey,
            beatGrid = timeline.beatGrid,
            barGrid = timeline.totalBars,
            chordTimeline = timeline.chordEvents,
            arrangement = arrangement,
            energyCurve = energyCurve,
            style = remixPlan.style,
            seed = remixPlan.melodyPlan.seed
        )
    }

    /**
     * Menghasilkan seluruh instrumen musik secara serempak di Master Clock.
     */
    suspend fun generateMusic(
        timeline: MasterTimeline,
        remixPlan: RemixBrain.RemixPlan,
        arrangement: ArrangementEngine.FullArrangement,
        onProgress: ((Float, String) -> Unit)? = null
    ): Result<GeneratedMusic> = withContext(Dispatchers.Default) {
        try {
            val sampleRate = timeline.sampleRate
            val totalFrames = timeline.totalFrames.toInt()
            val sections = arrangement.sections.map { it.toSongSection() }

            // 1. Kurva Energi
            val energyCurve = EnergyCurve(sections, timeline.totalFrames)

            // 2. Generate DRUM (Patuhi aturan section & style)
            onProgress?.invoke(0.15f, "Menyintesis track Drum (Kick punchy, Snare, Hihat swing)...")
            val beatAnalysis = com.autoremix.djslow.engine.analysis.BeatContentAnalyzer.BeatContentAnalysis(
                drumPresence = 0f,
                bassPresence = 0f,
                spectralDensity = 0f,
                isBeatEmptyOrSilent = true,
                recommendedDrumMode = com.autoremix.djslow.engine.analysis.GeneratedDrumMode.HIGH,
                explanation = "Synthesizer drum diaktifkan penuh"
            )
            val scheduledDrumEvents = DrumEngine.scheduleDrumEvents(
                sections = sections,
                energyCurve = energyCurve,
                bpm = remixPlan.targetBpm,
                sampleRate = sampleRate,
                totalBars = timeline.totalBars,
                beatAnalysis = beatAnalysis,
                preset = remixPlan.style.legacyPreset
            )
            val rawDrum = DrumEngine.renderDrums(
                events = scheduledDrumEvents,
                totalSamples = timeline.totalFrames,
                sampleRate = sampleRate
            )
            val drumVolume = remixPlan.mixPlan.drumVolume
            val drumPcm = if (drumVolume == 1f) rawDrum else rawDrum.copy(
                samples = FloatArray(rawDrum.samples.size) { rawDrum.samples[it] * drumVolume }
            )

            // 3. Generate BASS (Sub-bass, glide, ducking)
            onProgress?.invoke(0.35f, "Menyintesis track Sub-Bass & 808 Glide...")
            val rawBassEvents = BassEngine.generateBassEvents(
                timeline = timeline,
                key = remixPlan.targetKey,
                patternType = remixPlan.bassPlan.patternType
            )
            // BAGIAN D Section Behavior:
            // DROP: full bass
            // BREAK: light bass (reduced velocity)
            // BUILD: controlled bass (steady)
            // PRE-DROP: reduced low end (mute bass to build drop tension)
            val filteredBassEvents = rawBassEvents.mapNotNull { bassEvent ->
                val sec = arrangement.getSectionForBar(bassEvent.barIndex)
                when {
                    sec == null || sec.bassMode == ArrangementEngine.BassMode.OFF || sec.sectionType == SongSectionType.PRE_DROP -> null
                    sec.sectionType in listOf(SongSectionType.BREAK, SongSectionType.BREAKDOWN) -> {
                        bassEvent.copy(velocity = (bassEvent.velocity * 0.55f).coerceIn(0.1f, 1.0f))
                    }
                    sec.sectionType in listOf(SongSectionType.BUILD_UP, SongSectionType.BUILD_UP_2, SongSectionType.FINAL_BUILD) -> {
                        bassEvent.copy(velocity = (bassEvent.velocity * 0.70f).coerceIn(0.1f, 1.0f))
                    }
                    else -> bassEvent
                }
            }
            val bassPcm = BassEngine.renderBassPcm(
                timeline = timeline,
                bassEvents = filteredBassEvents,
                volume = remixPlan.bassPlan.bassVolume
            )

            // 4. Generate CHORD SYNTH (Polyphonic progression)
            onProgress?.invoke(0.55f, "Menyintesis progresi akor harmonik...")
            val chordPcm = ChordSynthEngine.renderProgressionPcm(
                timeline = timeline,
                preset = remixPlan.chordPlan.preset,
                volume = remixPlan.chordPlan.volume,
                arrangement = arrangement
            )

            // 5. Generate MELODY HOOK (Procedural dari seed)
            onProgress?.invoke(0.70f, "Menyusun melodi hook DJ Slow berkarakter...")
            val melodyEvents = MelodyEngine.scheduleMelodyEvents(
                key = remixPlan.targetKey,
                chords = remixPlan.chordPlan.progression,
                bpm = remixPlan.targetBpm,
                sampleRate = sampleRate,
                totalBars = timeline.totalBars,
                sections = sections,
                energyCurve = energyCurve,
                seed = remixPlan.melodyPlan.seed,
                preset = remixPlan.style.legacyPreset
            )
            val rawMelody = MelodyEngine.renderMelody(
                events = melodyEvents,
                totalSamples = timeline.totalFrames,
                sampleRate = sampleRate
            )
            val melodyVolume = remixPlan.melodyPlan.volume
            val melodyPcm = if (melodyVolume == 1f) rawMelody else rawMelody.copy(
                samples = FloatArray(rawMelody.samples.size) { rawMelody.samples[it] * melodyVolume }
            )

            // 6. Generate PAD ATMOSFIR
            onProgress?.invoke(0.85f, "Membangun pad atmosferik latar belakang...")
            val padEvents = PadEngine.schedulePadEvents(
                key = remixPlan.targetKey,
                chords = remixPlan.chordPlan.progression,
                bpm = remixPlan.targetBpm,
                sampleRate = sampleRate,
                totalBars = timeline.totalBars,
                sections = sections,
                energyCurve = energyCurve,
                preset = remixPlan.style.legacyPreset,
                hasVocal = false
            )
            val rawPad = PadEngine.renderPad(
                events = padEvents,
                totalSamples = timeline.totalFrames,
                sampleRate = sampleRate
            )
            val padVolume = remixPlan.padPlan.volume
            val padPcm = if (padVolume == 1f) rawPad else rawPad.copy(
                samples = FloatArray(rawPad.samples.size) { rawPad.samples[it] * padVolume }
            )

            // 7. Generate FX TRANSISI (Risers, Impacts, Sweeps)
            onProgress?.invoke(0.95f, "Menyintesis FX transisi, risers, dan impact...")
            val samplesPerBar = (sampleRate * 60f / remixPlan.targetBpm * 4f).toLong()
            val fxEvents = SectionEngines.scheduleTransitionEvents(
                sections = sections,
                sampleRate = sampleRate,
                samplesPerBar = samplesPerBar
            )
            val rawFx = SectionEngines.renderTransitions(
                events = fxEvents,
                totalSamples = timeline.totalFrames,
                sampleRate = sampleRate
            )
            val fxVolume = remixPlan.fxPlan.volume
            val fxPcm = if (fxVolume == 1f) rawFx else rawFx.copy(
                samples = FloatArray(rawFx.samples.size) { rawFx.samples[it] * fxVolume }
            )

            onProgress?.invoke(1.0f, "Sintesis seluruh instrumen musik selesai.")

            val generated = GeneratedMusic(
                drumPcm = drumPcm,
                bassPcm = bassPcm,
                chordPcm = chordPcm,
                melodyPcm = melodyPcm,
                padPcm = padPcm,
                fxPcm = fxPcm,
                drumEvents = scheduledDrumEvents,
                bassEvents = filteredBassEvents,
                melodyEvents = melodyEvents,
                transitionEvents = fxEvents
            )

            Result.success(generated)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Gagal membuat musik sintetis: ${e.localizedMessage ?: e.message}"))
        }
    }
}
