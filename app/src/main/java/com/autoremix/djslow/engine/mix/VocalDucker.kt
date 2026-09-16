package com.autoremix.djslow.engine.mix

import com.autoremix.djslow.engine.pcm.AudioPcmData
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

/**
 * Mesin Ducking Vokal Halus (Tahap 5).
 * Menurunkan level instrumen pengiring (Akor, Pad, Melodi, FX) saat vokal terdengar aktif (-2 s/d -4 dB).
 * Menggunakan attack halus (~25ms) dan release musikal (~280ms) untuk mencegah pumping kasar.
 */
object VocalDucker {

    fun applyVocalDucking(
        musicPcm: AudioPcmData,
        vocalPcm: AudioPcmData?,
        duckingDepthDb: Float = -3.0f,
        attackMs: Float = 25.0f,
        releaseMs: Float = 280.0f
    ): AudioPcmData {
        if (vocalPcm == null || vocalPcm.isSilent() || musicPcm.isSilent()) {
            return musicPcm
        }

        val musicSamples = musicPcm.samples.copyOf()
        val vocalSamples = vocalPcm.samples
        val sampleRate = musicPcm.sampleRate
        val channels = musicPcm.channels

        val totalFrames = musicPcm.totalFrames
        val vocalFrames = vocalPcm.totalFrames

        val attackAlpha = exp(-1.0 / (attackMs * 0.001 * sampleRate)).toFloat()
        val releaseAlpha = exp(-1.0 / (releaseMs * 0.001 * sampleRate)).toFloat()

        // Target gain ketika vokal aktif (misal -3 dB = ~0.707f)
        val targetDuckGain = 10.0f.pow(duckingDepthDb / 20.0f).coerceIn(0.5f, 0.95f)

        // Ambang batas deteksi vokal aktif (~ -38 dBFS)
        val vocalActiveThreshold = 0.012f

        var currentVocalEnv = 0.0f
        var currentDuckingGain = 1.0f

        for (f in 0 until totalFrames) {
            val vPeak = if (f < vocalFrames) {
                val vIdx = f * vocalPcm.channels
                val vL = abs(vocalSamples[vIdx])
                val vR = if (vocalPcm.channels > 1) abs(vocalSamples[vIdx + 1]) else vL
                max(vL, vR)
            } else {
                0.0f
            }

            // Envelope deteksi vokal
            currentVocalEnv = if (vPeak > currentVocalEnv) {
                0.15f * vPeak + 0.85f * currentVocalEnv
            } else {
                0.005f * vPeak + 0.995f * currentVocalEnv
            }

            // Tentukan target ducking gain
            val targetGain = if (currentVocalEnv > vocalActiveThreshold) {
                targetDuckGain
            } else {
                1.0f
            }

            // Smoothing ducking gain (attack/release)
            currentDuckingGain = if (targetGain < currentDuckingGain) {
                (1.0f - attackAlpha) * targetGain + attackAlpha * currentDuckingGain
            } else {
                (1.0f - releaseAlpha) * targetGain + releaseAlpha * currentDuckingGain
            }

            // Terapkan ke sampel musik
            val mIdx = f * channels
            musicSamples[mIdx] *= currentDuckingGain
            if (channels > 1) {
                musicSamples[mIdx + 1] *= currentDuckingGain
            }
        }

        return AudioPcmData(musicSamples, sampleRate, channels)
    }
}
