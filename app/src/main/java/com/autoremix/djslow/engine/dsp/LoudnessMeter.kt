package com.autoremix.djslow.engine.dsp

import com.autoremix.djslow.engine.pcm.AudioPcmData
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Pengukur Keras Suara Nyata (True DSP Loudness & Peak Meter).
 * Mengimplementasikan standar internasional ITU-R BS.1770-4 K-weighting filter & Gated Integrated LUFS.
 *
 * Mengukur secara presisi:
 * - LUFS Integrated (Target: -14 s/d -10 LUFS)
 * - True Peak (dBTP) melalui estimasi inter-sample 4x oversampling
 * - Peak Amplitude linear & Peak dBFS
 * - RMS linear & RMS dBFS
 * - Dynamic Range (Loudness Range / LU)
 *
 * TIDAK MENGGUNAKAN ANGKA PALSU.
 */
object LoudnessMeter {

    data class LoudnessReport(
        val lufsIntegrated: Float,
        val truePeakDbtp: Float,
        val peakLinear: Float,
        val peakDbfs: Float,
        val rmsLinear: Float,
        val rmsDbfs: Float,
        val dynamicRangeLu: Float,
        val isClipping: Boolean
    ) {
        val formattedLufs: String
            get() = String.format("%.1f LUFS", lufsIntegrated)

        val formattedTruePeak: String
            get() = String.format("%.2f dBTP", truePeakDbtp)

        val formattedPeak: String
            get() = String.format("%.2f dBFS", peakDbfs)

        val formattedRms: String
            get() = String.format("%.1f dBFS", rmsDbfs)
    }

    /**
     * Menganalisis buffer audio PCM Float32 untuk mengukur tingkat loudness, peak, dan RMS.
     */
    fun analyze(pcm: AudioPcmData): LoudnessReport {
        val samples = pcm.samples
        val channels = pcm.channels
        val sampleRate = pcm.sampleRate
        val totalFrames = pcm.totalFrames

        if (samples.isEmpty() || totalFrames <= 0) {
            return LoudnessReport(
                lufsIntegrated = -70.0f,
                truePeakDbtp = -96.0f,
                peakLinear = 0.0f,
                peakDbfs = -96.0f,
                rmsLinear = 0.0f,
                rmsDbfs = -96.0f,
                dynamicRangeLu = 0.0f,
                isClipping = false
            )
        }

        val meter = StreamingLoudnessMeter(sampleRate, channels)
        meter.processChunk(samples, 0, samples.size)
        return meter.finish()
    }

    /**
     * Pengukur Loudness Streaming BS.1770-4 berbasis blok kecil (Zero Full-Track Allocation).
     */
    class StreamingLoudnessMeter(val sampleRate: Int = 44100, val channels: Int = 2) {
        private var maxPeak = 0.0f
        private var sumSquares = 0.0
        private var totalSamplesCount = 0L
        private var highestTruePeak = 0.0f
        private var foundNanOrInf = false

        private val lastSamples = FloatArray(3 * channels)
        private var hasLastSamples = false

        private val stage1Filter = BiquadFilter(
            type = BiquadFilter.FilterType.HIGH_SHELF,
            frequencyHz = 1681.0f,
            sampleRate = sampleRate,
            q = 0.7071f,
            gainDb = 4.0f
        )
        private val stage2Filter = BiquadFilter(
            type = BiquadFilter.FilterType.HIGH_PASS,
            frequencyHz = 38.0f,
            sampleRate = sampleRate,
            q = 0.50f
        )

        private val hopSize = (sampleRate * 0.100f).toInt().coerceAtLeast(1)
        private val subBlockSamples = hopSize * channels
        private val subBlockBuffer = FloatArray(subBlockSamples)
        private var subBlockFilled = 0
        private val subBlockPowers = ArrayList<Double>(2048)

        val hasInvalidSample: Boolean get() = foundNanOrInf

        fun processChunk(chunk: FloatArray, offset: Int = 0, count: Int = chunk.size) {
            if (count <= 0) return
            totalSamplesCount += count
            val step = channels

            // 1. Peak Linear, RMS, and NaN/Inf check
            val endIdx = offset + count
            for (i in offset until endIdx) {
                val s = chunk[i]
                if (s.isNaN() || s.isInfinite()) {
                    foundNanOrInf = true
                    continue
                }
                val a = abs(s)
                if (a > maxPeak) maxPeak = a
                sumSquares += (s.toDouble() * s.toDouble())
            }

            // 2. True Peak estimation via Cubic Hermite
            for (i in offset until endIdx step step) {
                val s1 = abs(chunk[i])
                val s2 = if (i + step < endIdx) abs(chunk[i + step]) else s1
                if (s1 > 0.70f || s2 > 0.70f) {
                    val s0 = if (i - step >= offset) abs(chunk[i - step])
                    else if (hasLastSamples) abs(lastSamples[0]) else s1
                    val s3 = if (i + 2 * step < endIdx) abs(chunk[i + 2 * step]) else s2

                    for (sub in 1..3) {
                        val t = sub / 4.0f
                        val c0 = -0.5f * s0 + 1.5f * s1 - 1.5f * s2 + 0.5f * s3
                        val c1 = s0 - 2.5f * s1 + 2.0f * s2 - 0.5f * s3
                        val c2 = -0.5f * s0 + 0.5f * s2
                        val c3 = s1
                        val interp = c0 * t * t * t + c1 * t * t + c2 * t + c3
                        if (interp > highestTruePeak) highestTruePeak = interp
                    }
                } else {
                    if (s1 > highestTruePeak) highestTruePeak = s1
                }
            }
            if (count >= 3 * step) {
                System.arraycopy(chunk, endIdx - 3 * step, lastSamples, 0, 3 * step)
                hasLastSamples = true
            }

            // 3. Sub-block 100ms K-weighting accumulation
            var srcPos = offset
            var remaining = count
            while (remaining > 0) {
                val needed = subBlockSamples - subBlockFilled
                val toCopy = minOf(remaining, needed)
                System.arraycopy(chunk, srcPos, subBlockBuffer, subBlockFilled, toCopy)
                subBlockFilled += toCopy
                srcPos += toCopy
                remaining -= toCopy

                if (subBlockFilled == subBlockSamples) {
                    stage1Filter.processInterleaved(subBlockBuffer, channels)
                    stage2Filter.processInterleaved(subBlockBuffer, channels)
                    var subSum = 0.0
                    for (k in 0 until subBlockSamples) {
                        val v = subBlockBuffer[k].toDouble()
                        subSum += v * v
                    }
                    subBlockPowers.add(subSum / subBlockSamples)
                    subBlockFilled = 0
                }
            }
        }

