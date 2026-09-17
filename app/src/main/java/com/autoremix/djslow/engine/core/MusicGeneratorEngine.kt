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
import com.autoremix.djslow.engine.synth.ChordSynthPreset
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

    data class ScheduledMusicEvents(
        val drumEvents: List<DrumEvent>,
        val bassEvents: List<com.autoremix.djslow.engine.timeline.TimelineEvent.BassEvent>,
        val melodyEvents: List<MelodyEvent>,
        val padEvents: List<com.autoremix.djslow.engine.pad.PadEvent>,
        val transitionEvents: List<TransitionFxEvent>,
        val chordVoices: List<ChordSynthEngine.ChordVoiceState>,
        val bassVolume: Float,
        val drumVolume: Float = 1.0f,
        val melodyVolume: Float = 1.0f,
        val padVolume: Float = 0.85f,
        val fxVolume: Float = 0.90f
    )

    data class GeneratedMusic(
        val drumPcm: AudioPcmData = AudioPcmData.createEmpty(44100, 2),
        val bassPcm: AudioPcmData = AudioPcmData.createEmpty(44100, 2),
        val chordPcm: AudioPcmData = AudioPcmData.createEmpty(44100, 2),
        val melodyPcm: AudioPcmData = AudioPcmData.createEmpty(44100, 2),
        val padPcm: AudioPcmData = AudioPcmData.createEmpty(44100, 2),
        val fxPcm: AudioPcmData = AudioPcmData.createEmpty(44100, 2),
        val drumEvents: List<DrumEvent> = emptyList(),
        val bassEvents: List<com.autoremix.djslow.engine.timeline.TimelineEvent.BassEvent> = emptyList(),
        val chordEvents: List<com.autoremix.djslow.engine.timeline.TimelineEvent.ChordEvent> = emptyList(),
        val melodyEvents: List<MelodyEvent> = emptyList(),
        val padEvents: List<com.autoremix.djslow.engine.pad.PadEvent> = emptyList(),
        val transitionEvents: List<TransitionFxEvent> = emptyList(),
        val chordVoices: List<ChordSynthEngine.ChordVoiceState> = emptyList(),
        val timeline: MasterTimeline? = null,
        val arrangement: ArrangementEngine.FullArrangement? = null,
        val remixPlan: RemixBrain.RemixPlan? = null
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
     * Menjadwalkan seluruh event instrumen musik (Drum, Bass, Melody, Pad, FX) tanpa merender PCM.
     * Tidak mengalokasikan array PCM full-track, murni objek event timeline.
     */
    fun scheduleAllEvents(
        timeline: MasterTimeline,
        remixPlan: RemixBrain.RemixPlan,
        arrangement: ArrangementEngine.FullArrangement
    ): ScheduledMusicEvents {
        val sampleRate = timeline.sampleRate
        val sections = arrangement.sections.map { it.toSongSection() }
        val energyCurve = EnergyCurve(sections, timeline.totalFrames)

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

        val rawBassEvents = BassEngine.generateBassEvents(
            timeline = timeline,
            key = remixPlan.targetKey,
            patternType = remixPlan.bassPlan.patternType
        )
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

        val samplesPerBar = (sampleRate * 60f / remixPlan.targetBpm * 4f).toLong()
        val fxEvents = SectionEngines.scheduleTransitionEvents(
            sections = sections,
            sampleRate = sampleRate,
            samplesPerBar = samplesPerBar
        )

        // Pre-create ChordVoiceState list for continuous phase and low latency block rendering
        val totalFrames = timeline.totalFrames.toInt()
        val chordPreset = remixPlan.chordPlan.preset
        val chordVolume = remixPlan.chordPlan.volume
        val chordVoices = timeline.chordEvents.mapNotNull { event ->
            val eventStartFrame = event.startSample
            val eventEndFrame = minOf(event.endSample, totalFrames.toLong())
            val eventFrames = eventEndFrame - eventStartFrame
            if (eventFrames <= 0 || eventStartFrame >= totalFrames) {
                null
            } else {
                val sec = arrangement.getSectionForBar(event.barIndex)
                val secVolume = when (sec?.sectionType) {
                    SongSectionType.DROP, SongSectionType.MAIN_DROP, SongSectionType.PEAK, SongSectionType.FINAL_DROP -> chordVolume * 1.0f
                    SongSectionType.BREAK, SongSectionType.BREAKDOWN -> chordVolume * 0.65f
                    SongSectionType.BUILD_UP, SongSectionType.BUILD_UP_2, SongSectionType.FINAL_BUILD -> chordVolume * 0.80f
                    SongSectionType.PRE_DROP -> chordVolume * 0.60f
                    SongSectionType.INTRO, SongSectionType.OUTRO -> chordVolume * 0.60f
                    else -> chordVolume
                }
                ChordSynthEngine.ChordVoiceState(
                    event = event,
                    totalFrames = eventFrames,
                    sampleRate = sampleRate,
                    preset = chordPreset,
                    volume = secVolume
                )
            }
        }

        return ScheduledMusicEvents(
            drumEvents = scheduledDrumEvents,
            bassEvents = filteredBassEvents,
            melodyEvents = melodyEvents,
            padEvents = padEvents,
            transitionEvents = fxEvents,
            chordVoices = chordVoices,
            bassVolume = remixPlan.bassPlan.bassVolume,
            drumVolume = remixPlan.mixPlan.drumVolume,
            melodyVolume = remixPlan.mixPlan.melodyVolume,
            padVolume = remixPlan.mixPlan.padVolume,
            fxVolume = remixPlan.mixPlan.fxVolume
        )
    }

    /**
     * Merender blok audio instrumen secara on-the-fly (misal 16384 frame) langsung ke buffer stereo.
     * Mencegah alokasi memori heap besar (anti OOM).
     */
    fun renderMusicBlock(
        startFrame: Long,
        frameCount: Int,
        sampleRate: Int,
        timeline: MasterTimeline,
        events: ScheduledMusicEvents,
        arrangement: ArrangementEngine.FullArrangement,
        outStereo: FloatArray
    ) {
        val channels = 2
        val totalSamples = frameCount * channels
        java.util.Arrays.fill(outStereo, 0, totalSamples, 0f)

        val tempBuf = FloatArray(totalSamples)

        // 1. Drum Block
        DrumEngine.renderDrumBlock(
            events = events.drumEvents,
            startFrame = startFrame,
            frameCount = frameCount,
            sampleRate = sampleRate,
            outBuffer = tempBuf,
            offset = 0
        )
        val drumVol = events.drumVolume
        for (i in 0 until totalSamples) {
            outStereo[i] += tempBuf[i] * drumVol
        }

        // 2. Bass Block
        tempBuf.fill(0f)
        BassEngine.renderBassBlock(
            bassEvents = events.bassEvents,
            startFrame = startFrame,
            frameCount = frameCount,
            sampleRate = sampleRate,
            outBuffer = tempBuf,
            offset = 0,
            volume = events.bassVolume
        )
        for (i in 0 until totalSamples) {
            outStereo[i] += tempBuf[i]
        }

        // 3. Chord Block
        tempBuf.fill(0f)
        val endFrame = startFrame + frameCount
        val activeVoices = events.chordVoices.filter { voice ->
            voice.event.endSample > startFrame && voice.event.startSample < endFrame
        }
        if (activeVoices.isNotEmpty()) {
            ChordSynthEngine.renderChordBlock(
                activeVoices = activeVoices,
                startFrame = startFrame,
                frameCount = frameCount,
                outBuffer = tempBuf,
                offset = 0,
                channels = channels
            )
            for (i in 0 until totalSamples) {
                outStereo[i] += tempBuf[i]
            }
        }

        // 4. Melody Block
        tempBuf.fill(0f)
        MelodyEngine.renderMelodyBlock(
            events = events.melodyEvents,
            startFrame = startFrame,
            frameCount = frameCount,
            sampleRate = sampleRate,
            outBuffer = tempBuf,
            offset = 0
        )
        val melVol = events.melodyVolume
        for (i in 0 until totalSamples) {
            outStereo[i] += tempBuf[i] * melVol
        }

        // 5. Pad Block
        tempBuf.fill(0f)
        PadEngine.renderPadBlock(
            events = events.padEvents,
            startFrame = startFrame,
            frameCount = frameCount,
            sampleRate = sampleRate,
            outBlock = tempBuf,
            offset = 0
        )
        val padVol = events.padVolume
        for (i in 0 until totalSamples) {
            outStereo[i] += tempBuf[i] * padVol
        }

        // 6. FX Transitions Block
        tempBuf.fill(0f)
        SectionEngines.renderTransitionBlock(
            events = events.transitionEvents,
            chunkStartFrame = startFrame,
            frameCount = frameCount,
            sampleRate = sampleRate,
            outBuffer = tempBuf,
            offset = 0
        )
        val fxVol = events.fxVolume
        for (i in 0 until totalSamples) {
            outStereo[i] += tempBuf[i] * fxVol
        }
    }

    /**
     * Menghasilkan jadwal seluruh event instrumen musik secara serempak di Master Clock.
     * Sesuai arsitektur FASE 2 & FASE 3, fungsi ini HANYA menghasilkan EVENTS, TIMELINE,
     * ARRANGEMENT, PARAMETER, SEED, dan METADATA — BUKAN FULL PCM — untuk mencegah OOM.
     */
    suspend fun generateMusic(
        timeline: MasterTimeline,
        remixPlan: RemixBrain.RemixPlan,
        arrangement: ArrangementEngine.FullArrangement,
        onProgress: ((Float, String) -> Unit)? = null
    ): Result<GeneratedMusic> = withContext(Dispatchers.Default) {
        try {
            onProgress?.invoke(0.20f, "Menyusun jadwal event instrumen (Streaming Architecture)...")
            val scheduled = scheduleAllEvents(timeline, remixPlan, arrangement)
            onProgress?.invoke(1.0f, "Jadwal event instrumen siap untuk streaming render.")

            val generated = GeneratedMusic(
                drumPcm = AudioPcmData.createEmpty(timeline.sampleRate, 2),
                bassPcm = AudioPcmData.createEmpty(timeline.sampleRate, 2),
                chordPcm = AudioPcmData.createEmpty(timeline.sampleRate, 2),
                melodyPcm = AudioPcmData.createEmpty(timeline.sampleRate, 2),
                padPcm = AudioPcmData.createEmpty(timeline.sampleRate, 2),
                fxPcm = AudioPcmData.createEmpty(timeline.sampleRate, 2),
                drumEvents = scheduled.drumEvents,
                bassEvents = scheduled.bassEvents,
                chordEvents = timeline.chordEvents,
                melodyEvents = scheduled.melodyEvents,
                padEvents = scheduled.padEvents,
                transitionEvents = scheduled.transitionEvents,
                chordVoices = scheduled.chordVoices,
                timeline = timeline,
                arrangement = arrangement,
                remixPlan = remixPlan
            )

            Result.success(generated)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Gagal membuat jadwal musik: ${e.localizedMessage ?: e.message}"))
        }
    }
}
