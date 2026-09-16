package com.autoremix.djslow.engine.analysis

import com.autoremix.djslow.engine.pcm.AudioPcmData
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Detektor BPM lokal berbasis pemrosesan sinyal digital (DSP):
 * - Novelty curve (energi & onset transient)
 * - Autokorelasi interval ketukan
 * - Estimasi tempo 60–140 BPM
 * - Perhitungan confidence tanpa angka palsu
 */
object BpmDetector {

    const val MIN_BPM = 60.0f
    const val MAX_BPM = 140.0f
    const val DEFAULT_BPM = 80.0f

    val PRESET_BPMS = listOf(70, 75, 80, 85, 90, 95)

    data class BpmResult(
        val bpm: Float,
        val confidence: Float, // 0.0f .. 1.0f
        val isEstimated: Boolean, // true jika confidence < 0.40f
        val details: String = ""
    ) {
        val displayLabel: String
            get() = if (isEstimated) {
                "Perkiraan: ${bpm.roundToInt()} BPM"
            } else {
                "${bpm.roundToInt()} BPM (${(confidence * 100).toInt()}%)"
            }
    }

    data class TrackBpmAnalysis(
        val vocalBpm: BpmResult?,
        val beatBpm: BpmResult?,
        val targetBpm: Float,
        val targetConfidence: Float,
        val isTargetEstimated: Boolean
    ) {
        val targetDisplay: String
            get() = if (isTargetEstimated) {
                "Perkiraan: ${targetBpm.roundToInt()} BPM"
            } else {
                "${targetBpm.roundToInt()} BPM"
            }
    }

    /**
     * Menganalisis BPM dari trek audio PCM.
     */
    fun detectBpm(pcm: AudioPcmData?): BpmResult? {
        if (pcm == null || pcm.totalFrames < 44100 || pcm.isSilent()) {
            return null
        }

        val samples = pcm.samples
        val sampleRate = pcm.sampleRate
        val channels = pcm.channels

        // Ambil maksimal hingga 60 detik untuk analisis agar cepat dan akurat
        val maxFramesToAnalyze = minOf(pcm.totalFrames, sampleRate * 60)
        val hopSize = 512 // ~11.6 ms per hop pada 44.1 kHz
        val numFrames = maxFramesToAnalyze / hopSize

        if (numFrames < 100) {
            return BpmResult(DEFAULT_BPM, 0.2f, true, "Sampel terlalu pendek")
        }

        // 1. Hitung kurva energi RMS per blok frame
        val energy = FloatArray(numFrames)
        for (i in 0 until numFrames) {
            var sumSquare = 0.0
            val startSample = i * hopSize * channels
            val endSample = minOf(startSample + hopSize * channels, samples.size)
            val count = endSample - startSample
            if (count > 0) {
                for (s in startSample until endSample) {
                    val v = samples[s]
                    sumSquare += (v * v)
                }
                energy[i] = sqrt((sumSquare / count).toFloat())
            }
        }

        // 2. Hitung Onset Novelty Curve (Half-wave rectified first difference)
        val novelty = FloatArray(numFrames)
        for (i in 1 until numFrames) {
            val diff = energy[i] - energy[i - 1]
            novelty[i] = if (diff > 0.0f) diff else 0.0f
        }

        // 3. Autokorelasi pada rentang lag 60 - 140 BPM
        val envelopeRate = sampleRate.toFloat() / hopSize.toFloat() // ~86.13 Hz
        val minLag = (envelopeRate * 60.0f / MAX_BPM).toInt() // lag untuk 140 BPM
        val maxLag = (envelopeRate * 60.0f / MIN_BPM).toInt() // lag untuk 60 BPM

        if (minLag >= maxLag || maxLag >= numFrames / 2) {
            return BpmResult(DEFAULT_BPM, 0.25f, true, "Frame rate autokorelasi di luar batas")
        }

        var bestLag = minLag
        var maxCorrelation = 0.0f
        var totalCorrelation = 0.0f
        var lagCount = 0

        val correlations = FloatArray(maxLag - minLag + 1)

        for (lag in minLag..maxLag) {
            var sum = 0.0f
            val end = numFrames - lag
            for (n in 0 until end) {
                sum += novelty[n] * novelty[n + lag]
            }

            // Normalisasi jarak lag
            val corr = sum / end
            correlations[lag - minLag] = corr
            totalCorrelation += corr
            lagCount++

            if (corr > maxCorrelation) {
                maxCorrelation = corr
                bestLag = lag
            }
        }

        val avgCorrelation = if (lagCount > 0) totalCorrelation / lagCount else 0.0001f

        // Sub-sample interpolation untuk lag puncak
        val peakIndex = bestLag - minLag
        var refinedLag = bestLag.toFloat()
        if (peakIndex > 0 && peakIndex < correlations.size - 1) {
            val alpha = correlations[peakIndex - 1]
            val beta = correlations[peakIndex]
            val gamma = correlations[peakIndex + 1]
            val denom = (alpha - 2.0f * beta + gamma)
            if (abs(denom) > 1e-6f) {
                val delta = 0.5f * (alpha - gamma) / denom
                refinedLag = bestLag + delta.coerceIn(-0.5f, 0.5f)
            }
        }

        var rawBpm = (envelopeRate * 60.0f) / refinedLag

        // Harmonisasi: jika terdeteksi dua kali lipat (>140), bagi 2
        while (rawBpm > MAX_BPM) {
            rawBpm /= 2.0f
        }
        // jika kurang dari 60, kali 2
        while (rawBpm < MIN_BPM) {
            rawBpm *= 2.0f
        }

        val clampedBpm = rawBpm.coerceIn(MIN_BPM, MAX_BPM)

        // Hitung confidence berdasarkan rasio puncak terhadap rata-rata
        val peakToAvgRatio = if (avgCorrelation > 1e-7f) maxCorrelation / avgCorrelation else 1.0f
        val confidence = ((peakToAvgRatio - 1.0f) / 3.0f).coerceIn(0.15f, 0.95f)
        val isEstimated = confidence < 0.40f

        return BpmResult(
            bpm = clampedBpm,
            confidence = confidence,
            isEstimated = isEstimated,
            details = "Peak-to-average ratio: %.2f".format(peakToAvgRatio)
        )
    }

