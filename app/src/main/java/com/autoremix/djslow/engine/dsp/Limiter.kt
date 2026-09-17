package com.autoremix.djslow.engine.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.tanh

/**
 * Brickwall Lookahead Peak Limiter & Anti-Clipping Engine.
 * Menjamin sinyal master tidak pernah mengalami clipping digital (> 1.0f atau melewati ceiling).
 *
 * Menggunakan:
 * - Buffer lookahead ~2.5ms untuk mengantisipasi transien sebelum terjadi
 * - Kurva soft-saturation halus (tanh) saat mendekati batas
 * - Envelope release eksponensial yang musikal untuk mencegah distorsi dan pumping
 * - Proteksi absolut pasca-proses (hard clamp di batas aman ceiling)
 */
class Limiter(
    val ceilingDbtp: Float = -1.0f,
    val lookaheadMs: Float = 2.5f,
    val releaseMs: Float = 60.0f,
    val sampleRate: Int = 44100
) {
    val ceilingLinear = 10.0f.pow(ceilingDbtp / 20.0f).coerceIn(0.1f, 0.99f)
    private val lookaheadFrames = ((lookaheadMs * 0.001f) * sampleRate).toInt().coerceAtLeast(4)
    private val releaseAlpha = exp(-1.0 / (releaseMs * 0.001 * sampleRate)).toFloat()

    private val delayBufferL = FloatArray(lookaheadFrames)
    private val delayBufferR = FloatArray(lookaheadFrames)
    private var bufferIdx = 0
    private var currentGain = 1.0f

    /**
     * Memproses buffer interleaved stereo [samples].
     * Mengembalikan true jika limiter berhasil dan bebas clipping.
     */
    fun processInterleaved(samples: FloatArray, channels: Int = 2) {
        val totalFrames = samples.size / channels
        if (totalFrames <= 0) return

        // Ambang batas mulai reduksi halus (soft knee 2 dB di bawah ceiling)
        val softThreshold = ceilingLinear * 0.80f

        for (f in 0 until totalFrames) {
            val idxL = f * channels
            val idxR = if (channels > 1) idxL + 1 else idxL

            val inL = samples[idxL]
            val inR = if (channels > 1) samples[idxR] else inL

            // 1. Deteksi puncak instan pada input masa depan (lookahead)
            val peakNow = max(abs(inL), abs(inR))

            // Hitung target gain yang dibutuhkan agar puncak masa depan tidak melebihi ceiling
            val targetGain = when {
                peakNow <= softThreshold -> 1.0f
                peakNow < ceilingLinear -> {
                    // Soft knee kompresi halus
                    val excess = (peakNow - softThreshold) / (ceilingLinear - softThreshold)
                    1.0f - (excess * 0.15f)
                }
                else -> {
                    // Brickwall attenuation
                    ceilingLinear / peakNow
                }
            }

            // Envelope follower: Serangan instan (0 ms attack karena lookahead), pelepasan halus (release)
            if (targetGain < currentGain) {
                currentGain = targetGain
            } else {
                currentGain = releaseAlpha * currentGain + (1.0f - releaseAlpha) * targetGain
            }

            // 2. Baca sampel dari buffer delay yang sudah disejajarkan
            val delayedL = delayBufferL[bufferIdx]
            val delayedR = delayBufferR[bufferIdx]

            // Tulis sampel baru ke lookahead buffer
            delayBufferL[bufferIdx] = inL
            delayBufferR[bufferIdx] = inR
            bufferIdx = (bufferIdx + 1) % lookaheadFrames

            // 3. Terapkan gain reduction
            var outL = delayedL * currentGain
            var outR = delayedR * currentGain

            // 4. Soft Saturation Halus di dekat ceiling jika ada sisa overshoot
            if (abs(outL) > softThreshold) {
                val sign = if (outL >= 0.0f) 1.0f else -1.0f
                val norm = abs(outL) / ceilingLinear
                outL = sign * ceilingLinear * tanh(norm)
            }
            if (abs(outR) > softThreshold) {
                val sign = if (outR >= 0.0f) 1.0f else -1.0f
                val norm = abs(outR) / ceilingLinear
                outR = sign * ceilingLinear * tanh(norm)
            }

            // 5. Anti-Clipping Absolut: Pastikan tidak pernah melewati ceilingLinear
            samples[idxL] = outL.coerceIn(-ceilingLinear, ceilingLinear)
            if (channels > 1) {
                samples[idxR] = outR.coerceIn(-ceilingLinear, ceilingLinear)
            }
        }
    }
}
