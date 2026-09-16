package com.autoremix.djslow.engine.synth

import kotlin.math.PI
import kotlin.math.sin

/**
 * Bentuk Gelombang Osilator.
 */
enum class Waveform(val label: String) {
    SINE("Sine"),
    TRIANGLE("Triangle"),
    SAW("Sawtooth"),
    SQUARE("Square")
}

/**
 * Generator Gelombang Osilator dengan phase tracking.
 */
class SynthOscillator(
    var waveform: Waveform = Waveform.SINE,
    var frequencyHz: Float = 440.0f,
    var sampleRate: Int = 44100
) {
    private var phase: Double = 0.0

    fun resetPhase() {
        phase = 0.0
    }

    /**
     * Menghasilkan satu sampel audio [-1.0 .. 1.0].
     */
    fun nextSample(): Float {
        val phaseInc = (2.0 * PI * frequencyHz) / sampleRate
        val output = when (waveform) {
            Waveform.SINE -> sin(phase).toFloat()
            Waveform.TRIANGLE -> {
                // Triangle berosilasi -1 .. 1
                val normalizedPhase = (phase / (2.0 * PI)) % 1.0
                val p = if (normalizedPhase < 0) normalizedPhase + 1.0 else normalizedPhase
                if (p < 0.5) (4.0 * p - 1.0).toFloat() else (3.0 - 4.0 * p).toFloat()
            }
            Waveform.SAW -> {
                val normalizedPhase = (phase / (2.0 * PI)) % 1.0
                val p = if (normalizedPhase < 0) normalizedPhase + 1.0 else normalizedPhase
                (2.0 * p - 1.0).toFloat()
            }
            Waveform.SQUARE -> {
                val normalizedPhase = (phase / (2.0 * PI)) % 1.0
                val p = if (normalizedPhase < 0) normalizedPhase + 1.0 else normalizedPhase
                if (p < 0.5) 1.0f else -1.0f
            }
        }

        phase += phaseInc
        if (phase >= 2.0 * PI) {
            phase -= 2.0 * PI
        }
        return output
    }
}

/**
 * ADSR Envelope Generator (Attack, Decay, Sustain, Release) berbasis waktu dan sampel.
 */
data class AdsrParams(
    val attackSeconds: Float = 0.02f,
    val decaySeconds: Float = 0.15f,
    val sustainLevel: Float = 0.70f, // 0.0 .. 1.0
    val releaseSeconds: Float = 0.20f
)

class AdsrEnvelope(
    private val params: AdsrParams,
    private val totalFrames: Long,
    private val sampleRate: Int = 44100
) {
    private val attackFrames = (params.attackSeconds * sampleRate).toLong()
    private val decayFrames = (params.decaySeconds * sampleRate).toLong()
    private val releaseFrames = (params.releaseSeconds * sampleRate).toLong()
    private val sustainStartFrame = attackFrames + decayFrames
    private val releaseStartFrame = maxOf(sustainStartFrame, totalFrames - releaseFrames)

    /**
     * Menghitung nilai envelope [0.0 .. 1.0] pada indeks frame saat ini.
     */
    fun getGain(frameIndex: Long): Float {
        if (frameIndex < 0 || frameIndex >= totalFrames) return 0.0f

        return when {
            frameIndex < attackFrames -> {
                if (attackFrames > 0) frameIndex.toFloat() / attackFrames else 1.0f
            }
            frameIndex < sustainStartFrame -> {
                val decayProgress = if (decayFrames > 0) (frameIndex - attackFrames).toFloat() / decayFrames else 1.0f
                1.0f - decayProgress * (1.0f - params.sustainLevel)
            }
            frameIndex < releaseStartFrame -> {
                params.sustainLevel
            }
            else -> {
                val releaseProgress = if (releaseFrames > 0) (frameIndex - releaseStartFrame).toFloat() / releaseFrames else 1.0f
                (params.sustainLevel * (1.0f - releaseProgress)).coerceAtLeast(0.0f)
            }
        }
    }
}