    /**
     * Menganalisis vokal dan beat secara terpisah, lalu menentukan Target BPM.
     */
    fun analyzeBoth(vocalPcm: AudioPcmData?, beatPcm: AudioPcmData?): TrackBpmAnalysis {
        val vocalResult = detectBpm(vocalPcm)
        val beatResult = detectBpm(beatPcm)

        val targetBpm: Float
        val targetConfidence: Float
        val isTargetEstimated: Boolean

        when {
            // Jika ada Beat dengan confidence tinggi, Beat adalah acuan ritmik utama
            beatResult != null && beatResult.confidence >= 0.45f -> {
                targetBpm = beatResult.bpm
                targetConfidence = beatResult.confidence
                isTargetEstimated = beatResult.isEstimated
            }
            // Jika hanya ada vokal atau beat kurang jelas tapi vokal jelas
            vocalResult != null && vocalResult.confidence >= 0.40f -> {
                targetBpm = vocalResult.bpm
                targetConfidence = vocalResult.confidence
                isTargetEstimated = vocalResult.isEstimated
            }
            // Keduanya ada dengan confidence sedang
            beatResult != null && vocalResult != null -> {
                // Bobot 70% beat + 30% vokal
                targetBpm = (beatResult.bpm * 0.7f + vocalResult.bpm * 0.3f).coerceIn(MIN_BPM, MAX_BPM)
                targetConfidence = max(beatResult.confidence, vocalResult.confidence)
                isTargetEstimated = targetConfidence < 0.40f
            }
            // Salah satu ada
            beatResult != null -> {
                targetBpm = beatResult.bpm
                targetConfidence = beatResult.confidence
                isTargetEstimated = beatResult.isEstimated
            }
            vocalResult != null -> {
                targetBpm = vocalResult.bpm
                targetConfidence = vocalResult.confidence
                isTargetEstimated = vocalResult.isEstimated
            }
            // Tidak ada audio yang bisa dianalisis
            else -> {
                targetBpm = DEFAULT_BPM
                targetConfidence = 0.20f
                isTargetEstimated = true
            }
        }

        return TrackBpmAnalysis(
            vocalBpm = vocalResult,
            beatBpm = beatResult,
            targetBpm = targetBpm,
            targetConfidence = targetConfidence,
            isTargetEstimated = isTargetEstimated
        )
    }
}
