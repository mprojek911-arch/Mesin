package com.autoremix.djslow.engine

import com.autoremix.djslow.engine.dsp.LoudnessMeter
import com.autoremix.djslow.engine.mastering.AutoMasteringEngine
import com.autoremix.djslow.engine.mastering.MasteringPreset
import com.autoremix.djslow.engine.mix.BassProcessor
import com.autoremix.djslow.engine.mix.BusSettings
import com.autoremix.djslow.engine.mix.BusType
import com.autoremix.djslow.engine.mix.DrumProcessor
import com.autoremix.djslow.engine.mix.MixEngine
import com.autoremix.djslow.engine.mix.VocalDucker
import com.autoremix.djslow.engine.mix.VocalProcessor
import com.autoremix.djslow.engine.pcm.AudioPcmData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class MasteringAndMixBusTest {

    private val sampleRate = 44100
    private val channels = 2

    private fun generateSineWave(freqHz: Float, durationSec: Float, amplitude: Float): AudioPcmData {
        val totalFrames = (sampleRate * durationSec).toInt()
        val samples = FloatArray(totalFrames * channels)
        for (i in 0 until totalFrames) {
            val s = (sin(2.0 * Math.PI * freqHz * i / sampleRate) * amplitude).toFloat()
            samples[i * 2] = s
            samples[i * 2 + 1] = s
        }
        return AudioPcmData(samples, sampleRate, channels)
    }

    @Test
    fun testVocalProcessor_hpfRemovesLowFrequencyRumble() {
        // 40 Hz rumble vokal (harus teratenuasi kuat oleh HPF 85Hz)
        val rumble40Hz = generateSineWave(40.0f, 0.5f, 0.8f)
        val processedRumble = VocalProcessor.process(rumble40Hz)
        val rumblePeak = processedRumble.calculatePeak()

        assertTrue("40 Hz low rumble must be attenuated significantly by HPF (was 0.8, now $rumblePeak)", rumblePeak < 0.45f)

        // 1000 Hz vocal presence (harus tetap lolos)
        val voice1kHz = generateSineWave(1000.0f, 0.5f, 0.5f)
        val processedVoice = VocalProcessor.process(voice1kHz)
        val voicePeak = processedVoice.calculatePeak()

        assertTrue("1 kHz vocal presence must pass through cleanly ($voicePeak)", voicePeak > 0.35f)
    }

    @Test
    fun testVocalDucker_sidechainDucksMusicWhenVocalPresent() {
        val durationSec = 1.0f
        val totalFrames = (sampleRate * durationSec).toInt()

        // Vokal aktif di tengah (frame 10000 - 30000)
        val vocalSamples = FloatArray(totalFrames * channels)
        for (i in 10000 until 30000) {
            vocalSamples[i * 2] = 0.8f
            vocalSamples[i * 2 + 1] = 0.8f
        }
        val vocalPcm = AudioPcmData(vocalSamples, sampleRate, channels)

        // Musik konstan 0.6f
        val musicSamples = FloatArray(totalFrames * channels) { 0.6f }
        val musicPcm = AudioPcmData(musicSamples, sampleRate, channels)

        val ducked = VocalDucker.applyVocalDucking(musicPcm, vocalPcm, duckingDepthDb = -3.0f)

        assertEquals(musicPcm.totalFrames, ducked.totalFrames)

        // Di awal saat vokal senyap, musik tetap penuh (0.6f)
        assertEquals(0.6f, ducked.samples[100], 0.01f)

        // Di tengah saat vokal berbunyi kencang, musik harus ter-duck ke bawah 0.55f
        val duckedSample = ducked.samples[20000 * 2]
        assertTrue("Music sample during active vocal must be ducked ($duckedSample < 0.58)", duckedSample < 0.58f)
    }

    @Test
    fun testBassProcessor_hpf30HzAndMonoSub() {
        // 15 Hz inaudible sub rumble (harus terbuang oleh HPF 30Hz)
        val sub15Hz = generateSineWave(15.0f, 0.5f, 0.9f)
        val processedSub = BassProcessor.process(sub15Hz)
        val peak = processedSub.calculatePeak()

        assertTrue("15 Hz sub rumble must be sharply reduced by HPF 30Hz ($peak < 0.5)", peak < 0.50f)
    }

    @Test
    fun testDrumProcessor_punchLimiterPreservesHeadroom() {
        // Drum signal keras
        val loudDrum = generateSineWave(80.0f, 0.5f, 1.2f)
        val processedDrum = DrumProcessor.process(loudDrum)
        val peak = processedDrum.calculatePeak()

        assertTrue("Drum processor transient limiter must control excessive peaks ($peak <= 0.95)", peak <= 0.95f)
    }

    @Test
    fun testAutoMasteringEngine_strictPeakCeilingAndNoClipping() {
        val loudMusic = generateSineWave(440.0f, 1.0f, 1.0f)

        for (preset in MasteringPreset.values()) {
            val masteringResult = AutoMasteringEngine.master(loudMusic, preset)
            assertTrue("Mastering must succeed for preset ${preset.name}", masteringResult.isSuccess)

            val result = masteringResult.getOrThrow()
            val mastered = result.masteredPcm
            assertFalse("Mastered audio must not be silent", mastered.isSilent())
            assertFalse("Mastered audio must not have NaN/Inf", mastered.hasInvalidValues())
            assertFalse("Loudness report should not flag hard clipping", result.report.isClipping)

            val peak = mastered.calculatePeak()
            assertTrue("Mastered peak ($peak) must stay within digital ceiling <= 0.96f", peak <= 0.96f)
        }
    }

    @Test
    fun testLoudnessMeter_accuratelyMeasuresLufsAndTruePeak() {
        val testPcm = generateSineWave(1000.0f, 1.5f, 0.5f)
        val report = LoudnessMeter.analyze(testPcm)

        assertNotNull(report)
        assertTrue("LUFS Integrated must be reasonable (-30 to -6 LUFS)", report.lufsIntegrated in -30.0f..-6.0f)
        assertTrue("True Peak must be <= 0 dBTP for 0.5 amplitude sine", report.truePeakDbtp <= 0.0f)
        assertFalse("Audio should not be clipping", report.isClipping)
        assertTrue("Peak dBFS should be around -6 dBFS for 0.5 amplitude", report.peakDbfs in -7.5f..-5.0f)
    }

    @Test
    fun testMixEngine_6BusSummingAndMuteControl() {
        val totalFrames = 22050 // 0.5 detik
        val vocal = AudioPcmData(FloatArray(totalFrames * channels) { 0.5f }, sampleRate, channels)
        val drum = AudioPcmData(FloatArray(totalFrames * channels) { 0.5f }, sampleRate, channels)

        // Uji mute drum bus
        val params = MixEngine.MixParams(
            drumBusSettings = BusSettings(busType = BusType.DRUM_BUS, isMuted = true),
            masterGain = 1.0f
        )

        val mixResult = MixEngine.mix(
            vocalPcm = vocal,
            beatPcm = null,
            chordPcm = null,
            bassPcm = null,
            drumPcm = drum,
            melodyPcm = null,
            padPcm = null,
            transitionPcm = null,
            params = params
        )

        assertTrue("Mix must succeed", mixResult.isSuccess)
        val mixed = mixResult.getOrThrow()
        assertFalse("Mixed audio should not be silent (vocal still active)", mixed.isSilent())
        assertTrue("Peak amplitude should be within safe headroom", mixed.calculatePeak() <= 0.95f)
    }

    @Test
    fun testMasterEngine_masterAudioFullPipeline() {
        val testMusic = generateSineWave(440.0f, 1.0f, 0.9f)
        val masterResult = kotlinx.coroutines.runBlocking {
            com.autoremix.djslow.engine.core.MasterEngine.masterAudio(
                inputPcm = testMusic,
                preset = MasteringPreset.DJ_SLOW,
                inPlace = false
            )
        }

        assertTrue("MasterEngine audio mastering must succeed", masterResult.isSuccess)
        val output = masterResult.getOrThrow()
        assertNotNull("Mastered PCM must not be null", output.pcmData)
        assertFalse("Mastered PCM must not be silent", output.pcmData.isSilent())
        assertFalse("Mastered PCM must not have NaN/Inf values", output.pcmData.hasInvalidValues())
        assertTrue("Mastered True Peak should be <= -0.2 dBTP", output.loudnessReport.truePeakDbtp <= -0.2f)
        assertFalse("Output must not flag clipping", output.loudnessReport.isClipping)
        assertTrue("Mastered LUFS should be within reasonable range", output.loudnessReport.lufsIntegrated in -24.0f..-8.0f)
    }

    @Test
    fun testMasterEngine_inPlaceProcessingSavesAllocation() {
        val testMusic = generateSineWave(220.0f, 0.5f, 0.8f)
        val initialFirstSample = testMusic.samples[0]
        val masterResult = kotlinx.coroutines.runBlocking {
            com.autoremix.djslow.engine.core.MasterEngine.masterAudio(
                inputPcm = testMusic,
                preset = MasteringPreset.BASS_STRONG,
                inPlace = true
            )
        }

        assertTrue("In-place mastering must succeed", masterResult.isSuccess)
        val output = masterResult.getOrThrow()
        assertEquals(testMusic.samples.size, output.pcmData.samples.size)
        assertTrue("Mastered peak should stay within digital limits", output.pcmData.calculatePeak() <= 0.96f)
    }

    @Test
    fun testOutputEngine_memoryStatusCheck() {
        val memStatus = com.autoremix.djslow.engine.core.OutputEngine.getMemoryStatus()
        assertNotNull(memStatus)
        assertTrue("Total max memory must be positive", memStatus.maxMb > 0)
        assertTrue("Used memory must be non-negative", memStatus.usedMb >= 0)
    }
}
