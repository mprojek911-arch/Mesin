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

    fun processBlock(blockSamples: FloatArray) {
        if (blockSamples.isEmpty()) return

        hpf.processInterleaved(blockSamples, channels)
        mudFilter.processInterleaved(blockSamples, channels)
        presenceFilter.processInterleaved(blockSamples, channels)
        airFilter.processInterleaved(blockSamples, channels)
        vocalComp.processInterleaved(blockSamples, channels)

        // De-esser menggunakan buffer deteksi sementara dari pool (hanya seukuran blok)
        val tempDetection = AudioBufferPool.acquire(blockSamples.size)
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
            AudioBufferPool.release(tempDetection)
        }

        if (channels == 2) {
            StereoEngine.applyStereoAmbience(
                samples = blockSamples,
                sampleRate = sampleRate,
                delayMs = 28.0f,
                feedback = 0.10f,
                wetLevel = 0.12f
            )
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
        processor.processBlock(samples)

        return AudioPcmData(samples, sampleRate, channels)
    }
}
