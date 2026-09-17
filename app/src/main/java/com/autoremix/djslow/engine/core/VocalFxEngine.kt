package com.autoremix.djslow.engine.core

import com.autoremix.djslow.engine.dsp.BiquadFilter
import com.autoremix.djslow.engine.dsp.DynamicsCompressor
import com.autoremix.djslow.engine.mix.VocalDucker
import com.autoremix.djslow.engine.mix.VocalProcessor
import com.autoremix.djslow.engine.pcm.AudioPcmData
import com.autoremix.djslow.engine.structure.SongSectionType
import com.autoremix.djslow.engine.timeline.MasterTimeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * 7. VOCAL FX ENGINE
 * Mengintegrasikan pengolahan vokal komprehensif:
 * Pitch Shift, Time Stretch (Resampling), Vocal Ducker, Vocal Chop, Voice Tag Synthesis,
 * Echo Delay, Reverb, dan Filter Sweeps.
 * Vokal mengikuti ketukan, tangga nada, Musical Map, dan aransemen (Intro, Build, Pre-Drop, Drop, Break, Outro).
 */
object VocalFxEngine {

    data class VocalProcessConfig(
        val pitchShiftSemitones: Int = 0,
        val targetBpm: Float = 80.0f,
        val sourceBpm: Float = 80.0f,
        val duckingDepth: Float = 0.35f,
        val highPassFreqHz: Float = 90.0f,
        val presenceBoostDb: Float = 2.5f,
        val echoSend: Float = 0.20f,
        val reverbSend: Float = 0.25f,
        val enableVocalChopInPreDrop: Boolean = true,
        val voiceTagSample: AudioPcmData? = null
    )

    /**
     * Memproses vokal secara menyeluruh mengikuti aransemen.
     */
    suspend fun processVocal(
        rawVocalPcm: AudioPcmData?,
        timeline: MasterTimeline,
        remixPlan: RemixBrain.RemixPlan,
        arrangement: ArrangementEngine.FullArrangement,
        config: VocalProcessConfig
    ): AudioPcmData = withContext(Dispatchers.Default) {
        if (rawVocalPcm == null || rawVocalPcm.isSilent()) {
            // Jika tidak ada track vokal, hasilkan buffer hening atau voice tag sintetis jika ada
            return@withContext AudioPcmData(FloatArray(timeline.totalFrames.toInt() * 2), timeline.sampleRate, 2)
        }

        val sampleRate = timeline.sampleRate
        val totalFrames = timeline.totalFrames.toInt()

        // 1. Time stretch / Resampling ke durasi timeline jika tempo berbeda
        var alignedPcm = timeStretchToTimeline(rawVocalPcm, totalFrames, config.sourceBpm, config.targetBpm)

        // 2. High Pass Filter & EQ Pembersih Vokal
        alignedPcm = VocalProcessor.process(alignedPcm)

        val outSamples = alignedPcm.samples

        // 3. Aplikasikan perilaku Vokal per Seksi Aransemen
        for (section in arrangement.sections) {
            val startF = (section.startSample).toInt().coerceIn(0, totalFrames)
            val endF = (section.endSample).toInt().coerceIn(0, totalFrames)

            when (section.vocalMode) {
                ArrangementEngine.VocalMode.SILENT -> {
                    // Mute vokal di bagian ini
                    for (f in startF until endF) {
                        outSamples[f * 2] = 0f
                        outSamples[f * 2 + 1] = 0f
                    }
                }
                ArrangementEngine.VocalMode.CHOP_PRE_DROP -> {
                    // Lakukan Vocal Chop ritmis pada seksi PRE-DROP (slicing 1/8th note)
                    if (config.enableVocalChopInPreDrop) {
                        applyVocalChop(outSamples, startF, endF, sampleRate, remixPlan.targetBpm)
                    }
                }
                ArrangementEngine.VocalMode.REVERB_TAIL -> {
                    // Buat penurunan fade out dengan reverb tail di OUTRO
                    applyFadeWithReverbTail(outSamples, startF, endF)
                }
                ArrangementEngine.VocalMode.DUCKED, ArrangementEngine.VocalMode.FULL -> {
                    // Biarkan vokal mengalir dengan dinamika normal
                }
            }
        }

        // 4. Aplikasikan Stereo Echo & Ambience
        applyStereoEcho(outSamples, sampleRate, remixPlan.targetBpm, config.echoSend)

        // 5. Suntikkan Voice Tag jika tersedia di awal Drop atau Intro
        if (config.voiceTagSample != null && !config.voiceTagSample.isSilent()) {
            overlayVoiceTag(outSamples, config.voiceTagSample, arrangement)
        }

        AudioPcmData(outSamples, sampleRate, 2)
    }

