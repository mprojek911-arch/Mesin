package com.autoremix.djslow.engine.mix

import com.autoremix.djslow.engine.dsp.BiquadFilter
import com.autoremix.djslow.engine.dsp.DynamicsCompressor
import com.autoremix.djslow.engine.dsp.StereoEngine
import com.autoremix.djslow.engine.pcm.AudioBufferPool
import com.autoremix.djslow.engine.pcm.AudioPcmData
import kotlin.math.abs
import kotlin.math.max

/**
 * State DSP Vokal yang persisten untuk pemrosesan streaming per blok.
 */
class VocalBlockProcessor(val sampleRate: Int = 44100, val channels: Int = 2) {
    private val hpf = BiquadFilter(
        type = BiquadFilter.FilterType.HIGH_PASS,
        frequencyHz = 85.0f,
        sampleRate = sampleRate,
        q = 0.7071f
    )
    private val mudFilter = BiquadFilter(
        type = BiquadFilter.FilterType.PEAKING_EQ,
        frequencyHz = 320.0f,
        sampleRate = sampleRate,
        q = 1.0f,
        gainDb = -2.0f
    )
    private val presenceFilter = BiquadFilter(
        type = BiquadFilter.FilterType.PEAKING_EQ,
        frequencyHz = 3400.0f,
        sampleRate = sampleRate,
        q = 1.2f,
        gainDb = 2.2f
    )
    private val airFilter = BiquadFilter(
        type = BiquadFilter.FilterType.HIGH_SHELF,
        frequencyHz = 10500.0f,
        sampleRate = sampleRate,
        gainDb = 1.8f
    )
    private val vocalComp = DynamicsCompressor(
        thresholdDb = -18.0f,
        ratio = 2.4f,
        attackMs = 15.0f,
        releaseMs = 100.0f,
        kneeWidthDb = 4.0f,
        makeupGainDb = 0.8f,
        sampleRate = sampleRate
    )
    private val sibilanceFilter = BiquadFilter(
        type = BiquadFilter.FilterType.PEAKING_EQ,
        frequencyHz = 7200.0f,
        sampleRate = sampleRate,
        q = 2.0f,
        gainDb = 6.0f
    )
    private var deEssGain = 1.0f

    // Reusable detection buffer for de-esser (16384 frames max per block)
    private val persistentTempDetection = FloatArray(16384 * channels)

    // Reusable continuous stereo ambience delay lines (Zero per-block allocation)
    private val ambienceDelaySamples = ((28.0f * 0.001f) * sampleRate).toInt().coerceAtLeast(1)
    private val ambienceDelayL = FloatArray(ambienceDelaySamples)
    private val ambienceDelayR = FloatArray(ambienceDelaySamples)
    private var ambienceWriteIdx = 0
    private val ambienceClampedWet = 0.12f
    private val ambienceClampedFb = 0.10f

