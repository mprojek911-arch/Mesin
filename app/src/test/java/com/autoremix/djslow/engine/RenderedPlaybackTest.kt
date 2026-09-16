package com.autoremix.djslow.engine

import com.autoremix.djslow.state.UiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class RenderedPlaybackTest {

    @Test
    fun testRenderedPlaybackStatusValues() {
        assertEquals("⚪ BELUM TERSEDIA", RenderedPlaybackStatus.BELUM_TERSEDIA.label)
        assertEquals("⚪ SIAP", RenderedPlaybackStatus.SIAP.label)
        assertEquals("🟢 SEDANG MEMUTAR", RenderedPlaybackStatus.SEDANG_MEMUTAR.label)
        assertEquals("🟡 DIJEDA", RenderedPlaybackStatus.DIJEDA.label)
        assertEquals("⏹ BERHENTI", RenderedPlaybackStatus.BERHENTI.label)
        assertEquals("⏹ SELESAI", RenderedPlaybackStatus.SELESAI.label)
        assertEquals("🔴 AUDIO TIDAK VALID", RenderedPlaybackStatus.ERROR.label)
    }

    @Test
    fun testInitialAudioState() {
        val state = AudioState()
        assertEquals(RenderedPlaybackStatus.BELUM_TERSEDIA, state.renderedPlaybackStatus)
        assertEquals(0L, state.renderedPositionMs)
        assertEquals(0L, state.renderedDurationMs)
        assertFalse(state.isRenderedPlaying)
        assertFalse(state.isRenderedPaused)
    }

    @Test
    fun testUiStateRenderedProgressFraction() {
        val stateZero = UiState()
        assertEquals(0f, stateZero.renderedProgressFraction, 0.001f)

        val stateProgress = UiState(
            audioState = AudioState(
                renderedDurationMs = 100_000L,
                renderedPositionMs = 25_000L,
                isRenderedPlaying = true,
                renderedPlaybackStatus = RenderedPlaybackStatus.SEDANG_MEMUTAR
            )
        )
        assertEquals(0.25f, stateProgress.renderedProgressFraction, 0.001f)
        assertTrue(stateProgress.isRenderedPlaying)
        assertFalse(stateProgress.isRenderedPaused)
        assertEquals(RenderedPlaybackStatus.SEDANG_MEMUTAR, stateProgress.renderedPlaybackStatus)
    }

    @Test
    fun testUiStateRenderedProgressBounds() {
        // Position > Duration
        val stateOver = UiState(
            audioState = AudioState(
                renderedDurationMs = 100_000L,
                renderedPositionMs = 150_000L
            )
        )
        assertEquals(1f, stateOver.renderedProgressFraction, 0.001f)

        // Negative position
        val stateNeg = UiState(
            audioState = AudioState(
                renderedDurationMs = 100_000L,
                renderedPositionMs = -500L
            )
        )
        assertEquals(0f, stateNeg.renderedProgressFraction, 0.001f)
    }

    @Test
    fun testAudioPlayerNonExistentFile() {
        val player = AudioPlayer()
        val nonExistent = File("/invalid/path/to/missing_file.wav")
        val result = player.setRenderedWav(nonExistent)
        assertTrue(result.isFailure)
        assertEquals(RenderedPlaybackStatus.ERROR, player.audioState.value.renderedPlaybackStatus)
        player.release()
    }
}
