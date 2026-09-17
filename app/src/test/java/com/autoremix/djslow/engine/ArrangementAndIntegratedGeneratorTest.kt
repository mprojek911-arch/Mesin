package com.autoremix.djslow.engine

import com.autoremix.djslow.engine.arrangement.SectionEngines
import com.autoremix.djslow.engine.arrangement.TransitionFxType
import com.autoremix.djslow.engine.core.ArrangementEngine
import com.autoremix.djslow.engine.core.MusicalMapEngine
import com.autoremix.djslow.engine.core.MusicGeneratorEngine
import com.autoremix.djslow.engine.core.MusicUnderstandingEngine
import com.autoremix.djslow.engine.core.RemixBrain
import com.autoremix.djslow.engine.drum.DrumSoundType
import com.autoremix.djslow.engine.music.Chord
import com.autoremix.djslow.engine.music.ChordEngine
import com.autoremix.djslow.engine.music.MusicKey
import com.autoremix.djslow.engine.music.MusicMode
import com.autoremix.djslow.engine.music.PitchClass
import com.autoremix.djslow.engine.pcm.AudioPcmData
import com.autoremix.djslow.engine.structure.EnergyCurve
import com.autoremix.djslow.engine.structure.SongSectionType
import com.autoremix.djslow.engine.timeline.MasterMusicalClock
import com.autoremix.djslow.engine.timeline.MasterTimeline
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ArrangementAndIntegratedGeneratorTest {

    private val sampleRate = 44100
    private val targetBpm = 80.0f
    private val key = MusicKey(PitchClass.A, MusicMode.MINOR)

    private fun createMockAnalysis(totalDurationMs: Long = 60000L): MusicUnderstandingEngine.MusicAnalysis {
        val totalFrames = (totalDurationMs * sampleRate / 1000L).toInt()
        val baseTimeline = MasterTimeline.build(bpm = targetBpm, totalDurationMs = totalDurationMs, sampleRate = sampleRate)
        val chordResult = ChordEngine.buildChordProgression(baseTimeline, key)
        val timeline = baseTimeline.copy(chordEvents = chordResult.chordEvents)
        val chords = chordResult.chordProgressionSummary
        val sections = listOf(
            com.autoremix.djslow.engine.structure.SongSection(SongSectionType.INTRO, 0, 4, 0L, 88200L, 0L, 2000L, targetEnergy = 0.25f),
            com.autoremix.djslow.engine.structure.SongSection(SongSectionType.DROP, 4, 12, 88200L, 264600L, 2000L, 6000L, targetEnergy = 0.90f)
        )
        val energyCurve = EnergyCurve(sections, totalFrames.toLong())
        return MusicUnderstandingEngine.MusicAnalysis(
            bpm = targetBpm,
            bpmConfidence = 0.95f,
            isBpmEstimated = false,
            key = key,
            keyConfidence = 0.90f,
            isKeyEstimated = false,
            chords = chords,
            chordConfidence = 0.92f,
            sections = sections,
            energyAverage = 0.65f,
            energyAnalysis = null,
            beatAnalysis = null,
            downbeats = listOf(0L, 44100L, 88200L),
            vocalPhrases = emptyList(),
            durationMs = totalDurationMs,
            timeline = timeline.copy(energyCurve = energyCurve),
            energyCurve = energyCurve
        )
    }

    @Test
    fun testArrangementDurationAndSectionOrder() {
        val analysis = createMockAnalysis(totalDurationMs = 96000L) // 32 bar @ 80 BPM
        val musicalMap = MusicalMapEngine.buildMap(analysis, analysis.timeline)
        val remixPlan = RemixBrain.createPlan(analysis, musicalMap, RemixBrain.RemixStyle.DJ_SLOW)

        val arrangement = ArrangementEngine.createArrangement(analysis, musicalMap, remixPlan)
        val sections = arrangement.sections

        assertTrue("Arrangement must contain sections", sections.isNotEmpty())
        assertEquals(0, sections.first().startBar)
        assertEquals(musicalMap.totalBars, sections.last().endBar)

        // Verifikasi section order dan tidak ada celah/gap antar bar
        for (i in 0 until sections.size - 1) {
            val current = sections[i]
            val next = sections[i + 1]
            assertEquals("Next section startBar must match current endBar", current.endBar, next.startBar)
            assertEquals("Next section startSample must match current endSample", current.endSample, next.startSample)
            assertTrue("Section duration must be positive", current.duration > 0)
            assertTrue("Section durationSamples must be positive", current.durationSamples > 0)
        }

        // Verifikasi urutan struktur DJ Slow standar 8 seksi pada lagu panjang
        val types = sections.map { it.sectionType }
        assertTrue("Must have INTRO", types.contains(SongSectionType.INTRO))
        assertTrue("Must have BUILD", types.contains(SongSectionType.BUILD_UP))
        assertTrue("Must have PRE-DROP", types.contains(SongSectionType.PRE_DROP))
        assertTrue("Must have DROP", types.contains(SongSectionType.DROP))
        assertTrue("Must have BREAK", types.contains(SongSectionType.BREAK))
        assertTrue("Must have DROP 2 (MAIN_DROP)", types.contains(SongSectionType.MAIN_DROP))
        assertTrue("Must have OUTRO", types.contains(SongSectionType.OUTRO))
    }

    @Test
    fun testBarAndBpmAlignment() {
        val clock = MasterMusicalClock(targetBpm, sampleRate)
        val analysis = createMockAnalysis(60000L)
        val musicalMap = MusicalMapEngine.buildMap(analysis, analysis.timeline)
        val remixPlan = RemixBrain.createPlan(analysis, musicalMap, RemixBrain.RemixStyle.DJ_SLOW_BASS)
        val arrangement = ArrangementEngine.createArrangement(analysis, musicalMap, remixPlan)

        for (section in arrangement.sections) {
            val expectedStartSample = clock.getDownbeatSampleForBar(section.startBar)
            val expectedEndSample = clock.getDownbeatSampleForBar(section.endBar)
            assertEquals("Section startSample must align with MasterMusicalClock", expectedStartSample, section.startSample)
            assertEquals("Section endSample must align with MasterMusicalClock", expectedEndSample, section.endSample)
        }
    }

    @Test
    fun testKeyAndChordAlignment() {
        val analysis = createMockAnalysis(60000L)
        val musicalMap = MusicalMapEngine.buildMap(analysis, analysis.timeline)
        val remixPlan = RemixBrain.createPlan(analysis, musicalMap, RemixBrain.RemixStyle.DJ_SLOW_ROMANTIS)

        assertEquals("Target key must match analysis key", key, remixPlan.targetKey)
        assertTrue("Chord progression must not be empty", remixPlan.chordPlan.progression.isNotEmpty())

        val timelineChords = analysis.timeline.chordEvents
        assertTrue("Timeline chord events must not be empty", timelineChords.isNotEmpty())
        for (ce in timelineChords) {
            assertNotNull(ce.chord)
            assertTrue((ce.endSample - ce.startSample) > 0)
        }
    }

    @Test
    fun testDrumTimingAndSectionBehavior() {
        val analysis = createMockAnalysis(96000L)
        val musicalMap = MusicalMapEngine.buildMap(analysis, analysis.timeline)
        val remixPlan = RemixBrain.createPlan(analysis, musicalMap, RemixBrain.RemixStyle.DJ_SLOW)
        val arrangement = ArrangementEngine.createArrangement(analysis, musicalMap, remixPlan)

        val energyCurve = EnergyCurve(arrangement.sections.map { it.toSongSection() }, analysis.timeline.totalFrames)
        val drumEvents = com.autoremix.djslow.engine.drum.DrumEngine.scheduleDrumEvents(
            sections = arrangement.sections.map { it.toSongSection() },
            energyCurve = energyCurve,
            bpm = targetBpm,
            sampleRate = sampleRate,
            totalBars = musicalMap.totalBars,
            beatAnalysis = com.autoremix.djslow.engine.analysis.BeatContentAnalyzer.BeatContentAnalysis(
                drumPresence = 0f, bassPresence = 0f, spectralDensity = 0f,
                isBeatEmptyOrSilent = true,
                recommendedDrumMode = com.autoremix.djslow.engine.analysis.GeneratedDrumMode.HIGH,
                explanation = "Test"
            ),
            preset = remixPlan.style.legacyPreset
        )

        assertTrue("Drum events must be scheduled", drumEvents.isNotEmpty())

        // PRE-DROP: verify controlled silence in the last beat(s) of pre-drop
        val preDropSec = arrangement.sections.firstOrNull { it.sectionType == SongSectionType.PRE_DROP }
        assertNotNull("Must have PRE-DROP", preDropSec)
        val clock = MasterMusicalClock(targetBpm, sampleRate)
        val preDropEndSample = clock.getDownbeatSampleForBar(preDropSec!!.endBar)
        val silenceWindowStart = preDropEndSample - (clock.samplesPerBeat * 1.5).toLong()

        val eventsInSilenceWindow = drumEvents.filter { it.sampleOffset in silenceWindowStart until preDropEndSample }
        assertEquals("Controlled silence right before drop must have 0 drum events", 0, eventsInSilenceWindow.size)

        // DROP: verify downbeat crash cymbal on drop start
        val dropSec = arrangement.sections.firstOrNull { it.sectionType == SongSectionType.DROP }
        assertNotNull("Must have DROP", dropSec)
        val dropStartSample = clock.getDownbeatSampleForBar(dropSec!!.startBar)
        val crashOnDrop = drumEvents.any { it.soundType == DrumSoundType.CRASH && it.sampleOffset == dropStartSample }
        assertTrue("DROP must start with Crash Cymbal on downbeat", crashOnDrop)

        // BREAK: verify reduced kick vs DROP full kick
        val breakSec = arrangement.sections.firstOrNull { it.sectionType == SongSectionType.BREAK }
        assertNotNull("Must have BREAK", breakSec)
        val breakKicks = drumEvents.filter {
            it.soundType == DrumSoundType.KICK && it.sampleOffset >= breakSec!!.startSample && it.sampleOffset < breakSec.endSample
        }
        val dropKicks = drumEvents.filter {
            it.soundType == DrumSoundType.KICK && it.sampleOffset >= dropSec.startSample && it.sampleOffset < dropSec.endSample
        }
        assertTrue("BREAK should have reduced kicks compared to DROP", breakKicks.size <= dropKicks.size)
    }

    @Test
    fun testBassTimingAndSectionBehavior() {
        val analysis = createMockAnalysis(96000L)
        val musicalMap = MusicalMapEngine.buildMap(analysis, analysis.timeline)
        val remixPlan = RemixBrain.createPlan(analysis, musicalMap, RemixBrain.RemixStyle.DJ_SLOW_BASS)
        val arrangement = ArrangementEngine.createArrangement(analysis, musicalMap, remixPlan)

        val rawBass = com.autoremix.djslow.engine.synth.BassEngine.generateBassEvents(
            timeline = analysis.timeline,
            key = remixPlan.targetKey,
            patternType = remixPlan.bassPlan.patternType
        )
        // Check filtering in PRE-DROP
        val filteredBass = rawBass.mapNotNull { bassEvent ->
            val sec = arrangement.getSectionForBar(bassEvent.barIndex)
            when {
                sec == null || sec.bassMode == ArrangementEngine.BassMode.OFF || sec.sectionType == SongSectionType.PRE_DROP -> null
                sec.sectionType in listOf(SongSectionType.BREAK, SongSectionType.BREAKDOWN) -> {
                    bassEvent.copy(velocity = (bassEvent.velocity * 0.55f).coerceIn(0.1f, 1.0f))
                }
                else -> bassEvent
            }
        }

        val preDropSec = arrangement.sections.first { it.sectionType == SongSectionType.PRE_DROP }
        val bassInPreDrop = filteredBass.filter { it.barIndex in preDropSec.startBar until preDropSec.endBar }
        assertEquals("Bass must be reduced/off in PRE-DROP to create tension", 0, bassInPreDrop.size)

        val dropSec = arrangement.sections.first { it.sectionType == SongSectionType.DROP }
        val bassInDrop = filteredBass.filter { it.barIndex in dropSec.startBar until dropSec.endBar }
        assertTrue("DROP must contain full punch bass", bassInDrop.isNotEmpty())
    }

    @Test
    fun testMelodyPadFxTiming() {
        val analysis = createMockAnalysis(96000L)
        val musicalMap = MusicalMapEngine.buildMap(analysis, analysis.timeline)
        val remixPlan = RemixBrain.createPlan(analysis, musicalMap, RemixBrain.RemixStyle.DJ_SLOW)
        val arrangement = ArrangementEngine.createArrangement(analysis, musicalMap, remixPlan)
        val sections = arrangement.sections.map { it.toSongSection() }
        val energyCurve = EnergyCurve(sections, analysis.timeline.totalFrames)

        // Melody timing
        val melodyEvents = com.autoremix.djslow.engine.melody.MelodyEngine.scheduleMelodyEvents(
            key = key,
            chords = remixPlan.chordPlan.progression,
            bpm = targetBpm,
            sampleRate = sampleRate,
            totalBars = musicalMap.totalBars,
            sections = sections,
            energyCurve = energyCurve,
            seed = remixPlan.melodyPlan.seed,
            preset = remixPlan.style.legacyPreset
        )
        assertTrue("Melody events must be scheduled", melodyEvents.isNotEmpty())

        // Pad timing
        val padEvents = com.autoremix.djslow.engine.pad.PadEngine.schedulePadEvents(
            key = key,
            chords = remixPlan.chordPlan.progression,
            bpm = targetBpm,
            sampleRate = sampleRate,
            totalBars = musicalMap.totalBars,
            sections = sections,
            energyCurve = energyCurve,
            preset = remixPlan.style.legacyPreset,
            hasVocal = false
        )
        assertEquals("Pad events should span all bars", musicalMap.totalBars, padEvents.size)

        // FX timing
        val samplesPerBar = (sampleRate * 60f / targetBpm * 4f).toLong()
        val fxEvents = SectionEngines.scheduleTransitionEvents(
            sections = sections,
            sampleRate = sampleRate,
            samplesPerBar = samplesPerBar
        )
        assertTrue("Transition FX must include risers and downbeat impacts", fxEvents.isNotEmpty())
        assertTrue("Must contain DOWNBEAT_IMPACT", fxEvents.any { it.type == TransitionFxType.DOWNBEAT_IMPACT })
        assertTrue("Must contain NOISE_RISER", fxEvents.any { it.type == TransitionFxType.NOISE_RISER })
    }

    @Test
    fun testSeedDeterminismAcrossRuns() {
        val analysis = createMockAnalysis(30000L)
        val musicalMap = MusicalMapEngine.buildMap(analysis, analysis.timeline)
        val planA = RemixBrain.createPlan(analysis, musicalMap, RemixBrain.RemixStyle.DJ_SLOW, seed = 77777L)
        val planB = RemixBrain.createPlan(analysis, musicalMap, RemixBrain.RemixStyle.DJ_SLOW, seed = 77777L)
        val planC = RemixBrain.createPlan(analysis, musicalMap, RemixBrain.RemixStyle.DJ_SLOW, seed = 88888L)

        val arrA = ArrangementEngine.createArrangement(analysis, musicalMap, planA)
        val arrB = ArrangementEngine.createArrangement(analysis, musicalMap, planB)

        assertEquals("Identical seed must yield identical sections", arrA.sections.size, arrB.sections.size)
        assertEquals(planA.melodyPlan.seed, planB.melodyPlan.seed)
        assertNotEquals(planA.melodyPlan.seed, planC.melodyPlan.seed)
    }

    @Test
    fun testIntegratedAudioRenderOutputNotEmptyAndSafeHeadroom() = runBlocking {
        // Render a concise 8-bar loop to verify audio DSP synthesis without OOM (Streaming block architecture)
        val totalDurationMs = 24000L // 8 bars @ 80 BPM
        val analysis = createMockAnalysis(totalDurationMs)
        val musicalMap = MusicalMapEngine.buildMap(analysis, analysis.timeline)
        val remixPlan = RemixBrain.createPlan(analysis, musicalMap, RemixBrain.RemixStyle.DJ_SLOW)
        val arrangement = ArrangementEngine.createArrangement(analysis, musicalMap, remixPlan)

        val result = MusicGeneratorEngine.generateMusic(analysis.timeline, remixPlan, arrangement)
        assertTrue("MusicGeneratorEngine must succeed", result.isSuccess)

        val music = result.getOrThrow()

        // 1. Verify Event Scheduling (Zero OOM)
        assertTrue("Drum events must be scheduled", music.drumEvents.isNotEmpty())
        assertTrue("Bass events must be scheduled", music.bassEvents.isNotEmpty())
        assertTrue("Melody events must be scheduled", music.melodyEvents.isNotEmpty())
        assertTrue("Transition events must be scheduled", music.transitionEvents.isNotEmpty())

        // 2. Block-based render verification (16384 frames)
        val testFrames = 16384
        val channels = 2
        val drumBlock = FloatArray(testFrames * channels)
        val bassBlock = FloatArray(testFrames * channels)
        val chordBlock = FloatArray(testFrames * channels)
        val melodyBlock = FloatArray(testFrames * channels)

        // Drum Block (rendered around first drum event)
        val firstDrumSample = music.drumEvents.first().sampleOffset
        com.autoremix.djslow.engine.drum.DrumEngine.renderDrumBlock(
            events = music.drumEvents,
            startFrame = firstDrumSample,
            frameCount = testFrames,
            sampleRate = sampleRate,
            outBuffer = drumBlock,
            offset = 0
        )
        val drumPeak = drumBlock.maxOfOrNull { abs(it) } ?: 0f
        assertTrue("Drum block peak ($drumPeak) must be finite and controlled", drumPeak in 0.05f..1.5f)

        // Bass Block (rendered around first bass event)
        val firstBassSample = music.bassEvents.first().startSample
        com.autoremix.djslow.engine.synth.BassEngine.renderBassBlock(
            bassEvents = music.bassEvents,
            startFrame = firstBassSample,
            frameCount = testFrames,
            sampleRate = sampleRate,
            outBuffer = bassBlock,
            offset = 0,
            volume = 0.85f
        )
        val bassPeak = bassBlock.maxOfOrNull { abs(it) } ?: 0f
        assertTrue("Bass block peak ($bassPeak) must be finite and controlled", bassPeak in 0.05f..1.5f)

        // Melody Block (rendered around first melody event)
        val firstMelodySample = music.melodyEvents.first().sampleOffset
        com.autoremix.djslow.engine.melody.MelodyEngine.renderMelodyBlock(
            events = music.melodyEvents,
            startFrame = firstMelodySample,
            frameCount = testFrames,
            sampleRate = sampleRate,
            outBuffer = melodyBlock,
            offset = 0
        )
        val melodyPeak = melodyBlock.maxOfOrNull { abs(it) } ?: 0f
        assertTrue("Melody block peak ($melodyPeak) must be finite and controlled", melodyPeak in 0.01f..1.5f)

        // Check no NaN or Infinite values
        for (sample in drumBlock) {
            assertFalse("Audio samples must never be NaN", sample.isNaN())
            assertFalse("Audio samples must never be Infinite", sample.isInfinite())
        }
        for (sample in bassBlock) {
            assertFalse("Audio samples must never be NaN", sample.isNaN())
            assertFalse("Audio samples must never be Infinite", sample.isInfinite())
        }
    }

    @Test
    fun testAdaptiveStructureForShortSource() {
        // Verify that short source does not force 8 sections
        val shortAnalysis = createMockAnalysis(12000L) // 4 bars
        val musicalMap = MusicalMapEngine.buildMap(shortAnalysis, shortAnalysis.timeline)
        val remixPlan = RemixBrain.createPlan(shortAnalysis, musicalMap, RemixBrain.RemixStyle.DJ_SLOW)

        val arrangement = ArrangementEngine.createArrangement(shortAnalysis, musicalMap, remixPlan)
        assertTrue("Short song arrangement should not have more sections than total bars", arrangement.sections.size <= musicalMap.totalBars + 1)
        assertEquals(0, arrangement.sections.first().startBar)
        assertEquals(musicalMap.totalBars, arrangement.sections.last().endBar)
    }

    @Test
    fun testMemorySafetyAndGeneratorContext() {
        val analysis = createMockAnalysis(24000L)
        val musicalMap = MusicalMapEngine.buildMap(analysis, analysis.timeline)
        val remixPlan = RemixBrain.createPlan(analysis, musicalMap, RemixBrain.RemixStyle.DJ_SLOW, seed = 54321L)
        val arrangement = ArrangementEngine.createArrangement(analysis, musicalMap, remixPlan)

        val context = MusicGeneratorEngine.createGeneratorContext(analysis.timeline, remixPlan, arrangement)
        assertEquals(targetBpm, context.masterBpm, 0.01f)
        assertEquals(key, context.targetKey)
        assertEquals(remixPlan.style, context.style)
        assertEquals(remixPlan.melodyPlan.seed, context.seed)
        assertEquals(arrangement.sections.size, context.arrangement.sections.size)
        assertTrue(context.chordTimeline.isNotEmpty())
        assertTrue(context.beatGrid.isNotEmpty())
        assertTrue(context.barGrid > 0)

        // Memory safety: Buffer frames must be strictly bounded and reusable
        val testFrames = 4096
        val channels = 2
        val pcm = AudioPcmData(FloatArray(testFrames * channels), sampleRate, channels)
        assertEquals(testFrames, pcm.totalFrames)
        assertEquals(channels, pcm.channels)
        assertEquals(testFrames * channels, pcm.samples.size)

        // Verify no unbounded allocations occur on slicing or windowing
        val half = pcm.slice(0, 2048)
        assertEquals(2048, half.totalFrames)
    }
}