        fun finish(): LoudnessReport {
            if (subBlockFilled > 0) {
                val active = subBlockBuffer.copyOfRange(0, subBlockFilled)
                stage1Filter.processInterleaved(active, channels)
                stage2Filter.processInterleaved(active, channels)
                var subSum = 0.0
                for (k in 0 until subBlockFilled) {
                    val v = active[k].toDouble()
                    subSum += v * v
                }
                subBlockPowers.add(subSum / subBlockFilled)
            }

            val rmsLinear = if (totalSamplesCount > 0) sqrt(sumSquares / totalSamplesCount).toFloat() else 0.0f
            val peakDbfs = if (maxPeak > 1e-6f) (20.0 * log10(maxPeak.toDouble())).toFloat() else -96.0f
            val rmsDbfs = if (rmsLinear > 1e-6f) (20.0 * log10(rmsLinear.toDouble())).toFloat() else -96.0f
            val truePeakDbtp = if (highestTruePeak > 1e-6f) (20.0 * log10(highestTruePeak.toDouble())).toFloat() else -96.0f

            val blockPowers = ArrayList<Double>(subBlockPowers.size)
            if (subBlockPowers.size >= 4) {
                for (i in 0..subBlockPowers.size - 4) {
                    val mean400ms = (subBlockPowers[i] + subBlockPowers[i + 1] + subBlockPowers[i + 2] + subBlockPowers[i + 3]) / 4.0
                    blockPowers.add(mean400ms)
                }
            } else if (subBlockPowers.isNotEmpty()) {
                blockPowers.add(subBlockPowers.average())
            }

            val absoluteThresholdPower = 10.0.pow((-70.0 + 0.691) / 10.0)
            val aboveAbsolute = blockPowers.filter { it > absoluteThresholdPower }

            var lufsIntegrated = -70.0f
            var dynamicRange = 0.0f

            if (aboveAbsolute.isNotEmpty()) {
                val meanAbsolute = aboveAbsolute.average()
                val lkfsAbsolute = -0.691 + 10.0 * log10(meanAbsolute)
                val relativeThresholdPower = 10.0.pow((lkfsAbsolute - 10.0 + 0.691) / 10.0)
                val aboveRelative = aboveAbsolute.filter { it > relativeThresholdPower }

                if (aboveRelative.isNotEmpty()) {
                    val meanRelative = aboveRelative.average()
                    lufsIntegrated = (-0.691 + 10.0 * log10(meanRelative)).toFloat()
                    val sorted = aboveRelative.map { (-0.691 + 10.0 * log10(it)).toFloat() }.sorted()
                    val p10 = sorted[(sorted.size * 0.10).toInt()]
                    val p95 = sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)]
                    dynamicRange = max(0.0f, p95 - p10)
                }
            }

            val isClipping = maxPeak >= 1.0f || highestTruePeak > 1.02f

            return LoudnessReport(
                lufsIntegrated = lufsIntegrated.coerceIn(-70.0f, 0.0f),
                truePeakDbtp = truePeakDbtp,
                peakLinear = maxPeak,
                peakDbfs = peakDbfs,
                rmsLinear = rmsLinear,
                rmsDbfs = rmsDbfs,
                dynamicRangeLu = dynamicRange,
                isClipping = isClipping
            )
        }
    }

    /**
     * Estimasi True Peak dengan 4x inter-sample interpolation di sekitar titik puncak tertinggi.
     */
    private fun estimateTruePeak(samples: FloatArray, channels: Int): Float {
        var highestTruePeak = 0.0f
        val step = channels

        for (i in 0 until samples.size - 3 * step step step) {
            val s0 = abs(samples[i])
            val s1 = abs(samples[i + step])
            val s2 = abs(samples[i + 2 * step])
            val s3 = abs(samples[i + 3 * step])

            // Hanya periksa jika ada kandidat puncak lokal yang tinggi
            if (s1 > 0.70f || s2 > 0.70f) {
                for (sub in 1..3) {
                    val t = sub / 4.0f
                    // Cubic Hermite spline interpolation
                    val c0 = -0.5f * s0 + 1.5f * s1 - 1.5f * s2 + 0.5f * s3
                    val c1 = s0 - 2.5f * s1 + 2.0f * s2 - 0.5f * s3
                    val c2 = -0.5f * s0 + 0.5f * s2
                    val c3 = s1
                    val interp = c0 * t * t * t + c1 * t * t + c2 * t + c3
                    if (interp > highestTruePeak) highestTruePeak = interp
                }
            } else {
                if (s1 > highestTruePeak) highestTruePeak = s1
            }
        }

        return highestTruePeak
    }
}
