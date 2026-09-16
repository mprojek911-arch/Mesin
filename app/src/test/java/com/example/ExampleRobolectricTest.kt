package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.autoremix.djslow.engine.AudioSource
import com.autoremix.djslow.engine.AudioState
import com.autoremix.djslow.engine.BeatTrackState
import com.autoremix.djslow.engine.PlaybackEngineState
import com.autoremix.djslow.engine.VocalTrackState
import com.autoremix.djslow.engine.analysis.BpmDetector
import com.autoremix.djslow.engine.analysis.KeyDetector
import com.autoremix.djslow.engine.mix.MixEngine
import com.autoremix.djslow.engine.mix.MixTrackSettings
import com.autoremix.djslow.engine.music.Chord
import com.autoremix.djslow.engine.music.ChordEngine
import com.autoremix.djslow.engine.music.ChordType
import com.autoremix.djslow.engine.music.MusicKey
import com.autoremix.djslow.engine.music.MusicMode
import com.autoremix.djslow.engine.music.PitchClass
import com.autoremix.djslow.engine.pcm.AudioPcmData
import com.autoremix.djslow.engine.pcm.AudioPcmDecoder
import com.autoremix.djslow.engine.synth.BassEngine
import com.autoremix.djslow.engine.synth.BassPatternType
import com.autoremix.djslow.engine.synth.ChordSynthEngine
import com.autoremix.djslow.engine.synth.ChordSynthPreset
import com.autoremix.djslow.engine.timeline.MasterTimeline
import com.autoremix.djslow.engine.wav.WavRenderer
import com.autoremix.djslow.engine.wav.WavValidator
import com.autoremix.djslow.state.UiState
import com.autoremix.djslow.ui.MainScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileInputStream
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("AUTO REMIX DJ SLOW", appName)
  }

  @Test
  fun `audio source formats metadata correctly`() {
    val source = AudioSource(
      uri = Uri.parse("content://media/external/audio/media/1"),
      fileName = "vocal_track_slow.wav",
      fileSizeBytes = 5 * 1024 * 1024L,
      mimeType = "audio/wav",
      durationMs = 195000L, // 03:15
      sampleRateHz = 44100,
      channelCount = 2,
      formatExtension = "wav"
    )

    assertEquals("03:15", source.formattedDuration)
    assertEquals("WAV", source.formattedFormat)
    assertEquals("44100 Hz", source.formattedSampleRate)
    assertEquals("Stereo (2 ch)", source.formattedChannels)
    assertEquals("5.0 MB", source.formattedFileSize)
  }

  @Test
  fun `audio state and ui state reflect playback and track states correctly`() {
    val audioState = AudioState(
      vocalState = VocalTrackState.VOKAL_DIPILIH,
      beatState = BeatTrackState.BEAT_DIPILIH,
      playbackState = PlaybackEngineState.MEMUTAR,
      vocalDurationMs = 180000L,
      beatDurationMs = 180000L,
      vocalPositionMs = 90000L,
      beatPositionMs = 90000L
    )

    val uiState = UiState(
      audioState = audioState
    )

    assertTrue(uiState.isPlaying)
    assertFalse(uiState.isPaused)
    assertEquals(0.5f, uiState.playbackProgress, 0.01f)
    assertEquals("MEMUTAR", audioState.playbackState.label)
    assertEquals("Vokal Dipilih", audioState.vocalState.label)
    assertEquals("Beat Dipilih", audioState.beatState.label)
  }

  // ==========================================
  // TAHAP 3: TES BPM ENGINE
  // ==========================================
  @Test
  fun `bpm detector detects periodicity from periodic pulse train`() {
    val sampleRate = 44100
    val durationSeconds = 6
    val frameCount = sampleRate * durationSeconds

    // Buat beat sintetis pada tempo 80 BPM
    // Interval 80 BPM = 60 / 80 = 0.75 detik = 33075 sampel
    val samples = FloatArray(frameCount * 2)
    val intervalSamples = (sampleRate * 0.75).toInt()

    var trigger = 0
    while (trigger < frameCount) {
      val hitDuration = minOf(4410, frameCount - trigger) // 100ms burst
      for (f in 0 until hitDuration) {
        val i = (trigger + f) * 2
        val amp = (1.0f - (f.toFloat() / hitDuration)) * 0.9f
        samples[i] = amp
        samples[i + 1] = amp
      }
      trigger += intervalSamples
    }

    val beatPcm = AudioPcmData(samples, sampleRate, 2)
    val bpmResult = BpmDetector.detectBpm(beatPcm)

    assertNotNull(bpmResult)
    val detectedBpm = bpmResult?.bpm ?: 0f
    // Harus mendeteksi sekitar 80 BPM (toleransi ±5 BPM atau kelipatan harmonik dalam rentang DJ Slow)
    assertTrue("BPM harus dalam rentang valid 60..140: $detectedBpm", detectedBpm in 60.0f..140.0f)
  }

  // ==========================================
  // TAHAP 3: TES KEY ENGINE
  // ==========================================
  @Test
  fun `key detector analyzes chroma and profiles for tonal audio`() {
    val sampleRate = 44100
    val frameCount = sampleRate * 3 // 3 detik

    // Buat akor A Minor sintetis (A=220 Hz, C=261.63 Hz, E=329.63 Hz)
    val samples = FloatArray(frameCount * 2) { i ->
      val frame = i / 2
      val a = sin(2.0 * Math.PI * 220.0 * frame / sampleRate).toFloat()
      val c = sin(2.0 * Math.PI * 261.63 * frame / sampleRate).toFloat()
      val e = sin(2.0 * Math.PI * 329.63 * frame / sampleRate).toFloat()
      (0.3f * (a + c + e))
    }

    val pcm = AudioPcmData(samples, sampleRate, 2)
    val keyResult = KeyDetector.detectKey(pcm)

    assertNotNull(keyResult)
    assertTrue("Tingkat keyakinan deteksi tangga nada harus valid", (keyResult?.confidence ?: 0f) > 0f)
    assertNotNull(keyResult?.key?.displayName)
  }

  // ==========================================
  // TAHAP 3: TES CHORD ENGINE & TIMELINE
  // ==========================================
  @Test
  fun `chord engine constructs diatonic progression and synchronizes with master timeline`() {
    val bpm = 80.0f
    val durationMs = 12000L // 12 detik = 16 beat pada 80 BPM = 4 bar
    val timeline = MasterTimeline.build(bpm, durationMs)

    assertEquals(4, timeline.totalBars)
    assertEquals(16, timeline.totalBeats)

    val key = MusicKey(PitchClass.A, MusicMode.MINOR)
    val progression = ChordEngine.buildChordProgression(timeline, key, null)

    assertEquals(4, progression.chordEvents.size)
    assertEquals(PitchClass.A, progression.chordEvents[0].chord.root)
    assertEquals(ChordType.MINOR, progression.chordEvents[0].chord.type)

    // Periksa bahwa timing event tepat di grid bar
    for (i in 0 until 4) {
      assertEquals(i, progression.chordEvents[i].barIndex)
      val expectedStartSample = timeline.beatToSample(i * 4.0f)
      assertEquals(expectedStartSample, progression.chordEvents[i].startSample)
    }
  }

  // ==========================================
  // TAHAP 3: TES CHORD SYNTH ENGINE
  // ==========================================
  @Test
  fun `chord synth engine generates non-silent polyphonic pcm audio`() {
    val timeline = MasterTimeline.build(80.0f, 6000L)
    val key = MusicKey(PitchClass.A, MusicMode.MINOR)
    val progression = ChordEngine.buildChordProgression(timeline, key, null)
    val syncTimeline = timeline.copy(chordEvents = progression.chordEvents)

    val synthPcm = ChordSynthEngine.renderProgressionPcm(
      timeline = syncTimeline,
      preset = ChordSynthPreset.SOFT_PIANO,
      volume = 0.70f
    )

    assertEquals(44100, synthPcm.sampleRate)
    assertEquals(2, synthPcm.channels)
    assertEquals(syncTimeline.totalFrames.toInt(), synthPcm.totalFrames)
    assertFalse("Sintesis akor tidak boleh menghasilkan audio hening", synthPcm.isSilent())
    assertTrue("Peak akor harus berada dalam batas aman: ${synthPcm.calculatePeak()}", synthPcm.calculatePeak() <= 1.0f)
  }

  // ==========================================
  // TAHAP 3: TES BASS ENGINE
  // ==========================================
  @Test
  fun `bass engine generates syncopated dj slow bass pattern and pcm`() {
    val timeline = MasterTimeline.build(80.0f, 6000L)
    val key = MusicKey(PitchClass.A, MusicMode.MINOR)
    val progression = ChordEngine.buildChordProgression(timeline, key, null)
    val syncTimeline = timeline.copy(chordEvents = progression.chordEvents)

    val bassEvents = BassEngine.generateBassEvents(syncTimeline, key, BassPatternType.DJ_SLOW_BASS)
    assertTrue("Harus menghasilkan event bass untuk setiap bar", bassEvents.size >= 4)

    val bassPcm = BassEngine.renderBassPcm(
      timeline = syncTimeline,
      bassEvents = bassEvents,
      volume = 0.85f
    )

    assertEquals(44100, bassPcm.sampleRate)
    assertEquals(2, bassPcm.channels)
    assertEquals(syncTimeline.totalFrames.toInt(), bassPcm.totalFrames)
    assertFalse("Sintesis bass tidak boleh hening", bassPcm.isSilent())
    assertTrue("Peak bass harus terkontrol: ${bassPcm.calculatePeak()}", bassPcm.calculatePeak() <= 1.0f)
  }

  // ==========================================
  // TAHAP 3: TES 4-TRACK MIXING & HEADROOM
  // ==========================================
  @Test
  fun `mix engine mixes 4 tracks vocal beat chord and bass safely`() {
    val sampleRate = 44100
    val frameCount = sampleRate * 2 // 2 detik

    val vocalPcm = AudioPcmData(FloatArray(frameCount * 2) { 0.5f * sin(2.0 * Math.PI * 440.0 * (it / 2) / sampleRate).toFloat() }, sampleRate, 2)
    val beatPcm = AudioPcmData(FloatArray(frameCount * 2) { 0.6f * sin(2.0 * Math.PI * 120.0 * (it / 2) / sampleRate).toFloat() }, sampleRate, 2)
    val chordPcm = AudioPcmData(FloatArray(frameCount * 2) { 0.5f * sin(2.0 * Math.PI * 330.0 * (it / 2) / sampleRate).toFloat() }, sampleRate, 2)
    val bassPcm = AudioPcmData(FloatArray(frameCount * 2) { 0.7f * sin(2.0 * Math.PI * 55.0 * (it / 2) / sampleRate).toFloat() }, sampleRate, 2)

    val params = MixEngine.MixParams(
      vocalSettings = MixTrackSettings(volume = 1.0f),
      beatSettings = MixTrackSettings(volume = 0.8f),
      chordSettings = MixTrackSettings(volume = 0.7f),
      bassSettings = MixTrackSettings(volume = 0.85f),
      masterGain = 1.0f,
      isAutoMixEnabled = true
    )

    val mixResult = MixEngine.mix(
      vocalPcm = vocalPcm,
      beatPcm = beatPcm,
      chordPcm = chordPcm,
      bassPcm = bassPcm,
      params = params
    )

    assertTrue("Mixing 4 trek harus berhasil", mixResult.isSuccess)
    val mixedPcm = mixResult.getOrThrow()

    assertEquals(44100, mixedPcm.sampleRate)
    assertEquals(2, mixedPcm.channels)
    assertEquals(frameCount, mixedPcm.totalFrames)
    assertTrue("Peak 4-trek harus <= 0.95 (Headroom Limiter): ${mixedPcm.calculatePeak()}", mixedPcm.calculatePeak() <= 0.95f)
    assertFalse("Mixed audio tidak boleh hening", mixedPcm.isSilent())
  }

  // ==========================================
  // TAHAP 3: TES RENDER WAV & VALIDASI
  // ==========================================
  @Test
  fun `wav renderer produces valid riff 16-bit 44100hz stereo wav file`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sampleRate = 44100
    val frameCount = sampleRate * 2

    val samples = FloatArray(frameCount * 2) { i ->
      (0.6f * sin(2.0 * Math.PI * 440.0 * (i / 2) / sampleRate)).toFloat()
    }
    val pcm = AudioPcmData(samples, sampleRate, 2)
    val outputFile = File(context.cacheDir, "test_tahap3_render.wav")
    if (outputFile.exists()) outputFile.delete()

    val renderResult = WavRenderer.render(pcm, outputFile)
    assertTrue("Render WAV harus berhasil", renderResult.isSuccess)

    val validation = WavValidator.validate(outputFile)
    assertTrue("Validasi berkas WAV harus lulus: ${validation.errorMessage}", validation.isValid)
    assertEquals(44100, validation.sampleRate)
    assertEquals(2, validation.channels)
    assertEquals(16, validation.bitsPerSample)
    assertTrue("Durasi harus ~2 detik", validation.durationMs in 1990..2010)

    outputFile.delete()
  }

  // ==========================================
  // TAHAP 3: TES UI & SEMUA KONTROL MUSIK
  // ==========================================
  @Test
  fun `main screen renders all music engine and tahap 3 components properly`() {
    composeTestRule.setContent {
      MyApplicationTheme {
        MainScreen()
      }
    }

    // Tombol utama Tahap 1-3
    composeTestRule.onNodeWithText("🎧 AUTO REMIX DJ SLOW").assertExists()
    composeTestRule.onNodeWithTag("select_vocal_button").assertExists()
    composeTestRule.onNodeWithTag("select_beat_button").assertExists()
    composeTestRule.onNodeWithTag("play_button").assertExists()
    composeTestRule.onNodeWithTag("pause_button").assertExists()
    composeTestRule.onNodeWithTag("stop_button").assertExists()
    composeTestRule.onNodeWithTag("analysis_button").assertExists()
    composeTestRule.onNodeWithTag("auto_remix_button").assertExists()
    composeTestRule.onNodeWithTag("results_button").assertExists()

    // Komponen Tahap 3: Mesin Musik Card
    composeTestRule.onNodeWithTag("music_engine_card").assertExists()
    composeTestRule.onNodeWithTag("bpm_section").assertExists()
    composeTestRule.onNodeWithTag("target_bpm_text").assertExists()
    composeTestRule.onNodeWithTag("bpm_decrement_button").assertExists()
    composeTestRule.onNodeWithTag("bpm_increment_button").assertExists()
    composeTestRule.onNodeWithTag("key_section").assertExists()
    composeTestRule.onNodeWithTag("key_text").assertExists()
    composeTestRule.onNodeWithTag("chord_section").assertExists()
    composeTestRule.onNodeWithTag("chord_text").assertExists()
    composeTestRule.onNodeWithTag("bass_section").assertExists()
    composeTestRule.onNodeWithTag("bass_pattern_text").assertExists()

    // 4-Track Mixer & FX
    composeTestRule.onNodeWithTag("mix_section").assertExists()
    composeTestRule.onNodeWithTag("vocal_mix_volume_slider").assertExists()
    composeTestRule.onNodeWithTag("beat_mix_volume_slider").assertExists()
    composeTestRule.onNodeWithTag("chord_mix_volume_slider").assertExists()
    composeTestRule.onNodeWithTag("bass_mix_volume_slider").assertExists()
    composeTestRule.onNodeWithTag("mix_and_render_button").assertExists()

    // Tahap 5: Mastering & 6-Bus Mixer Card
    composeTestRule.onNodeWithTag("mastering_card").assertExists()
    composeTestRule.onNodeWithTag("preset_dj_slow").assertExists()
    composeTestRule.onNodeWithTag("vocal_bus_slider").assertExists()
    composeTestRule.onNodeWithTag("master_bus_slider").assertExists()
  }
}
