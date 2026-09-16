package com.autoremix.djslow.engine.melody

import com.autoremix.djslow.engine.arrangement.AutoDjPreset
import com.autoremix.djslow.engine.music.Chord
import com.autoremix.djslow.engine.music.MusicKey
import com.autoremix.djslow.engine.music.PitchClass
import com.autoremix.djslow.engine.pcm.AudioPcmData
import com.autoremix.djslow.engine.structure.EnergyCurve
import com.autoremix.djslow.engine.structure.SongSection
import com.autoremix.djslow.engine.structure.SongSectionType
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Lapisan peran melodi.
 */
enum class MelodyLayerType {
    LEAD, COUNTER, HOOK
}

/**
 * Event nada melodi terjadwal.
 */
data class MelodyEvent(
    val pitchHz: Float,
    val midiNote: Int,
    val sampleOffset: Long,
    val durationSamples: Int,
    val velocity: Float,
    val layerType: MelodyLayerType = MelodyLayerType.LEAD
)

/**
 * Melody Engine:
 * Algorithmic melody generator deterministik (didasarkan pada Seed acak yang dapat diulang).
 * Menyesuaikan tangga nada (Key/Scale), progresi akor, seksi lagu, dan energi dinamis.
 */
object MelodyEngine {

    /**
     * Menghasilkan event melodi deterministik berdasarkan Seed.
     */
    fun scheduleMelodyEvents(
        key: MusicKey,
        chords: List<Chord>,
        bpm: Float,
        sampleRate: Int,
        totalBars: Int,
        sections: List<SongSection>,
        energyCurve: EnergyCurve,
        seed: Long,
        preset: AutoDjPreset
    ): List<MelodyEvent> {
        val random = Random(seed)
        val events = ArrayList<MelodyEvent>()

        val samplesPerBeat = (sampleRate * 60f / bpm).toLong()
        val samplesPerBar = samplesPerBeat * 4
        val scaleNotes = key.getScalePitchClasses()

        // Oktaf dasar untuk Lead: Oktaf 4 dan 5 (MIDI 60 .. 84)
        val baseOctave = 4

        // Buat kumpulan nada skala pada oktaf 4 dan 5
        val scaleMidiNotes = ArrayList<Int>()
        for (oct in baseOctave..(baseOctave + 1)) {
            for (p in scaleNotes) {
                val midi = 12 * (oct + 1) + p.semitone
                scaleMidiNotes.add(midi)
            }
        }

        val presence = preset.melodyPresence

        for (section in sections) {
            val secType = section.sectionType
            val isIntro = secType == SongSectionType.INTRO
            val isDropOrPeak = secType in listOf(
                SongSectionType.DROP,
                SongSectionType.MAIN_DROP,
                SongSectionType.PEAK,
                SongSectionType.FINAL_DROP
            )
            val isBreak = secType in listOf(SongSectionType.BREAK, SongSectionType.BREAKDOWN)
            val isPreDrop = secType == SongSectionType.PRE_DROP
            val isBuildUp = secType in listOf(
                SongSectionType.BUILD_UP,
                SongSectionType.BUILD_UP_2,
                SongSectionType.FINAL_BUILD
            )
            val isOutro = secType == SongSectionType.OUTRO

            // PRE-DROP: Melodi hening / teaser minimal (BAGIAN D)
            if (isPreDrop) {
                val barStartSample = section.startBar * samplesPerBar
                val tonicMidi = 12 * (baseOctave + 2) + key.tonic.semitone
                events.add(
                    MelodyEvent(
                        pitchHz = Chord.midiToFrequency(tonicMidi),
                        midiNote = tonicMidi,
                        sampleOffset = barStartSample,
                        durationSamples = (samplesPerBeat * 0.75).toInt(),
                        velocity = 0.35f * presence,
                        layerType = MelodyLayerType.LEAD
                    )
                )
                continue
            }

            // INTRO: Nada jarang / motif sederhana di bar ke-5 dst
            if (isIntro) {
                for (bar in (section.startBar + 4) until section.endBar) {
                    val barStartSample = bar * samplesPerBar
                    // 1-2 nada per bar
                    val targetMidi = scaleMidiNotes[random.nextInt(scaleMidiNotes.size.coerceAtMost(7))]
                    events.add(
                        MelodyEvent(
                            pitchHz = Chord.midiToFrequency(targetMidi),
                            midiNote = targetMidi,
                            sampleOffset = barStartSample + samplesPerBeat,
                            durationSamples = (samplesPerBeat * 2).toInt(),
                            velocity = 0.45f * presence,
                            layerType = MelodyLayerType.LEAD
                        )
                    )
                }
                continue
            }

            // BUILD UP: Nada melodi arpeggio naik perlahan mengikuti build
            if (isBuildUp) {
                for (bar in section.startBar until section.endBar) {
                    val barStartSample = bar * samplesPerBar
                    val stepSample = samplesPerBeat / 2
                    val energy = energyCurve.getEnergyAtBar(bar)
                    for (step in 0 until 8) {
                        if (step % 2 == 0) {
                            val noteIdx = (step + (bar - section.startBar) * 2) % scaleMidiNotes.size
                            val midi = scaleMidiNotes[noteIdx]
                            events.add(
                                MelodyEvent(
                                    pitchHz = Chord.midiToFrequency(midi),
                                    midiNote = midi,
                                    sampleOffset = barStartSample + step * stepSample,
                                    durationSamples = (stepSample * 0.85).toInt(),
                                    velocity = (0.35f + energy * 0.45f) * presence,
                                    layerType = MelodyLayerType.LEAD
                                )
                            )
                        }
                    }
                }
                continue
            }

            // BREAK / BREAKDOWN: Melodi counter manis & santai (legato lembut)
            if (isBreak) {
                for (bar in section.startBar until section.endBar) {
                    val barStartSample = bar * samplesPerBar
                    val tonicMidi = 12 * (baseOctave + 1) + key.tonic.semitone
                    // Motif 3 nada santai
                    val motif = listOf(tonicMidi, tonicMidi + 2, tonicMidi + 4)
                    for ((idx, midi) in motif.withIndex()) {
                        events.add(
                            MelodyEvent(
                                pitchHz = Chord.midiToFrequency(midi),
                                midiNote = midi,
                                sampleOffset = barStartSample + (idx * samplesPerBeat),
                                durationSamples = (samplesPerBeat * 0.9).toInt(),
                                velocity = 0.50f * presence,
                                layerType = MelodyLayerType.COUNTER
                            )
                        )
                    }
                }
                continue
            }

            // DROP / GROOVE / PEAK: Ritmik DJ Slow yang catchy (Hook & Lead)
            // Buat pola motif 2-bar berulang dengan variasi
            val motifPitches = List(4) {
                scaleMidiNotes[random.nextInt(scaleMidiNotes.size)]
            }

            for (bar in section.startBar until section.endBar) {
                val barStartSample = bar * samplesPerBar
                val energy = energyCurve.getEnergyAtBar(bar)
                val barInCycle = (bar - section.startBar) % 4

                // Pola sinkopasi DJ Slow: nada pada beat 1, beat 1.75, beat 2.5, beat 3, beat 4.25
                val beatSteps = listOf(0.0f, 0.75f, 1.5f, 2.25f, 3.0f, 3.5f)

                for ((stepIdx, beatPos) in beatSteps.withIndex()) {
                    val noteMidi = if (secType == SongSectionType.PEAK) {
                        // Di Peak: oktaf lebih tinggi (+12 semitone)
                        motifPitches[(stepIdx + barInCycle) % motifPitches.size] + 12
                    } else {
                        motifPitches[(stepIdx + barInCycle) % motifPitches.size]
                    }

                    val offset = barStartSample + (beatPos * samplesPerBeat).toLong()
                    val dur = (samplesPerBeat * 0.65).toInt()
                    val vel = (0.70f * energy * presence).coerceIn(0.2f, 1.0f)

                    events.add(
                        MelodyEvent(
                            pitchHz = Chord.midiToFrequency(noteMidi),
                            midiNote = noteMidi,
                            sampleOffset = offset,
                            durationSamples = dur,
                            velocity = vel,
                            layerType = if (isDropOrPeak) MelodyLayerType.HOOK else MelodyLayerType.LEAD
                        )
                    )
                }
            }

            // OUTRO: Resolusi ke nada dasar (tonic) dengan peluruhan
            if (isOutro) {
                for (bar in section.startBar until section.endBar) {
                    val barStartSample = bar * samplesPerBar
                    val tonicMidi = 12 * (baseOctave + 1) + key.tonic.semitone
                    val energy = energyCurve.getEnergyAtBar(bar)
                    events.add(
                        MelodyEvent(
                            pitchHz = Chord.midiToFrequency(tonicMidi),
                            midiNote = tonicMidi,
                            sampleOffset = barStartSample,
                            durationSamples = (samplesPerBeat * 3).toInt(),
                            velocity = (0.50f * energy * presence).coerceIn(0.1f, 0.6f),
                            layerType = MelodyLayerType.LEAD
                        )
                    )
                }
            }
        }

        return events.sortedBy { it.sampleOffset }
    }

