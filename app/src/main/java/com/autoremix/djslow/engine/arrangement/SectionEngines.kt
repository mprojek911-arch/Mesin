package com.autoremix.djslow.engine.arrangement

import com.autoremix.djslow.engine.pcm.AudioPcmData
import com.autoremix.djslow.engine.structure.SongSection
import com.autoremix.djslow.engine.structure.SongSectionType
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Event efek transisi dan FX risers.
 */
data class TransitionFxEvent(
    val type: TransitionFxType,
    val sampleOffset: Long,
    val durationSamples: Int,
    val velocity: Float
)

enum class TransitionFxType {
    NOISE_RISER, REVERSE_SWEEP, DOWNBEAT_IMPACT, SILENCE_GAP
}

/**
 * Mesin-mesin khusus seksi lagu dan transisi cerdas Tahap 4:
 * - BuildUpEngine
 * - AutoDropEngine
 * - BreakdownEngine
 * - FinalBuildEngine
 * - PeakEnergyEngine
 * - FinalDropEngine
 * - AutoOutroEngine
 * - AutoTransitionEngine
 */
object SectionEngines {

    /**
     * BuildUpEngine:
     * Menghasilkan Noise Riser sweep yang menaik frekuensinya secara eksponensial
     * menuju drop.
     */
    fun generateRiserFx(
        startSample: Long,
        durationSamples: Int,
        sampleRate: Int,
        velocity: Float = 0.65f
    ): AudioPcmData {
        val channels = 2
        val safeDur = max(1000, durationSamples)
        val buffer = FloatArray(safeDur * channels)
        val random = Random(12345)

        for (i in 0 until safeDur) {
            val t = i.toDouble() / safeDur
            // Kurva kenaikan volume & frekuensi (ekor membesar di akhir)
            val ampEnv = (t * t * velocity).toFloat()
            // Modulasi pitch sweep
            val pitch = 200.0 + 3500.0 * (t * t)
            val tone = sin(2.0 * PI * pitch * i / sampleRate) * 0.3
            val noise = (random.nextFloat() * 2f - 1f) * 0.7

            val sampleVal = ((tone + noise) * ampEnv * 0.45f).toFloat()

            // Di 100ms terakhir sebelum drop, terapkan hening singkat (breath gap) untuk kontras ledakan drop
            val isGap = (safeDur - i) < (sampleRate * 0.10)
            val finalVal = if (isGap) 0.0f else sampleVal

            val idx = i * channels
            buffer[idx] = finalVal
            buffer[idx + 1] = finalVal
        }

        return AudioPcmData(
            samples = buffer,
            sampleRate = sampleRate,
            channels = channels
        )
    }

    /**
     * AutoTransitionEngine:
     * Menghasilkan event FX transisi otomatis pada setiap pergantian seksi.
     */
    fun scheduleTransitionEvents(
        sections: List<SongSection>,
        sampleRate: Int,
        samplesPerBar: Long
    ): List<TransitionFxEvent> {
        val events = ArrayList<TransitionFxEvent>()

        for ((idx, sec) in sections.withIndex()) {
            val nextSec = sections.getOrNull(idx + 1)
            val isNextDrop = nextSec?.sectionType in listOf(
                SongSectionType.DROP,
                SongSectionType.MAIN_DROP,
                SongSectionType.PEAK,
                SongSectionType.FINAL_DROP
            )

            // Ambience FX di awal seksi Break (BAGIAN D)
            if (sec.sectionType in listOf(SongSectionType.BREAK, SongSectionType.BREAKDOWN)) {
                events.add(
                    TransitionFxEvent(
                        type = TransitionFxType.REVERSE_SWEEP,
                        sampleOffset = sec.startSample,
                        durationSamples = (sampleRate * 2.0).toInt(),
                        velocity = 0.50f
                    )
                )
            }

            // Jika seksi berikutnya adalah Drop, buat riser di bar terakhir seksi sekarang
            if (isNextDrop && nextSec != null) {
                val riserDuration = (samplesPerBar * 2).toInt().coerceAtMost((sampleRate * 4.0).toInt())
                val riserStart = max(0L, nextSec.startSample - riserDuration)
                events.add(
                    TransitionFxEvent(
                        type = TransitionFxType.NOISE_RISER,
                        sampleOffset = riserStart,
                        durationSamples = riserDuration,
                        velocity = 0.70f
                    )
                )

                // Downbeat impact tepat di frame 0 drop
                events.add(
                    TransitionFxEvent(
                        type = TransitionFxType.DOWNBEAT_IMPACT,
                        sampleOffset = nextSec.startSample,
                        durationSamples = (sampleRate * 1.5).toInt(),
                        velocity = 0.85f
                    )
                )
            }
        }

        return events
    }

