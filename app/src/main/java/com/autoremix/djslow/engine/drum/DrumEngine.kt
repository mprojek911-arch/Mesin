package com.autoremix.djslow.engine.drum

import com.autoremix.djslow.engine.analysis.BeatContentAnalyzer.BeatContentAnalysis
import com.autoremix.djslow.engine.analysis.GeneratedDrumMode
import com.autoremix.djslow.engine.arrangement.AutoDjPreset
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
 * Tipe suara drum yang disintesis.
 */
enum class DrumSoundType {
    KICK, SNARE, CLAP, CLOSED_HAT, OPEN_HAT, PERCUSSION, CRASH, FILL_SNARE
}

/**
 * Event drum terjadwal pada Master Timeline.
 */
data class DrumEvent(
    val soundType: DrumSoundType,
    val sampleOffset: Long,
    val durationSamples: Int,
    val velocity: Float = 1.0f,
    val pan: Float = 0.0f // -1.0 (L) .. 1.0 (R)
)

/**
 * Drum Engine:
 * Menghasilkan ritme drum lengkap DJ Slow bervariasi per seksi (Kick, Snare, Clap, Hats, Crash, Fills).
 * Menggunakan sintesis DSP murni ke PCM Float32 Stereo (44.1 kHz).
 */
object DrumEngine {

