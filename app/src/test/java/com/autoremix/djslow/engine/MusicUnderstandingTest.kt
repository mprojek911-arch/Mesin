package com.autoremix.djslow.engine

import com.autoremix.djslow.engine.core.MusicUnderstandingEngine
import com.autoremix.djslow.engine.dsp.LoudnessMeter
import com.autoremix.djslow.engine.pcm.AudioPcmData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin

class MusicUnderstandingTest {

    private val sampleRate = 44100

    @Test
    fun testDetectVocalPhrases_distinguishesSilenceFromAudio() {
        val sampleRate = 44100
        val silenceSamples = FloatArray(sampleRate * 2) // 2 detik hening
        val silencePcm = AudioPcmData(silenceSamples, sampleRate, 2)

        val silentPhrases = MusicUnderstandingEngine.detectVocalPhrases(silencePcm)
        assertTrue("Silent audio should produce no vocal phrases", silentPhrases.isEmpty())

        // Buat sinyal audio dengan gelombang sinus 440 Hz (A4) yang memiliki energi cukup
        val toneSamples = FloatArray(sampleRate * 2)
        for (i in toneSamples.indices) {
            toneSamples[i] = (0.5f * sin(2.0 * Math.PI * 440.0 * (i / 2) / sampleRate)).toFloat()
        }
        val tonePcm = AudioPcmData(toneSamples, sampleRate, 2)
        val tonePhrases = MusicUnderstandingEngine.detectVocalPhrases(tonePcm)
        assertTrue("Tone audio should produce vocal phrases", tonePhrases.isNotEmpty())
    }

    @Test
    fun testLoudnessMeter_streamingSubBlocks() {
        // Uji LoudnessMeter dengan buffer 2 detik gelombang sinus
        val durationFrames = sampleRate * 2
        val samples = FloatArray(durationFrames * 2)
        for (f in 0 until durationFrames) {
            val s = (0.25f * sin(2.0 * Math.PI * 1000.0 * f / sampleRate)).toFloat()
            samples[f * 2] = s
            samples[f * 2 + 1] = s
        }
        val tonePcm = AudioPcmData(samples, sampleRate, 2)

        val report = LoudnessMeter.analyze(tonePcm)
        assertNotNull(report)
        assertTrue("Integrated LUFS should be measured", report.lufsIntegrated < 0.0f && report.lufsIntegrated > -60.0f)
        assertTrue("True peak should be calculated", report.truePeakDbtp < 0.0f)
        assertFalse("Proper tone should not clip", report.isClipping)
    }
}