    /**
     * Merender potongan transisi FX secara chunk-by-chunk untuk penghematan memori.
     */
    fun renderTransitionChunk(
        events: List<TransitionFxEvent>,
        chunkStartFrame: Long,
        frameCount: Int,
        sampleRate: Int
    ): AudioPcmData {
        val channels = 2
        val buffer = FloatArray(frameCount * channels)
        val chunkEndFrame = chunkStartFrame + frameCount
        val random = Random(999 + (chunkStartFrame % 10000).toInt())

        for (event in events) {
            val eventStart = event.sampleOffset
            val eventEnd = event.sampleOffset + event.durationSamples

            if (eventEnd <= chunkStartFrame || eventStart >= chunkEndFrame) continue

            val dur = event.durationSamples
            if (dur <= 0) continue

            val startInChunk = maxOf(0L, eventStart - chunkStartFrame).toInt()
            val endInChunk = minOf(frameCount.toLong(), eventEnd - chunkStartFrame).toInt()

            for (f in startInChunk until endInChunk) {
                val absFrame = chunkStartFrame + f
                val sampleInEvent = (absFrame - eventStart).toInt()
                if (sampleInEvent < 0 || sampleInEvent >= dur) continue

                val outIdx = f * channels
                val t = sampleInEvent.toDouble() / dur

                when (event.type) {
                    TransitionFxType.NOISE_RISER -> {
                        val amp = (t * t * event.velocity * 0.35f).toFloat()
                        val isGap = (dur - sampleInEvent) < (sampleRate * 0.10)
                        if (!isGap) {
                            val noiseL = (random.nextFloat() * 2f - 1f) * amp
                            val noiseR = (random.nextFloat() * 2f - 1f) * amp
                            buffer[outIdx] += noiseL
                            buffer[outIdx + 1] += noiseR
                        }
                    }
                    TransitionFxType.DOWNBEAT_IMPACT -> {
                        val tSec = sampleInEvent.toDouble() / sampleRate
                        val env = exp(-tSec * 3.5)
                        val sub = sin(2.0 * PI * 55.0 * tSec) * env * 0.5
                        val splash = (random.nextFloat() * 2f - 1f) * env * 0.35
                        val v = ((sub + splash) * event.velocity * 0.45f).toFloat()
                        buffer[outIdx] += v
                        buffer[outIdx + 1] += v
                    }
                    TransitionFxType.REVERSE_SWEEP -> {
                        val env = sin(t * PI).toFloat()
                        val noise = (random.nextFloat() * 2f - 1f) * env * event.velocity * 0.25f
                        buffer[outIdx] += noise
                        buffer[outIdx + 1] += noise
                    }
                    else -> {}
                }
            }
        }

        return AudioPcmData(
            samples = buffer,
            sampleRate = sampleRate,
            channels = channels
        )
    }

    /**
     * Merender seluruh efek transisi menjadi buffer PCM Stereo.
     */
    fun renderTransitions(
        events: List<TransitionFxEvent>,
        totalSamples: Long,
        sampleRate: Int
    ): AudioPcmData {
        val channels = 2
        val safeSamples = minOf(totalSamples, 44100L * 30L).coerceAtLeast(44100L)
        val totalFloats = (safeSamples * channels).toInt().coerceAtLeast(channels)
        val buffer = FloatArray(totalFloats)
        val random = Random(999)

        for (event in events) {
            val startIdx = (event.sampleOffset * channels).toInt()
            if (startIdx >= buffer.size) continue

            val dur = min(event.durationSamples, (totalSamples - event.sampleOffset).toInt())
            if (dur <= 0) continue

            when (event.type) {
                TransitionFxType.NOISE_RISER -> {
                    for (i in 0 until dur) {
                        val outIdx = startIdx + i * channels
                        if (outIdx + 1 >= buffer.size) break

                        val t = i.toDouble() / dur
                        val amp = (t * t * event.velocity * 0.35f).toFloat()
                        // 100ms silence gap sebelum drop
                        val isGap = (dur - i) < (sampleRate * 0.10)
                        if (!isGap) {
                            val noiseL = (random.nextFloat() * 2f - 1f) * amp
                            val noiseR = (random.nextFloat() * 2f - 1f) * amp
                            buffer[outIdx] += noiseL
                            buffer[outIdx + 1] += noiseR
                        }
                    }
                }

                TransitionFxType.DOWNBEAT_IMPACT -> {
                    // Sub boom impact (60 Hz decaying) + stereo white noise crash
                    for (i in 0 until dur) {
                        val outIdx = startIdx + i * channels
                        if (outIdx + 1 >= buffer.size) break

                        val t = i.toDouble() / sampleRate
                        val env = exp(-t * 3.5)
                        val sub = sin(2.0 * PI * 55.0 * t) * env * 0.5
                        val splash = (random.nextFloat() * 2f - 1f) * env * 0.35

                        val v = ((sub + splash) * event.velocity * 0.45f).toFloat()
                        buffer[outIdx] += v
                        buffer[outIdx + 1] += v
                    }
                }

                TransitionFxType.REVERSE_SWEEP -> {
                    // Soft ethereal ambience sweep (filtered noise fading in then out)
                    for (i in 0 until dur) {
                        val outIdx = startIdx + i * channels
                        if (outIdx + 1 >= buffer.size) break

                        val t = i.toDouble() / dur
                        val env = sin(t * PI).toFloat()
                        val noise = (random.nextFloat() * 2f - 1f) * env * event.velocity * 0.25f
                        buffer[outIdx] += noise
                        buffer[outIdx + 1] += noise
                    }
                }

                else -> {}
            }
        }

        return AudioPcmData(
            samples = buffer,
            sampleRate = sampleRate,
            channels = channels
        )
    }
}