    /**
     * Membangun daftar event drum untuk seluruh lagu berdasarkan seksi, energi, dan preset.
     */
    fun scheduleDrumEvents(
        sections: List<SongSection>,
        energyCurve: EnergyCurve,
        bpm: Float,
        sampleRate: Int,
        totalBars: Int,
        beatAnalysis: BeatContentAnalysis,
        preset: AutoDjPreset
    ): List<DrumEvent> {
        if (beatAnalysis.recommendedDrumMode == GeneratedDrumMode.OFF) {
            return emptyList()
        }

        val events = ArrayList<DrumEvent>()
        val samplesPerBeat = (sampleRate * 60f / bpm).toLong()
        val samplesPerBar = samplesPerBeat * 4
        val drumDensity = preset.drumDensityMultiplier * beatAnalysis.recommendedDrumMode.volumeMultiplier

        for (section in sections) {
            val isBuildUp = section.sectionType in listOf(
                SongSectionType.BUILD_UP,
                SongSectionType.BUILD_UP_2,
                SongSectionType.FINAL_BUILD
            )
            val isDropOrPeak = section.sectionType in listOf(
                SongSectionType.DROP,
                SongSectionType.MAIN_DROP,
                SongSectionType.PEAK,
                SongSectionType.FINAL_DROP
            )
            val isIntro = section.sectionType == SongSectionType.INTRO
            val isBreak = section.sectionType in listOf(SongSectionType.BREAK, SongSectionType.BREAKDOWN)
            val isPreDrop = section.sectionType == SongSectionType.PRE_DROP
            val isOutro = section.sectionType == SongSectionType.OUTRO

            for (bar in section.startBar until section.endBar) {
                val barStartSample = bar * samplesPerBar
                val barInSec = bar - section.startBar
                val isSectionLastBar = bar == section.endBar - 1
                val isSecondToLastBar = bar == section.endBar - 2
                val energy = energyCurve.getEnergyAtBar(bar)

                // Crash Cymbal pada downbeat seksi Drop atau Main Drop
                if (bar == section.startBar && (isDropOrPeak || section.sectionType == SongSectionType.GROOVE)) {
                    events.add(
                        DrumEvent(
                            soundType = DrumSoundType.CRASH,
                            sampleOffset = barStartSample,
                            durationSamples = (sampleRate * 2.0).toInt(),
                            velocity = 0.85f * energy
                        )
                    )
                }

                // INTRO: Drum sangat minimal (hanya hat / snap ringan)
                if (isIntro) {
                    if (energy > 0.25f && barInSec >= 4) {
                        // Closed hat pada setiap offbeat (beat 1.5, 2.5, 3.5, 4.5)
                        for (beat in 0 until 4) {
                            val offbeatSample = barStartSample + (beat * samplesPerBeat) + (samplesPerBeat / 2)
                            events.add(
                                DrumEvent(
                                    soundType = DrumSoundType.CLOSED_HAT,
                                    sampleOffset = offbeatSample,
                                    durationSamples = (sampleRate * 0.05).toInt(),
                                    velocity = 0.40f * drumDensity
                                )
                            )
                        }
                    }
                    continue
                }

                // BREAK: Reduced kick, reduced drums, soft clap, gentle hats (BAGIAN D)
                if (isBreak) {
                    // Reduced kick: hanya ketukan downbeat halus di beat 1
                    val reducedKickVel = 0.45f * energy * drumDensity
                    events.add(
                        DrumEvent(
                            soundType = DrumSoundType.KICK,
                            sampleOffset = barStartSample,
                            durationSamples = (sampleRate * 0.25).toInt(),
                            velocity = reducedKickVel
                        )
                    )
                    // Soft clap di Beat 3
                    val clapSample = barStartSample + (samplesPerBeat * 2)
                    events.add(
                        DrumEvent(
                            soundType = DrumSoundType.CLAP,
                            sampleOffset = clapSample,
                            durationSamples = (sampleRate * 0.15).toInt(),
                            velocity = 0.40f * drumDensity
                        )
                    )
                    // Gentle closed hat di offbeat beat 2 dan beat 4
                    for (beat in listOf(1, 3)) {
                        val offbeatSample = barStartSample + (beat * samplesPerBeat) + (samplesPerBeat / 2)
                        events.add(
                            DrumEvent(
                                soundType = DrumSoundType.CLOSED_HAT,
                                sampleOffset = offbeatSample,
                                durationSamples = (sampleRate * 0.04).toInt(),
                                velocity = 0.35f * drumDensity
                            )
                        )
                    }
                    continue
                }

                // PRE-DROP: Reduced low end, snare roll tension, controlled silence menuju drop (BAGIAN D)
                if (isPreDrop) {
                    // Low end tereduksi: TIDAK ADA kick berat
                    if (barInSec == 0) {
                        // Hanya satu kick penanda awal pre-drop
                        events.add(
                            DrumEvent(
                                soundType = DrumSoundType.KICK,
                                sampleOffset = barStartSample,
                                durationSamples = (sampleRate * 0.20).toInt(),
                                velocity = 0.50f * drumDensity
                            )
                        )
                    }

                    // Snare roll tension di 2 beat awal bar
                    val subBeats = 8
                    val stepSample = (samplesPerBeat * 2) / subBeats
                    for (i in 0 until subBeats) {
                        val vel = (0.4f + 0.5f * (i.toFloat() / subBeats)) * drumDensity
                        events.add(
                            DrumEvent(
                                soundType = DrumSoundType.FILL_SNARE,
                                sampleOffset = barStartSample + i * stepSample,
                                durationSamples = (sampleRate * 0.08).toInt(),
                                velocity = vel
                            )
                        )
                    }
                    // CONTROLLED SILENCE: Dari beat 2.5 hingga akhir bar (menuju drop),
                    // drum diheningkan total untuk menciptakan ruang hisap (tension vacuum)!
                    continue
                }

                // BUILD UP: Riser roll snare bertahap meningkat
                if (isBuildUp) {
                    val progress = barInSec.toFloat() / maxOf(1, section.barCount)
                    if (isSectionLastBar) {
                        // Roll rapat 16th note sebelum drop
                        val subBeats = 16
                        val stepSample = samplesPerBar / subBeats
                        for (i in 0 until subBeats) {
                            // Beri ruang hening mikro di beat terakhir sebelum drop untuk impak ledakan
                            if (i >= 14) continue
                            val vel = 0.5f + 0.5f * (i.toFloat() / subBeats)
                            events.add(
                                DrumEvent(
                                    soundType = DrumSoundType.FILL_SNARE,
                                    sampleOffset = barStartSample + i * stepSample,
                                    durationSamples = (sampleRate * 0.1).toInt(),
                                    velocity = vel * drumDensity
                                )
                            )
                        }
                    } else {
                        // Snare hit setiap beat (4 on the floor snare) meningkat intensitasnya
                        for (b in 0 until 4) {
                            val vel = (0.4f + progress * 0.5f).coerceIn(0.2f, 1.0f)
                            events.add(
                                DrumEvent(
                                    soundType = DrumSoundType.SNARE,
                                    sampleOffset = barStartSample + b * samplesPerBeat,
                                    durationSamples = (sampleRate * 0.15).toInt(),
                                    velocity = vel * drumDensity
                                )
                            )
                        }
                    }
                    continue
                }

                // DROP / GROOVE / OUTRO / PEAK:
                // Variasi ritme DJ Slow khas Indonesia/Asia:
                // Kick padat di Beat 1, Beat 2.5 / 3, Beat 4
                // Snare/Clap di Beat 2 & Beat 4
                // Hi-Hats syncopated & Open Hat di offbeat

                // 1. KICK PATTERN (Variasi 4-bar):
                val kickVel = (0.90f * energy * drumDensity).coerceIn(0.3f, 1.0f)
                // Downbeat beat 1 selalu ada
                events.add(DrumEvent(DrumSoundType.KICK, barStartSample, (sampleRate * 0.35).toInt(), kickVel))

                // Beat 2
                if (isDropOrPeak || energy > 0.6f) {
                    events.add(DrumEvent(DrumSoundType.KICK, barStartSample + samplesPerBeat, (sampleRate * 0.3).toInt(), kickVel * 0.85f))
                }
                // Beat 3
                events.add(DrumEvent(DrumSoundType.KICK, barStartSample + samplesPerBeat * 2, (sampleRate * 0.35).toInt(), kickVel))
                // Beat 4
                events.add(DrumEvent(DrumSoundType.KICK, barStartSample + samplesPerBeat * 3, (sampleRate * 0.3).toInt(), kickVel * 0.85f))

                // Sinkopasi tambahan di bar genap (DJ Slow signature kick syncopation di beat 3.5)
                if (bar % 2 == 1 && !isOutro) {
                    val syncSample = barStartSample + (samplesPerBeat * 2) + (samplesPerBeat / 2)
                    events.add(DrumEvent(DrumSoundType.KICK, syncSample, (sampleRate * 0.25).toInt(), kickVel * 0.75f))
                }

                // 2. SNARE & CLAP PATTERN (Beat 2 & Beat 4)
                val snareVel = (0.80f * energy * drumDensity).coerceIn(0.2f, 1.0f)
                events.add(DrumEvent(DrumSoundType.SNARE, barStartSample + samplesPerBeat, (sampleRate * 0.2).toInt(), snareVel))
                events.add(DrumEvent(DrumSoundType.CLAP, barStartSample + samplesPerBeat * 3, (sampleRate * 0.25).toInt(), snareVel * 0.9f))

                // 3. HI-HATS & PERCUSSION:
                // Closed Hat pada setiap 8th-note
                val hatVel = 0.55f * energy * drumDensity
                for (b in 0 until 8) {
                    val hatSample = barStartSample + (b * samplesPerBeat / 2)
                    events.add(DrumEvent(DrumSoundType.CLOSED_HAT, hatSample, (sampleRate * 0.05).toInt(), hatVel))
                }

                // Open Hat pada off-beats (1.5, 2.5, 3.5, 4.5)
                if (isDropOrPeak) {
                    for (b in 0 until 4) {
                        val openHatSample = barStartSample + (b * samplesPerBeat) + (samplesPerBeat / 2)
                        events.add(DrumEvent(DrumSoundType.OPEN_HAT, openHatSample, (sampleRate * 0.15).toInt(), hatVel * 0.85f))
                    }
                }

                // 4. DRUM FILL pada bar akhir frase 4-bar
                if (isSectionLastBar || bar % 4 == 3) {
                    val fillStart = barStartSample + samplesPerBeat * 3
                    val fillSteps = 4
                    val stepSize = samplesPerBeat / fillSteps
                    for (f in 0 until fillSteps) {
                        events.add(
                            DrumEvent(
                                soundType = DrumSoundType.FILL_SNARE,
                                sampleOffset = fillStart + f * stepSize,
                                durationSamples = (sampleRate * 0.1).toInt(),
                                velocity = 0.75f * drumDensity
                            )
                        )
                    }
                }
            }
        }

        return events.sortedBy { it.sampleOffset }
    }