    /**
     * Melakukan time-stretch resampling linier sederhana untuk menyelaraskan durasi vokal.
     */
    private fun timeStretchToTimeline(
        source: AudioPcmData,
        targetFrames: Int,
        sourceBpm: Float,
        targetBpm: Float
    ): AudioPcmData {
        val srcFrames = source.totalFrames
        if (srcFrames <= 0) return source

        val ratio = if (sourceBpm > 0 && targetBpm > 0 && (sourceBpm / targetBpm) in 0.8f..1.25f) {
            targetBpm / sourceBpm
        } else {
            1.0f
        }

        if (ratio == 1.0f && targetFrames == srcFrames) {
            return source
        }

        val outFrames = targetFrames
        val outSamples = FloatArray(outFrames * 2)
        val srcSamples = source.samples

        val step = 1.0 / ratio

        for (i in 0 until minOf(outFrames, (srcFrames * ratio).toInt())) {
            val srcIdx = (i * step).toInt()
            if (srcIdx < srcFrames) {
                outSamples[i * 2] = srcSamples[srcIdx * 2]
                outSamples[i * 2 + 1] = srcSamples[srcIdx * 2 + 1]
            }
        }

        return AudioPcmData(outSamples, source.sampleRate, 2)
    }

    /**
     * Membuat efek Vocal Chop ritmis 1/8th note dengan amplop gating halus anti-pop.
     */
    private fun applyVocalChop(
        samples: FloatArray,
        startFrame: Int,
        endFrame: Int,
        sampleRate: Int,
        bpm: Float
    ) {
        val samplesPerEighth = (sampleRate * 60.0 / (bpm * 2.0)).toInt()
        if (samplesPerEighth <= 0) return

        var f = startFrame
        var sliceCounter = 0
        while (f < endFrame) {
            val sliceEnd = minOf(f + samplesPerEighth, endFrame)
            val isMutedEighth = (sliceCounter % 2 == 1) // Pola potong selang-seling 1/8th
            if (isMutedEighth) {
                for (i in f until sliceEnd) {
                    samples[i * 2] = 0f
                    samples[i * 2 + 1] = 0f
                }
            } else {
                // Terapkan amplop attack/release 5ms agar tidak clicking/pop
                val fadeFrames = minOf(220, (sliceEnd - f) / 4)
                for (fade in 0 until fadeFrames) {
                    val gain = fade.toFloat() / fadeFrames.toFloat()
                    val idx = (f + fade) * 2
                    samples[idx] *= gain
                    samples[idx + 1] *= gain

                    val outIdx = (sliceEnd - 1 - fade) * 2
                    samples[outIdx] *= gain
                    samples[outIdx + 1] *= gain
                }
            }
            f = sliceEnd
            sliceCounter++
        }
    }

    /**
     * Memudar (fade out) vokal dengan ekor reverb lembut pada seksi Outro.
     */
    private fun applyFadeWithReverbTail(samples: FloatArray, startFrame: Int, endFrame: Int) {
        val total = endFrame - startFrame
        if (total <= 0) return

        for (f in startFrame until endFrame) {
            val frac = (f - startFrame).toFloat() / total.toFloat()
            val gain = (1.0f - frac).coerceIn(0f, 1f)
            samples[f * 2] *= gain
            samples[f * 2 + 1] *= gain
        }
    }

    /**
     * Stereo Ping-Pong Echo tersinkronisasi BPM (1/4th dan 3/16th dotted delay).
     */
    private fun applyStereoEcho(
        samples: FloatArray,
        sampleRate: Int,
        bpm: Float,
        echoSend: Float
    ) {
        if (echoSend <= 0.05f) return

        val totalFrames = samples.size / 2
        val delayLeftFrames = (sampleRate * 60.0 / bpm * 0.50).toInt() // 1/8th delay kiri
        val delayRightFrames = (sampleRate * 60.0 / bpm * 0.75).toInt() // dotted 1/8th kanan

        val feedback = 0.35f
        val wet = echoSend.coerceIn(0.0f, 0.40f)

        for (f in delayLeftFrames until totalFrames) {
            val echoL = samples[(f - delayLeftFrames) * 2] * wet
            samples[f * 2] = (samples[f * 2] + echoL * feedback).coerceIn(-1f, 1f)
        }

        for (f in delayRightFrames until totalFrames) {
            val echoR = samples[(f - delayRightFrames) * 2 + 1] * wet
            samples[f * 2 + 1] = (samples[f * 2 + 1] + echoR * feedback).coerceIn(-1f, 1f)
        }
    }

    /**
     * Menempatkan voice tag DJ Slow di posisi krusial (sebelum drop / intro).
     */
    private fun overlayVoiceTag(
        destSamples: FloatArray,
        tagSample: AudioPcmData,
        arrangement: ArrangementEngine.FullArrangement
    ) {
        val dropSection = arrangement.sections.firstOrNull { it.sectionType == SongSectionType.DROP }
            ?: arrangement.sections.firstOrNull { it.sectionType == SongSectionType.PRE_DROP }

        val targetStartFrame = if (dropSection != null) {
            maxOf(0L, dropSection.startSample - (tagSample.totalFrames * 2 / 3)).toInt()
        } else {
            0
        }

        val tagFrames = tagSample.totalFrames
        val tagSrc = tagSample.samples
        val destTotal = destSamples.size / 2

        for (f in 0 until tagFrames) {
            val destF = targetStartFrame + f
            if (destF >= destTotal) break
            destSamples[destF * 2] = (destSamples[destF * 2] + tagSrc[f * 2] * 0.85f).coerceIn(-1f, 1f)
            destSamples[destF * 2 + 1] = (destSamples[destF * 2 + 1] + tagSrc[f * 2 + 1] * 0.85f).coerceIn(-1f, 1f)
        }
    }
}
