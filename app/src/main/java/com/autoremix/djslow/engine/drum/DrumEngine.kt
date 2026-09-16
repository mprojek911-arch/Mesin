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
            val isBreakdown = section.sectionType == SongSectionType.BREAKDOWN
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

                // BREAKDOWN: Kick & drum berat ditiadakan, hanya perkusi atmosferik
                if (isBreakdown) {
                    if (bar % 2 == 1) {
                        val clapSample = barStartSample + (samplesPerBeat * 2) // Beat 3
                        events.add(
                            DrumEvent(
                                soundType = DrumSoundType.CLAP,
                                sampleOffset = clapSample,
                                durationSamples = (sampleRate * 0.15).toInt(),
                                velocity = 0.45f * drumDensity
                            )
                        )
                    }
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
     * Merender seluruh drum events menjadi buffer PCM Stereo Float32 murni (DSP).
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

        val random = Random(42)

        for (event in events) {
            val startIdx = (event.sampleOffset * channels).toInt()
            if (startIdx >= buffer.size) continue

            val vel = event.velocity
            val sound = event.soundType

            when (sound) {
                DrumSoundType.KICK -> {
                    // Sine sweep dari 140 Hz ke 45 Hz dengan pitch envelope eksponensial
                    val duration = min(event.durationSamples, (sampleRate * 0.35).toInt())
                    var phase = 0.0
                    for (i in 0 until duration) {
                        val outIdx = startIdx + i * channels
                        if (outIdx + 1 >= buffer.size) break

                        val t = i.toDouble() / sampleRate
                        val env = exp(-t * 12.0)
                        val pitch = 45.0 + 95.0 * exp(-t * 35.0)
                        phase += 2.0 * PI * pitch / sampleRate
                        val sampleVal = (sin(phase) * env * vel * 0.85f).toFloat()

                        // Tambahkan sedikit punch transient di 15ms awal
                        val transient = if (t < 0.015) ((random.nextFloat() * 2f - 1f) * (1.0 - t / 0.015) * 0.2f * vel).toFloat() else 0f

                        val mixed = sampleVal + transient
                        buffer[outIdx] += mixed
                        buffer[outIdx + 1] += mixed
                    }
                }

                DrumSoundType.SNARE, DrumSoundType.FILL_SNARE -> {
                    // Body sine (180 Hz) + shaped noise
                    val duration = min(event.durationSamples, (sampleRate * 0.22).toInt())
                    var phase = 0.0
                    for (i in 0 until duration) {
                        val outIdx = startIdx + i * channels
                        if (outIdx + 1 >= buffer.size) break

                        val t = i.toDouble() / sampleRate
                        val bodyEnv = exp(-t * 22.0)
                        val noiseEnv = exp(-t * 14.0)
                        val tone = sin(phase) * bodyEnv * 0.45
                        phase += 2.0 * PI * 180.0 / sampleRate

                        val noise = (random.nextFloat() * 2f - 1f) * noiseEnv * 0.55
                        val sampleVal = ((tone + noise) * vel * 0.65f).toFloat()

                        buffer[outIdx] += sampleVal
                        buffer[outIdx + 1] += sampleVal
                    }
                }

                DrumSoundType.CLAP -> {
                    // Multi-burst impulse (3 mikro klik) + ekor noise
                    val duration = min(event.durationSamples, (sampleRate * 0.25).toInt())
                    for (i in 0 until duration) {
                        val outIdx = startIdx + i * channels
                        if (outIdx + 1 >= buffer.size) break

                        val t = i.toDouble() / sampleRate
                        val noise = (random.nextFloat() * 2f - 1f)
                        val env = exp(-t * 16.0)

                        // Mikro burst di 0ms, 12ms, 24ms
                        val burstMultiplier = when {
                            t < 0.010 -> 0.8
                            t in 0.012..0.022 -> 0.9
                            t in 0.024..0.034 -> 1.0
                            else -> 0.5
                        }

                        val sampleVal = (noise * env * burstMultiplier * vel * 0.60f).toFloat()
                        buffer[outIdx] += sampleVal
                        buffer[outIdx + 1] += sampleVal
                    }
                }

                DrumSoundType.CLOSED_HAT -> {
                    // Noise frekuensi tinggi dengan decay sangat cepat (35ms)
                    val duration = min(event.durationSamples, (sampleRate * 0.045).toInt())
                    var prevN = 0.0f
                    for (i in 0 until duration) {
                        val outIdx = startIdx + i * channels
                        if (outIdx + 1 >= buffer.size) break

                        val t = i.toDouble() / sampleRate
                        val env = exp(-t * 65.0)
                        val rawNoise = random.nextFloat() * 2f - 1f
                        // High-pass diferensial
                        val hpNoise = rawNoise - prevN
                        prevN = rawNoise

                        val sampleVal = (hpNoise * env * vel * 0.35f).toFloat()
                        buffer[outIdx] += sampleVal
                        buffer[outIdx + 1] += sampleVal
                    }
                }

                DrumSoundType.OPEN_HAT -> {
                    // Noise frekuensi tinggi decay 180ms
                    val duration = min(event.durationSamples, (sampleRate * 0.20).toInt())
                    var prevN = 0.0f
                    for (i in 0 until duration) {
                        val outIdx = startIdx + i * channels
                        if (outIdx + 1 >= buffer.size) break

                        val t = i.toDouble() / sampleRate
                        val env = exp(-t * 14.0)
                        val rawNoise = random.nextFloat() * 2f - 1f
                        val hpNoise = rawNoise - prevN
                        prevN = rawNoise

                        val sampleVal = (hpNoise * env * vel * 0.40f).toFloat()
                        buffer[outIdx] += sampleVal
                        buffer[outIdx + 1] += sampleVal
                    }
                }

                DrumSoundType.CRASH -> {
                    // Noise shimmer lebar stereo dengan decay 2.2 detik
                    val duration = min(event.durationSamples, (sampleRate * 2.2).toInt())
                    for (i in 0 until duration) {
                        val outIdx = startIdx + i * channels
                        if (outIdx + 1 >= buffer.size) break

                        val t = i.toDouble() / sampleRate
                        val env = exp(-t * 2.2)
                        val noiseL = ((random.nextFloat() * 2f - 1f) * env * vel * 0.40f).toFloat()
                        val noiseR = ((random.nextFloat() * 2f - 1f) * env * vel * 0.40f).toFloat()

                        buffer[outIdx] += noiseL
                        buffer[outIdx + 1] += noiseR
                    }
                }

                DrumSoundType.PERCUSSION -> {
                    // Bongo / rimshot wood tone
                    val duration = min(event.durationSamples, (sampleRate * 0.12).toInt())
                    var phase = 0.0
                    for (i in 0 until duration) {
                        val outIdx = startIdx + i * channels
                        if (outIdx + 1 >= buffer.size) break

                        val t = i.toDouble() / sampleRate
                        val env = exp(-t * 30.0)
                        phase += 2.0 * PI * 420.0 / sampleRate
                        val sampleVal = (sin(phase) * env * vel * 0.50f).toFloat()

                        buffer[outIdx] += sampleVal
                        buffer[outIdx + 1] += sampleVal
                    }
                }
            }
        }

        return AudioPcmData(
            samples = buffer,
            sampleRate = sampleRate,
            channels = channels
        )
    }
}