    /**
     * Merender satu blok drum secara streaming langsung ke [outBuffer] pada [offset].
     * Menghilangkan kebutuhan alokasi array PCM track penuh dan memungkinkan reuse buffer.
     */
    fun renderDrumBlock(
        events: List<DrumEvent>,
        startFrame: Long,
        frameCount: Int,
        sampleRate: Int,
        outBuffer: FloatArray,
        offset: Int = 0
    ) {
        val channels = 2
        val endFrame = startFrame + frameCount

        for (event in events) {
            val eventStart = event.sampleOffset
            val maxDuration = when (event.soundType) {
                DrumSoundType.KICK -> (sampleRate * 0.35).toInt()
                DrumSoundType.SNARE, DrumSoundType.FILL_SNARE -> (sampleRate * 0.22).toInt()
                DrumSoundType.CLAP -> (sampleRate * 0.25).toInt()
                DrumSoundType.CLOSED_HAT -> (sampleRate * 0.045).toInt()
                DrumSoundType.OPEN_HAT -> (sampleRate * 0.20).toInt()
                DrumSoundType.CRASH -> (sampleRate * 2.2).toInt()
                DrumSoundType.PERCUSSION -> (sampleRate * 0.12).toInt()
            }
            val duration = min(event.durationSamples, maxDuration)
            val eventEnd = eventStart + duration
            if (eventEnd <= startFrame || eventStart >= endFrame) continue

            val overlapStart = maxOf(startFrame, eventStart)
            val overlapEnd = minOf(endFrame, eventEnd)

            val vel = event.velocity
            val sound = event.soundType

            when (sound) {
                DrumSoundType.KICK -> {
                    for (f in overlapStart until overlapEnd) {
                        val i = (f - eventStart).toInt()
                        val outIdx = offset + ((f - startFrame) * channels).toInt()
                        if (outIdx + 1 >= outBuffer.size) break

                        val t = i.toDouble() / sampleRate
                        val env = exp(-t * 12.0)
                        val pitch = 45.0 + 95.0 * exp(-t * 35.0)
                        val phase = 2.0 * PI * (45.0 * t - (95.0 / 35.0) * (exp(-t * 35.0) - 1.0))
                        val sampleVal = (sin(phase) * env * vel * 0.85f).toFloat()

                        val transient = if (t < 0.015) {
                            val noise = (((i * 1103515245 + 12345) and 0x7FFFFFFF).toFloat() / 0x3FFFFFFF - 1.0f)
                            (noise * (1.0 - t / 0.015) * 0.2f * vel).toFloat()
                        } else 0f

                        val mixed = sampleVal + transient
                        outBuffer[outIdx] += mixed
                        outBuffer[outIdx + 1] += mixed
                    }
                }

                DrumSoundType.SNARE, DrumSoundType.FILL_SNARE -> {
                    for (f in overlapStart until overlapEnd) {
                        val i = (f - eventStart).toInt()
                        val outIdx = offset + ((f - startFrame) * channels).toInt()
                        if (outIdx + 1 >= outBuffer.size) break

                        val t = i.toDouble() / sampleRate
                        val bodyEnv = exp(-t * 22.0)
                        val noiseEnv = exp(-t * 14.0)
                        val tone = sin(2.0 * PI * 180.0 * t) * bodyEnv * 0.45
                        val noise = (((i * 1103515245 + 12345) and 0x7FFFFFFF).toFloat() / 0x3FFFFFFF - 1.0f) * noiseEnv * 0.55
                        val sampleVal = ((tone + noise) * vel * 0.65f).toFloat()

                        outBuffer[outIdx] += sampleVal
                        outBuffer[outIdx + 1] += sampleVal
                    }
                }

                DrumSoundType.CLAP -> {
                    for (f in overlapStart until overlapEnd) {
                        val i = (f - eventStart).toInt()
                        val outIdx = offset + ((f - startFrame) * channels).toInt()
                        if (outIdx + 1 >= outBuffer.size) break

                        val t = i.toDouble() / sampleRate
                        val noise = ((i * 1103515245 + 12345) and 0x7FFFFFFF).toFloat() / 0x3FFFFFFF - 1.0f
                        val env = exp(-t * 16.0)

                        val burstMultiplier = when {
                            t < 0.010 -> 0.8
                            t in 0.012..0.022 -> 0.9
                            t in 0.024..0.034 -> 1.0
                            else -> 0.5
                        }
                        val sampleVal = (noise * env * burstMultiplier * vel * 0.60f).toFloat()
                        outBuffer[outIdx] += sampleVal
                        outBuffer[outIdx + 1] += sampleVal
                    }
                }

                DrumSoundType.CLOSED_HAT -> {
                    for (f in overlapStart until overlapEnd) {
                        val i = (f - eventStart).toInt()
                        val outIdx = offset + ((f - startFrame) * channels).toInt()
                        if (outIdx + 1 >= outBuffer.size) break

                        val t = i.toDouble() / sampleRate
                        val env = exp(-t * 65.0)
                        val rawNoise = ((i * 1103515245 + 12345) and 0x7FFFFFFF).toFloat() / 0x3FFFFFFF - 1.0f
                        val prevNoise = (((i - 1) * 1103515245 + 12345) and 0x7FFFFFFF).toFloat() / 0x3FFFFFFF - 1.0f
                        val hpNoise = rawNoise - prevNoise

                        val sampleVal = (hpNoise * env * vel * 0.35f).toFloat()
                        outBuffer[outIdx] += sampleVal
                        outBuffer[outIdx + 1] += sampleVal
                    }
                }

                DrumSoundType.OPEN_HAT -> {
                    for (f in overlapStart until overlapEnd) {
                        val i = (f - eventStart).toInt()
                        val outIdx = offset + ((f - startFrame) * channels).toInt()
                        if (outIdx + 1 >= outBuffer.size) break

                        val t = i.toDouble() / sampleRate
                        val env = exp(-t * 14.0)
                        val rawNoise = ((i * 1103515245 + 12345) and 0x7FFFFFFF).toFloat() / 0x3FFFFFFF - 1.0f
                        val prevNoise = (((i - 1) * 1103515245 + 12345) and 0x7FFFFFFF).toFloat() / 0x3FFFFFFF - 1.0f
                        val hpNoise = rawNoise - prevNoise

                        val sampleVal = (hpNoise * env * vel * 0.40f).toFloat()
                        outBuffer[outIdx] += sampleVal
                        outBuffer[outIdx + 1] += sampleVal
                    }
                }

                DrumSoundType.CRASH -> {
                    for (f in overlapStart until overlapEnd) {
                        val i = (f - eventStart).toInt()
                        val outIdx = offset + ((f - startFrame) * channels).toInt()
                        if (outIdx + 1 >= outBuffer.size) break

                        val t = i.toDouble() / sampleRate
                        val env = exp(-t * 2.2).toFloat()
                        val noiseL = (((i * 1103515245 + 12345) and 0x7FFFFFFF).toFloat() / 0x3FFFFFFF - 1.0f) * env * vel * 0.40f
                        val noiseR = ((((i + 777) * 1103515245 + 12345) and 0x7FFFFFFF).toFloat() / 0x3FFFFFFF - 1.0f) * env * vel * 0.40f

                        outBuffer[outIdx] += noiseL
                        outBuffer[outIdx + 1] += noiseR
                    }
                }

                DrumSoundType.PERCUSSION -> {
                    for (f in overlapStart until overlapEnd) {
                        val i = (f - eventStart).toInt()
                        val outIdx = offset + ((f - startFrame) * channels).toInt()
                        if (outIdx + 1 >= outBuffer.size) break

                        val t = i.toDouble() / sampleRate
                        val env = exp(-t * 30.0)
                        val sampleVal = (sin(2.0 * PI * 420.0 * t) * env * vel * 0.50f).toFloat()

                        outBuffer[outIdx] += sampleVal
                        outBuffer[outIdx + 1] += sampleVal
                    }
                }
            }
        }
    }

    /**
     * Merender seluruh drum events menjadi buffer PCM Stereo Float32 murni (DSP) menggunakan pemrosesan blok.
     */
    fun renderDrums(
        events: List<DrumEvent>,
        totalSamples: Long,
        sampleRate: Int
    ): AudioPcmData {
        val channels = 2
        val safeSamples = max(44100L, totalSamples)
        val totalFloats = (safeSamples * channels).toInt().coerceAtLeast(channels)
        val buffer = FloatArray(totalFloats)

        val blockFrames = 16384
        var current = 0L
        while (current < safeSamples) {
            val count = minOf(blockFrames.toLong(), safeSamples - current).toInt()
            val offset = (current * channels).toInt()
            renderDrumBlock(events, current, count, sampleRate, buffer, offset)
            current += count
        }

        return AudioPcmData(
            samples = buffer,
            sampleRate = sampleRate,
            channels = channels
        )
    }
}
