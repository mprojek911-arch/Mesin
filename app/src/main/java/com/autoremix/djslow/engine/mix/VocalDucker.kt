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

    /**
     * Prosesor Ducking Vokal per Blok yang menyimpan status filter attack/release
     * melintasi batas blok pemrosesan (anti popping / anti klik).
     */
    class VocalDuckProcessor(
        val sampleRate: Int = 44100,
        val duckingDepthDb: Float = -3.0f,
        val attackMs: Float = 25.0f,
        val releaseMs: Float = 280.0f
    ) {
        private val attackAlpha = exp(-1.0 / (attackMs * 0.001 * sampleRate)).toFloat()
        private val releaseAlpha = exp(-1.0 / (releaseMs * 0.001 * sampleRate)).toFloat()
        private val targetDuckGain = 10.0f.pow(duckingDepthDb / 20.0f).coerceIn(0.5f, 0.95f)
        private val vocalActiveThreshold = 0.012f

        private var currentVocalEnv = 0.0f
        private var currentDuckingGain = 1.0f

        fun processBlock(
            musicBlock: FloatArray,
            vocalBlock: FloatArray?,
            frameCount: Int,
            channels: Int = 2,
            offset: Int = 0
        ) {
            if (vocalBlock == null) return

            for (f in 0 until frameCount) {
                val vIdx = f * channels
                val vL = if (vIdx < vocalBlock.size) abs(vocalBlock[vIdx]) else 0.0f
                val vR = if (vIdx + 1 < vocalBlock.size) abs(vocalBlock[vIdx + 1]) else vL
                val vPeak = max(vL, vR)

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

                val mIdx = offset + f * channels
                if (mIdx + 1 < musicBlock.size) {
                    musicBlock[mIdx] *= currentDuckingGain
                    musicBlock[mIdx + 1] *= currentDuckingGain
                }
            }
        }

        fun reset() {
            currentVocalEnv = 0.0f
            currentDuckingGain = 1.0f
        }
    }

    @Deprecated("Gunakan VocalDuckProcessor untuk pemrosesan blok streaming tanpa OOM")
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
