package com.autoremix.djslow.engine

import com.autoremix.djslow.engine.arrangement.AutoArranger
import com.autoremix.djslow.engine.arrangement.AutoDjPreset
import com.autoremix.djslow.engine.mix.KickBassEngine
import com.autoremix.djslow.engine.mix.MixEngine
import com.autoremix.djslow.engine.mix.MixTrackSettings
import com.autoremix.djslow.engine.music.ChordEngine
import com.autoremix.djslow.engine.music.MusicKey
import com.autoremix.djslow.engine.music.MusicMode
import com.autoremix.djslow.engine.music.PitchClass
import com.autoremix.djslow.engine.pcm.AudioPcmData
import com.autoremix.djslow.engine.timeline.MasterTimeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrangementEngineTest {

    private val sampleRate = 44100
    private val bpm = 80.0f
    private val key = MusicKey(PitchClass.A, MusicMode.MINOR)

    @Test
    fun testAutoArranger_generatesCompletePlan() {
        val totalDurationMs = 60000L // 1 menit = ~20 bar @ 80 bpm
        val timeline = MasterTimeline.build(bpm = bpm, totalDurationMs = totalDurationMs)
        val chords = ChordEngine.getDefaultProgressionForKey(key)

        val plan = AutoArranger.arrange(
            vocalPcm = null,
            beatPcm = null,
            timeline = timeline,
            key = key,
            chords = chords,
            preset = AutoDjPreset.DJ_SLOW,
            melodySeed = 12345L
        )

        assertNotNull(plan)
        assertTrue("Sections should not be empty", plan.sections.isNotEmpty())
        assertTrue("Drum events should be scheduled", plan.drumEvents.isNotEmpty())
        assertTrue("Melody events should be scheduled", plan.melodyEvents.isNotEmpty())
        assertTrue("Pad events should be scheduled", plan.padEvents.isNotEmpty())
        assertTrue("Energy curve should have sections", plan.energyCurve.sections.isNotEmpty())
        assertEquals(AutoDjPreset.DJ_SLOW, plan.preset)
        assertEquals(12345L, plan.melodySeed)
    }

    @Test
    fun testMelodyEngine_deterministicWithSeed() {
        val totalDurationMs = 30000L
        val timeline = MasterTimeline.build(bpm = bpm, totalDurationMs = totalDurationMs)
        val chords = ChordEngine.getDefaultProgressionForKey(key)

        val planA = AutoArranger.arrange(null, null, timeline, key, chords, AutoDjPreset.DJ_SLOW, 9999L)
        val planB = AutoArranger.arrange(null, null, timeline, key, chords, AutoDjPreset.DJ_SLOW, 9999L)

        assertEquals("Same seed must produce identical count of melody events", planA.melodyEvents.size, planB.melodyEvents.size)
        for (i in planA.melodyEvents.indices) {
            assertEquals(planA.melodyEvents[i].pitchHz, planB.melodyEvents[i].pitchHz, 0.001f)
            assertEquals(planA.melodyEvents[i].sampleOffset, planB.melodyEvents[i].sampleOffset)
        }
    }

    @Test
    fun testKickBassEngine_sidechainDuckingAttenuatesBass() {
        val totalFrames = 44100 // 1 detik
        val channels = 2
        val samples = FloatArray(totalFrames * channels) { 0.8f } // Bass konstan 0.8
        val bassPcm = AudioPcmData(samples, sampleRate, channels)

        val kickEvents = listOf(
            com.autoremix.djslow.engine.drum.DrumEvent(
                soundType = com.autoremix.djslow.engine.drum.DrumSoundType.KICK,
                sampleOffset = 1000L,
                durationSamples = 4410,
                velocity = 1.0f
            )
        )

        val sidechained = KickBassEngine.applySidechainDucking(
            bassPcm = bassPcm,
            drumEvents = kickEvents,
            sampleRate = sampleRate
        )

        assertNotNull(sidechained)
        assertEquals(bassPcm.totalFrames, sidechained.totalFrames)
        // Offset 1000 memiliki kick, maka indeks 2000 (1000 * 2) harus ter-duck
        val sampleAtKick = sidechained.samples[2000]
        assertTrue("Ducked bass sample ($sampleAtKick) must be lower than original (0.8)", sampleAtKick < 0.79f)
    }

    @Test
    fun testMultiTrackMixEngine_safeHeadroomLimiter() {
        val totalFrames = 44100
        val channels = 2
        // Buat 4 track dengan amplitudo tinggi
        val vocal = AudioPcmData(FloatArray(totalFrames * channels) { 0.7f }, sampleRate, channels)
        val drum = AudioPcmData(FloatArray(totalFrames * channels) { 0.7f }, sampleRate, channels)
        val bass = AudioPcmData(FloatArray(totalFrames * channels) { 0.7f }, sampleRate, channels)
        val chord = AudioPcmData(FloatArray(totalFrames * channels) { 0.7f }, sampleRate, channels)

        val params = MixEngine.MixParams(
            vocalSettings = MixTrackSettings(volume = 1.0f),
            drumSettings = MixTrackSettings(volume = 1.0f),
            bassSettings = MixTrackSettings(volume = 1.0f),
            chordSettings = MixTrackSettings(volume = 1.0f),
            masterGain = 1.0f,
            isAutoMixEnabled = true
        )

        val mixResult = MixEngine.mix(
            vocalPcm = vocal,
            beatPcm = null,
            chordPcm = chord,
            bassPcm = bass,
            drumPcm = drum,
            melodyPcm = null,
            padPcm = null,
            transitionPcm = null,
            params = params
        )

        assertTrue("Mix must succeed", mixResult.isSuccess)
        val mix = mixResult.getOrThrow()
        val peak = mix.calculatePeak()
        assertTrue("Master mix peak ($peak) must never exceed safe headroom (0.95)", peak <= 0.95f)
        assertFalse("Mixed audio should not be completely silent", mix.isSilent())
    }
}
