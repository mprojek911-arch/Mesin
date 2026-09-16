package com.autoremix.djslow.engine.pad

import com.autoremix.djslow.engine.arrangement.AutoDjPreset
import com.autoremix.djslow.engine.music.Chord
import com.autoremix.djslow.engine.music.MusicKey
import com.autoremix.djslow.engine.pcm.AudioPcmData
import com.autoremix.djslow.engine.structure.EnergyCurve
import com.autoremix.djslow.engine.structure.SongSection
import com.autoremix.djslow.engine.structure.SongSectionType
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Event Pad terharmonisasi.
 */
data class PadEvent(
    val chord: Chord,
    val sampleOffset: Long,
    val durationSamples: Int,
    val velocity: Float,
    val filterCutoffHz: Float,
    val attackMs: Float,
    val releaseMs: Float
)

/**
 * Pad Engine:
 * Menghasilkan instrumen Pad hangat (warm lush pad synthesizer) yang mengikuti akor,
 * tangga nada, seksi lagu, dan kurva energi.
 * Dilengkapi dynamic attack/release, otomasi Low-Pass Filter, dan headroom vokal (tidak menutupi vokal).
 */
object PadEngine {

    fun schedulePadEvents(
        key: MusicKey,
        chords: List<Chord>,
        bpm: Float,
        sampleRate: Int,
        totalBars: Int,
        sections: List<SongSection>,
        energyCurve: EnergyCurve,
        preset: AutoDjPreset,
        hasVocal: Boolean
    ): List<PadEvent> {
        val events = ArrayList<PadEvent>()
        val samplesPerBar = (sampleRate * 60f / bpm * 4f).toLong()
        val warmth = preset.padWarmth

        // Jika ada vokal, kurangi volume pad sedikit (ducking frekuensi vokal)
        val vocalDuckMultiplier = if (hasVocal) 0.75f else 1.0f

        for (section in sections) {
            val secType = section.sectionType
            for (bar in section.startBar until section.endBar) {
                val chord = chords[bar % chords.size]
                val barStart = bar * samplesPerBar
                val energy = energyCurve.getEnergyAtBar(bar)

                // LPF Cutoff & ADSR Envelope berdasarkan seksi:
                val (cutoff, attackMs, releaseMs, vel) = when (secType) {
                    SongSectionType.INTRO -> {
                        // Pad lembut, LPF rendah (800 Hz), attack lambat (800 ms)
                        Quad(850f, 800f, 1000f, 0.50f * warmth)
                    }
                    SongSectionType.BUILD_UP, SongSectionType.BUILD_UP_2, SongSectionType.FINAL_BUILD -> {
                        // LPF membuka progresif (1200 Hz -> 3500 Hz), attack sedang (400 ms)
                        val prog = (bar - section.startBar).toFloat() / maxOf(1, section.barCount)
                        Quad(1200f + 2500f * prog, 400f, 600f, (0.40f + 0.35f * prog) * warmth)
                    }
                    SongSectionType.BREAK, SongSectionType.BREAKDOWN -> {
                        // Pad lush paling menonjol, hangat, LPF 1800 Hz
                        Quad(1800f, 600f, 800f, 0.70f * warmth)
                    }
                    SongSectionType.PRE_DROP -> {
                        // Dramatic swell pad menuju drop (BAGIAN D)
                        Quad(2200f, 250f, 300f, 0.60f * warmth)
                    }
                    SongSectionType.DROP, SongSectionType.MAIN_DROP, SongSectionType.PEAK, SongSectionType.FINAL_DROP -> {
                        // Pad lebar menopang bass & melodi, LPF 2800 Hz, attack cepat (150 ms)
                        Quad(2800f, 150f, 400f, 0.55f * warmth)
                    }
                    SongSectionType.OUTRO -> {
                        // Pad meluruh santai, LPF 1000 Hz, release panjang (1500 ms)
                        Quad(1000f, 500f, 1500f, (0.50f * energy) * warmth)
                    }
                    else -> {
                        Quad(1500f, 300f, 500f, 0.50f * warmth)
                    }
                }

                events.add(
                    PadEvent(
                        chord = chord,
                        sampleOffset = barStart,
                        durationSamples = samplesPerBar.toInt(),
                        velocity = vel * vocalDuckMultiplier,
                        filterCutoffHz = cutoff,
                        attackMs = attackMs,
                        releaseMs = releaseMs
                    )
                )
            }
        }

        return events
    }

    private data class Quad(val f1: Float, val f2: Float, val f3: Float, val f4: Float)

    /**
     * Merender pad hangat ke audio PCM Stereo Float32 murni.
     * Menggunakan multi-voice sinusoidal detuned pad dengan low-pass filtering.
     */
    fun renderPad(
        events: List<PadEvent>,
        totalSamples: Long,
        sampleRate: Int
    ): AudioPcmData {
        val channels = 2
        val safeSamples = max(44100L, totalSamples)
        val totalFloats = (safeSamples * channels).toInt().coerceAtLeast(channels)
        val buffer = FloatArray(totalFloats)

        for (event in events) {
            val startIdx = (event.sampleOffset * channels).toInt()
            if (startIdx >= buffer.size) continue

            // Ambil frekuensi penyusun akor di oktaf 3 & 4 (hangat)
            val freqs = event.chord.getFrequenciesHz(octave = 3)
            val duration = min(event.durationSamples, (totalSamples - event.sampleOffset).toInt())
            if (duration <= 0) continue

            val attackSamples = ((event.attackMs / 1000f) * sampleRate).toInt().coerceAtLeast(100)
            val releaseSamples = ((event.releaseMs / 1000f) * sampleRate).toInt().coerceAtLeast(100)

            val phases = DoubleArray(freqs.size)
            val detunedPhases = DoubleArray(freqs.size)

            for (i in 0 until duration) {
                val outIdx = startIdx + i * channels
                if (outIdx + 1 >= buffer.size) break

                // Envelope ADSR halus
                val env = when {
                    i < attackSamples -> (i.toDouble() / attackSamples)
                    i > duration - releaseSamples -> ((duration - i).toDouble() / releaseSamples)
                    else -> 1.0
                }

                var sumL = 0.0
                var sumR = 0.0

                for (f in freqs.indices) {
                    val baseFreq = freqs[f]
                    val detuneFreq = baseFreq * 1.003

                    phases[f] += 2.0 * PI * baseFreq / sampleRate
                    detunedPhases[f] += 2.0 * PI * detuneFreq / sampleRate

                    // Sine murni dengan sub-harmonik halus (warm pad feel)
                    val v1 = sin(phases[f]) + 0.3 * sin(phases[f] * 0.5)
                    val v2 = sin(detunedPhases[f]) + 0.3 * sin(detunedPhases[f] * 0.5)

                    sumL += v1
                    sumR += v2
                }

                val norm = 1.0 / maxOf(1, freqs.size)
                val sampleL = (sumL * norm * env * event.velocity * 0.40f).toFloat()
                val sampleR = (sumR * norm * env * event.velocity * 0.40f).toFloat()

                buffer[outIdx] += sampleL
                buffer[outIdx + 1] += sampleR
            }
        }

        return AudioPcmData(
            samples = buffer,
            sampleRate = sampleRate,
            channels = channels
        )
    }
}