    fun processBlock(blockSamples: FloatArray) {
        if (blockSamples.isEmpty()) return

        hpf.processInterleaved(blockSamples, channels)
        mudFilter.processInterleaved(blockSamples, channels)
        presenceFilter.processInterleaved(blockSamples, channels)
        airFilter.processInterleaved(blockSamples, channels)
        vocalComp.processInterleaved(blockSamples, channels)

        // De-esser menggunakan buffer deteksi persisten (zero transient allocation)
        val tempDetection = if (blockSamples.size <= persistentTempDetection.size) {
            persistentTempDetection
        } else {
            AudioBufferPool.acquire(blockSamples.size)
        }
        try {
            System.arraycopy(blockSamples, 0, tempDetection, 0, blockSamples.size)
            sibilanceFilter.processInterleaved(tempDetection, channels)

            val totalFrames = blockSamples.size / channels
            val attack = 0.20f
            val release = 0.05f

            for (f in 0 until totalFrames) {
                val idxL = f * channels
                val idxR = if (channels > 1) idxL + 1 else idxL

                val sibLevel = max(abs(tempDetection[idxL]), abs(tempDetection[idxR]))
                val targetGain = if (sibLevel > 0.40f) {
                    val over = (sibLevel - 0.40f) * 1.5f
                    (1.0f - over.coerceIn(0.0f, 0.32f))
                } else {
                    1.0f
                }

                deEssGain = if (targetGain < deEssGain) {
                    (1.0f - attack) * deEssGain + attack * targetGain
                } else {
                    (1.0f - release) * deEssGain + release * targetGain
                }

                blockSamples[idxL] *= deEssGain
                if (channels > 1) {
                    blockSamples[idxR] *= deEssGain
                }
            }
        } finally {
            if (tempDetection !== persistentTempDetection) {
                AudioBufferPool.release(tempDetection)
            }
        }

        if (channels == 2) {
            val totalFrames = blockSamples.size / 2
            val dSamples = ambienceDelaySamples
            for (f in 0 until totalFrames) {
                val idxL = f * 2
                val idxR = idxL + 1

                val inL = blockSamples[idxL]
                val inR = blockSamples[idxR]

                val delayedL = ambienceDelayL[ambienceWriteIdx]
                val delayedR = ambienceDelayR[(ambienceWriteIdx + dSamples / 2) % dSamples]

                ambienceDelayL[ambienceWriteIdx] = inL + delayedL * ambienceClampedFb
                ambienceDelayR[ambienceWriteIdx] = inR + delayedR * ambienceClampedFb

                ambienceWriteIdx = (ambienceWriteIdx + 1) % dSamples

                blockSamples[idxL] = inL * (1.0f - ambienceClampedWet * 0.5f) + delayedR * ambienceClampedWet
                blockSamples[idxR] = inR * (1.0f - ambienceClampedWet * 0.5f) + delayedL * ambienceClampedWet
            }
        }
    }
}

/**
 * Pemroses Vokal Profesional (Tahap 5).
 * Alur:
 * 1. High Pass Filter (85 Hz) untuk membuang low-rumble & DC offset
 * 2. EQ Vokal (Mud scoop di 320 Hz, presence di 3.4 kHz, air shelf di 10.5 kHz)
 * 3. Kompresi Vokal Ringan (ratio 2.5:1, dynamic leveling tanpa membuat vokal gepeng)
 * 4. De-esser Lembut (menjinakkan sibilansi frekuensi 6.5 - 9 kHz)
 * 5. Reverb / Ambience Halus (memberikan kedalaman ruang tanpa menenggelamkan vokal dari posisi tengah)
 */
object VocalProcessor {

    fun process(vocalPcm: AudioPcmData, inPlace: Boolean = true): AudioPcmData {
        val samples = if (inPlace) vocalPcm.samples else vocalPcm.samples.copyOf()
        val sampleRate = vocalPcm.sampleRate
        val channels = vocalPcm.channels

        if (samples.isEmpty() || vocalPcm.isSilent()) {
            return vocalPcm
        }

        val processor = VocalBlockProcessor(sampleRate, channels)
        val blockSize = 16384
        val totalFrames = samples.size / channels
        val blockBuffer = FloatArray(blockSize * channels)

        var f = 0
        while (f < totalFrames) {
            val framesThis = minOf(blockSize, totalFrames - f)
            val sampleCount = framesThis * channels
            val offset = f * channels
            if (framesThis == blockSize) {
                System.arraycopy(samples, offset, blockBuffer, 0, sampleCount)
                processor.processBlock(blockBuffer)
                System.arraycopy(blockBuffer, 0, samples, offset, sampleCount)
            } else {
                val partialBuffer = FloatArray(sampleCount)
                System.arraycopy(samples, offset, partialBuffer, 0, sampleCount)
                processor.processBlock(partialBuffer)
                System.arraycopy(partialBuffer, 0, samples, offset, sampleCount)
            }
            f += framesThis
        }

        return AudioPcmData(samples, sampleRate, channels)
    }
}