    /**
     * Merender melodi menjadi buffer PCM stereo Float32.
     * Menggunakan sintesis Lead Synth dengan dua osilator detuned + envelope decay ekspresif.
     */
    fun renderMelody(
        events: List<MelodyEvent>,
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

            val duration = min(event.durationSamples, (sampleRate * 2.0).toInt())
            val freq1 = event.pitchHz
            val freq2 = event.pitchHz * 1.004f // Sedikit detune untuk stereo spread mewah
            val vel = event.velocity

            var phase1 = 0.0
            var phase2 = 0.0

            val attackSamples = (sampleRate * 0.015).toInt()
            val releaseSamples = (sampleRate * 0.05).toInt()

            for (i in 0 until duration) {
                val outIdx = startIdx + i * channels
                if (outIdx + 1 >= buffer.size) break

                val t = i.toDouble() / sampleRate
                val env = when {
                    i < attackSamples -> (i.toDouble() / attackSamples)
                    i > duration - releaseSamples -> ((duration - i).toDouble() / releaseSamples)
                    else -> exp(-t * 2.5) * 0.85 + 0.15
                }

                phase1 += 2.0 * PI * freq1 / sampleRate
                phase2 += 2.0 * PI * freq2 / sampleRate

                // Saw-like wave (fundamental + harmonik ke-2 & ke-3)
                val voiceL = sin(phase1) * 0.6 + sin(phase1 * 2.0) * 0.25 + sin(phase1 * 3.0) * 0.15
                val voiceR = sin(phase2) * 0.6 + sin(phase2 * 2.0) * 0.25 + sin(phase2 * 3.0) * 0.15

                val sampleL = (voiceL * env * vel * 0.45f).toFloat()
                val sampleR = (voiceR * env * vel * 0.45f).toFloat()

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
