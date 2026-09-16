package com.autoremix.djslow.engine

import com.autoremix.djslow.engine.core.MusicUnderstandingEngine
import com.autoremix.djslow.engine.core.MusicalMapEngine
import com.autoremix.djslow.engine.core.MusicalMapEngine.EnergyCategory
import com.autoremix.djslow.engine.core.RemixBrain
import com.autoremix.djslow.engine.core.RemixBrain.EnergyPreference
import com.autoremix.djslow.engine.core.RemixBrain.FocusPreference
import com.autoremix.djslow.engine.core.RemixBrain.RemixStyle
import com.autoremix.djslow.engine.music.Chord
import com.autoremix.djslow.engine.music.ChordType
import com.autoremix.djslow.engine.music.MusicKey
import com.autoremix.djslow.engine.music.MusicMode
import com.autoremix.djslow.engine.music.PitchClass
import com.autoremix.djslow.engine.structure.EnergyCurve
import com.autoremix.djslow.engine.structure.SongSection
import com.autoremix.djslow.engine.structure.SongSectionType
import com.autoremix.djslow.engine.timeline.MasterMusicalClock
import com.autoremix.djslow.engine.timeline.MasterTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MusicalMapAndRemixBrainTest {

    private val sampleRate = 44100
    private val bpm = 80.0f
    private val key = MusicKey(PitchClass.A, MusicMode.MINOR)
    private lateinit var mockTimeline: MasterTimeline
    private lateinit var mockAnalysis: MusicUnderstandingEngine.MusicAnalysis

    @Before
    fun setUp() {
        val totalDurationMs = 60000L // 1 menit = 20 bar @ 80 bpm
        mockTimeline = MasterTimeline.build(bpm = bpm, totalDurationMs = totalDurationMs)

        val chords = listOf(
            Chord(PitchClass.A, ChordType.MINOR),
            Chord(PitchClass.F, ChordType.MAJOR),
            Chord(PitchClass.C, ChordType.MAJOR),
            Chord(PitchClass.G, ChordType.MAJOR)
        )

        val sections = listOf(
            SongSection(SongSectionType.INTRO, 0, 4, 0L, mockTimeline.beatToSample(16f), 0L, mockTimeline.beatToMs(16f), targetEnergy = 0.25f),
            SongSection(SongSectionType.BUILD_UP, 4, 8, mockTimeline.beatToSample(16f), mockTimeline.beatToSample(32f), mockTimeline.beatToMs(16f), mockTimeline.beatToMs(32f), targetEnergy = 0.75f),
            SongSection(SongSectionType.DROP, 8, 16, mockTimeline.beatToSample(32f), mockTimeline.beatToSample(64f), mockTimeline.beatToMs(32f), mockTimeline.beatToMs(64f), targetEnergy = 0.95f),
            SongSection(SongSectionType.OUTRO, 16, 20, mockTimeline.beatToSample(64f), mockTimeline.beatToSample(80f), mockTimeline.beatToMs(64f), mockTimeline.beatToMs(80f), targetEnergy = 0.20f)
        )

        val energyCurve = EnergyCurve(sections, mockTimeline.totalFrames)

        mockAnalysis = MusicUnderstandingEngine.MusicAnalysis(
            bpm = bpm,
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
            downbeats = listOf(0L, mockTimeline.beatToSample(4f), mockTimeline.beatToSample(8f)),
            vocalPhrases = listOf(
                MusicUnderstandingEngine.VocalPhrase(
                    index = 0,
                    startSample = mockTimeline.beatToSample(4f),
                    endSample = mockTimeline.beatToSample(12f),
                    startTimeMs = mockTimeline.beatToMs(4f),
                    endTimeMs = mockTimeline.beatToMs(12f),
                    averageRms = 0.35f
                )
            ),
            durationMs = mockTimeline.totalDurationMs,
            timeline = mockTimeline.copy(energyCurve = energyCurve),
            energyCurve = energyCurve
        )
    }

    // =========================================================================
    // 1. BAGIAN B: MUSICAL MAP CONSISTENCY
    // =========================================================================

    @Test
    fun testMusicalMap_consistency() {
        val musicalMap = MusicalMapEngine.buildMap(mockAnalysis)

        assertNotNull(musicalMap)
        assertEquals(bpm, musicalMap.bpm, 0.01f)
        assertEquals(key, musicalMap.key)
        assertEquals(mockTimeline.totalBars, musicalMap.totalBars)
        assertEquals(mockTimeline.totalBeats, musicalMap.totalBeats)
        assertEquals(mockTimeline.totalBars, musicalMap.barContexts.size)

        // Verifikasi konteks musikal per bar
        for (bar in musicalMap.barContexts) {
            assertTrue("Bar ${bar.barIndex} startSample must be < endSample", bar.startSample < bar.endSample)
            assertTrue("Bar ${bar.barIndex} startTimeMs must be < endTimeMs", bar.startTimeMs < bar.endTimeMs)
            assertEquals("Downbeat sample must align with bar start", bar.startSample, bar.downbeatSample)
            assertNotNull("Chord must be present", bar.chord)
            assertTrue("Energy must be between 0 and 1", bar.energyLevel in 0.0f..1.0f)
            assertNotNull("EnergyCategory must not be null", bar.energyCategory)
            assertFalse("Musical context narrative must not be empty", bar.musicalContext.isBlank())
        }

        // Verifikasi seksi Intro bar 0 (target energy 0.25f -> CALM)
        val bar0 = musicalMap.getBarAtIndex(0)
        assertNotNull(bar0)
        assertEquals(SongSectionType.INTRO, bar0!!.sectionType)
        assertEquals(EnergyCategory.CALM, bar0.energyCategory)

        // Verifikasi seksi Drop bar 10 (target energy 0.95f -> DROP)
        val bar10 = musicalMap.getBarAtIndex(10)
        assertNotNull(bar10)
        assertEquals(SongSectionType.DROP, bar10!!.sectionType)
        assertEquals(EnergyCategory.DROP, bar10.energyCategory)

        // Verifikasi aktivitas vokal pada bar 1 & 2 (beat 4 sampai 12)
        val bar1 = musicalMap.getBarAtIndex(1)
        assertNotNull(bar1)
        assertTrue("Bar 1 should have vocal phrase", bar1!!.hasVocalPhrase)
        assertTrue("Bar 1 vocal activity ratio should be > 0", bar1.vocalActivityRatio > 0.0f)

        // Verifikasi bar transisi (bar 3 adalah akhir Intro)
        val bar3 = musicalMap.getBarAtIndex(3)
        assertNotNull(bar3)
        assertTrue("Bar 3 should be marked as transition bar", bar3!!.isTransitionBar)
    }

    // =========================================================================
    // 2. BAGIAN C: SATU MASTER MUSICAL CLOCK
    // =========================================================================

    @Test
    fun testMusicalClock_singleMasterClockAccuracy() {
        val clock = MasterMusicalClock(bpm = 80.0f, sampleRate = sampleRate)

        // 80 BPM = 60/80 = 0.75 s per beat = 750 ms per beat
        assertEquals(750.0, clock.msPerBeat, 0.01)
        // 0.75 s * 44100 = 33075 samples per beat
        assertEquals(33075.0, clock.samplesPerBeat, 0.01)

        // Roundtrip konversi beat <-> sample
        val testBeat = 4.0f
        val sample = clock.beatToSample(testBeat)
        val recoveredBeat = clock.sampleToBeat(sample)
        assertEquals(testBeat, recoveredBeat, 0.01f)

        // Roundtrip konversi beat <-> ms
        val ms = clock.beatToMs(testBeat)
        val recoveredBeatFromMs = clock.msToBeat(ms)
        assertEquals(testBeat, recoveredBeatFromMs, 0.01f)

        // Bar and Downbeat calculation
        assertEquals(0, clock.getBarForBeat(3.9f))
        assertEquals(1, clock.getBarForBeat(4.0f))
        assertEquals(clock.beatToSample(4.0f), clock.getDownbeatSampleForBar(1))
    }

    // =========================================================================
    // 3. BAGIAN D: REMIX BRAIN GENERATES COMPLETE REMIX PLAN
    // =========================================================================

    @Test
    fun testRemixBrain_generatesAllRequiredSubPlans() {
        val musicalMap = MusicalMapEngine.buildMap(mockAnalysis)
        val plan = RemixBrain.createPlan(
            analysis = mockAnalysis,
            musicalMap = musicalMap,
            style = RemixStyle.DJ_SLOW,
            targetBpmOverride = 80.0f,
            energyPreference = EnergyPreference.MEDIUM,
            focusPreference = FocusPreference.BALANCED,
            seed = 42L
        )

        assertNotNull(plan)
        assertEquals(80.0f, plan.targetBpm, 0.01f)
        assertEquals(key, plan.targetKey)

        // 12 Sub-rencana FASE 2 - Bagian D
        assertNotNull("ArrangementPlan must exist", plan.arrangementPlan)
        assertNotNull("DrumPlan must exist", plan.drumPlan)
        assertNotNull("BassPlan must exist", plan.bassPlan)
        assertNotNull("ChordPlan must exist", plan.chordPlan)
        assertNotNull("MelodyPlan must exist", plan.melodyPlan)
        assertNotNull("PadPlan must exist", plan.padPlan)
        assertNotNull("FxPlan must exist", plan.fxPlan)
        assertNotNull("VocalPlan must exist", plan.vocalPlan)
        assertNotNull("MixPlan must exist", plan.mixPlan)
        assertNotNull("MasterPlan must exist", plan.masterPlan)

        assertTrue(plan.arrangementPlan.totalBars > 0)
        assertTrue(plan.drumPlan.kickVelocity > 0.0f)
        assertTrue(plan.bassPlan.subBoostGain > 0.0f)
        assertTrue(plan.chordPlan.progression.isNotEmpty())
        assertTrue(plan.planSummary.isNotEmpty())
    }

    // =========================================================================
    // 4. BAGIAN E: REMIX STYLE BEHAVIOR
    // =========================================================================

    @Test
    fun testRemixStyle_allSixStylesHaveDistinctMusicalParameters() {
        val musicalMap = MusicalMapEngine.buildMap(mockAnalysis)

        val styles = listOf(
            RemixStyle.DJ_SLOW,
            RemixStyle.DJ_SLOW_BASS,
            RemixStyle.DJ_SLOW_ROMANTIS,
            RemixStyle.DJ_SLOW_DARK,
            RemixStyle.DJ_SLOW_DEEP,
            RemixStyle.DJ_SLOW_PARTY
        )

        assertEquals("Must have exactly 6 DJ Slow styles", 6, styles.size)

        val plans = styles.map { style ->
            style to RemixBrain.createPlan(
                analysis = mockAnalysis,
                musicalMap = musicalMap,
                style = style,
                seed = 100L
            )
        }.toMap()

        // DJ SLOW BASS harus memiliki boost sub-bass lebih tinggi dari DJ SLOW ROMANTIS
        val bassPlan = plans[RemixStyle.DJ_SLOW_BASS]!!.bassPlan
        val romantisBassPlan = plans[RemixStyle.DJ_SLOW_ROMANTIS]!!.bassPlan
        assertTrue("DJ SLOW BASS subBoostGain must be > DJ SLOW ROMANTIS", bassPlan.subBoostGain > romantisBassPlan.subBoostGain)

        // DJ SLOW PARTY harus memiliki densitas drum lebih tinggi dari DJ SLOW ROMANTIS
        val partyDrum = plans[RemixStyle.DJ_SLOW_PARTY]!!.drumPlan
        val romantisDrum = plans[RemixStyle.DJ_SLOW_ROMANTIS]!!.drumPlan
        assertTrue("DJ SLOW PARTY drum density must be > DJ SLOW ROMANTIS", partyDrum.densityMultiplier > romantisDrum.densityMultiplier)

        // Target mastering LUFS berbeda antar style
        val partyMaster = plans[RemixStyle.DJ_SLOW_PARTY]!!.masterPlan
        val romantisMaster = plans[RemixStyle.DJ_SLOW_ROMANTIS]!!.masterPlan
        assertTrue("Party mix should be louder than romantic ballad", partyMaster.targetLufs > romantisMaster.targetLufs)
    }

    // =========================================================================
    // 5. BAGIAN F: ENERGY SYSTEM
    // =========================================================================

    @Test
    fun testEnergySystem_influencesElementsFromCalmToDrop() {
        val musicalMap = MusicalMapEngine.buildMap(mockAnalysis)

        val calmPlan = RemixBrain.createPlan(
            analysis = mockAnalysis,
            musicalMap = musicalMap,
            style = RemixStyle.DJ_SLOW,
            energyPreference = EnergyPreference.CALM,
            seed = 50L
        )

        val dropPlan = RemixBrain.createPlan(
            analysis = mockAnalysis,
            musicalMap = musicalMap,
            style = RemixStyle.DJ_SLOW,
            energyPreference = EnergyPreference.DROP,
            seed = 50L
        )

        // Drum kick & snare velocity
        assertTrue("DROP kick velocity must be >= CALM", dropPlan.drumPlan.kickVelocity > calmPlan.drumPlan.kickVelocity)
        assertTrue("DROP snare velocity must be >= CALM", dropPlan.drumPlan.snareVelocity > calmPlan.drumPlan.snareVelocity)

        // Bass sidechain ducking depth
        assertTrue("DROP bass ducking must be deeper than CALM", dropPlan.bassPlan.sidechainDuckAmount > calmPlan.bassPlan.sidechainDuckAmount)

        // Pad behavior
        assertFalse("CALM pad should not duck during drop", calmPlan.padPlan.duckDuringDrop)
        assertTrue("DROP pad should duck during drop", dropPlan.padPlan.duckDuringDrop)
    }

    // =========================================================================
    // 6. BAGIAN G: SEED SYSTEM DETERMINISM & CONTROLLED VARIATION
    // =========================================================================

    @Test
    fun testSeedDeterminism_sameSeedYieldsIdenticalPlan() {
        val musicalMap = MusicalMapEngine.buildMap(mockAnalysis)
        val seed = 777888L

        val plan1 = RemixBrain.createPlan(
            analysis = mockAnalysis,
            musicalMap = musicalMap,
            style = RemixStyle.DJ_SLOW,
            seed = seed
        )

        val plan2 = RemixBrain.createPlan(
            analysis = mockAnalysis,
            musicalMap = musicalMap,
            style = RemixStyle.DJ_SLOW,
            seed = seed
        )

        assertEquals("Same seed must produce identical targetBpm", plan1.targetBpm, plan2.targetBpm, 0.001f)
        assertEquals("Same seed must produce identical hihat variant", plan1.drumPlan.patternVariant, plan2.drumPlan.patternVariant)
        assertEquals("Same seed must produce identical melody seed", plan1.melodyPlan.seed, plan2.melodyPlan.seed)
        assertEquals("Same seed must produce identical bass syncopation", plan1.bassPlan.syncopationVariant, plan2.bassPlan.syncopationVariant)
        assertEquals("Same seed must produce identical planSummary", plan1.planSummary, plan2.planSummary)
    }

    @Test
    fun testSeedVariation_differentSeedMaintainsCoreWhileVaryingPatterns() {
        val musicalMap = MusicalMapEngine.buildMap(mockAnalysis)

        val planA = RemixBrain.createPlan(
            analysis = mockAnalysis,
            musicalMap = musicalMap,
            style = RemixStyle.DJ_SLOW,
            seed = 11111L
        )

        val planB = RemixBrain.createPlan(
            analysis = mockAnalysis,
            musicalMap = musicalMap,
            style = RemixStyle.DJ_SLOW,
            seed = 99999L
        )

        // Yang WAJIB DIPERTAHANKAN:
        assertEquals("Key must remain identical", planA.targetKey, planB.targetKey)
        assertEquals("Target BPM must remain identical", planA.targetBpm, planB.targetBpm, 0.001f)
        assertEquals("Style must remain identical", planA.style, planB.style)

        // Yang BERVARIASI secara terkontrol:
        assertNotEquals("Melody procedural seed must differ", planA.melodyPlan.seed, planB.melodyPlan.seed)
    }
}
