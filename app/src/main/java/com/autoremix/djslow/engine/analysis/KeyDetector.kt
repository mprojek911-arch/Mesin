package com.autoremix.djslow.engine.analysis

import com.autoremix.djslow.engine.music.Chord
import com.autoremix.djslow.engine.music.MusicKey
import com.autoremix.djslow.engine.music.MusicMode
import com.autoremix.djslow.engine.music.PitchClass
import com.autoremix.djslow.engine.pcm.AudioPcmData
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Detektor Tangga Nada (Musical Key) berbasis Chroma dan Profil Nada Krumhansl-Schmuckler.
 * Mendukung 12 Pitch Class (C .. B) x 2 Mode (Mayor & Minor).
 * Menghitung tingkat keyakinan (Confidence) nyata dari korelasi Pearson.
 */
object KeyDetector {

    // Profil Nada Krumhansl-Schmuckler standar
    private val MAJOR_PROFILE = doubleArrayOf(
        6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88
    )
    private val MINOR_PROFILE = doubleArrayOf(
        6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17
    )

    data class KeyDetectionResult(
        val key: MusicKey,
        val confidence: Float,
        val isEstimated: Boolean,
        val chromagram: FloatArray,
        val secondaryCandidate: MusicKey? = null
    ) {
        val displayLabel: String
            get() = if (isEstimated) {
                "Perkiraan: ${key.displayName}"
            } else {
                "${key.displayName} (${(confidence * 100).toInt()}%)"
            }
    }

    /**
     * Menganalisis Tangga Nada dari trek PCM audio.
     */
    fun detectKey(pcm: AudioPcmData?): KeyDetectionResult? {
        if (pcm == null || pcm.totalFrames < 44100 || pcm.isSilent()) {
            return null
        }

        val chromagram = computeChromagram(pcm)
        if (chromagram.all { it <= 1e-6f }) {
            val fallbackKey = MusicKey(PitchClass.A, MusicMode.MINOR, 0.25f)
            return KeyDetectionResult(fallbackKey, 0.25f, true, chromagram)
        }

        var bestKey = MusicKey(PitchClass.C, MusicMode.MAJOR)
        var maxCorr = -1.0
        var secondCorr = -1.0
        var secondKey: MusicKey? = null

        // Uji 12 Mayor dan 12 Minor
        for (mode in MusicMode.entries) {
            val profile = if (mode == MusicMode.MAJOR) MAJOR_PROFILE else MINOR_PROFILE
            for (tonicIndex in 0 until 12) {
                // Rotasi profil ke tonic tertentu
                val rotatedProfile = DoubleArray(12) { i ->
                    profile[(i - tonicIndex + 12) % 12]
                }
                val corr = pearsonCorrelation(chromagram, rotatedProfile)
                val candidateKey = MusicKey(PitchClass.fromSemitone(tonicIndex), mode)

                if (corr > maxCorr) {
                    secondCorr = maxCorr
                    secondKey = bestKey
                    maxCorr = corr
                    bestKey = candidateKey
                } else if (corr > secondCorr) {
                    secondCorr = corr
                    secondKey = candidateKey
                }
            }
        }

        // Hitung confidence berdasarkan korelasi absolut dan selisih terhadap runner-up
        val margin = (maxCorr - secondCorr).coerceIn(0.0, 1.0)
        val normalizedCorr = ((maxCorr + 1.0) / 2.0).coerceIn(0.0, 1.0) // petakan [-1, 1] ke [0, 1]
        val rawConfidence = (normalizedCorr * 0.6 + margin * 0.4).toFloat().coerceIn(0.15f, 0.95f)

        val isEstimated = rawConfidence < 0.45f

        val detectedKey = bestKey.copy(confidence = rawConfidence)

        return KeyDetectionResult(
            key = detectedKey,
            confidence = rawConfidence,
            isEstimated = isEstimated,
            chromagram = chromagram,
            secondaryCandidate = secondKey
        )
    }

    /**
     * Menghitung vektor Chroma 12 dimensi [C, C#, D, ..., B] dari audio PCM.
     * Menggunakan Goertzel filter / bank bandpass pada rentang oktaf C2 hingga C6.
     */
    fun computeChromagram(pcm: AudioPcmData): FloatArray {
        return computeChromagram(
            samples = pcm.samples,
            channels = pcm.channels,
            sampleRate = pcm.sampleRate,
            startFrameOffset = 0,
            frameCount = pcm.totalFrames
        )
    }

    /**
     * Menghitung vektor Chroma langsung dari rentang sampel tanpa alokasi array slice.
     */
    fun computeChromagram(
        samples: FloatArray,
        channels: Int,
        sampleRate: Int,
        startFrameOffset: Int,
        frameCount: Int
    ): FloatArray {
        val chroma = DoubleArray(12)

        // Analisis potongan hingga 45 detik agar optimal dan cepat
        val maxFrames = minOf(frameCount, sampleRate * 45)
        val step = max(1, frameCount / maxFrames)

        // Rentang MIDI 36 (C2 ~ 65.4 Hz) hingga MIDI 84 (C6 ~ 1046.5 Hz)
        val octaves = 4 // oktaf 2, 3, 4, 5
        val windowSize = 4096

        val blockCount = maxOf(1, maxFrames / windowSize)

        for (b in 0 until blockCount) {
            val startFrame = startFrameOffset + (b * windowSize * step)
            if (startFrame + windowSize > startFrameOffset + frameCount) break

            for (pitchClass in 0 until 12) {
                var pitchEnergy = 0.0
                for (oct in 2..(2 + octaves)) {
                    val midiNote = 12 * (oct + 1) + pitchClass
                    val freq = Chord.midiToFrequency(midiNote)
                    val k = (freq * windowSize / sampleRate).toInt()
                    if (k in 1 until windowSize / 2) {
                        // Hitung magnitudo Goertzel pada frekuensi target
                        val mag = goertzelMagnitude(samples, channels, startFrame, windowSize, freq, sampleRate)
                        pitchEnergy += mag
                    }
                }
                chroma[pitchClass] += pitchEnergy
            }
        }

        // Normalisasi vektor chroma ke rentang [0, 1]
        var maxVal = 0.0
        for (v in chroma) if (v > maxVal) maxVal = v

        val result = FloatArray(12)
        if (maxVal > 1e-7) {
            for (i in 0 until 12) {
                result[i] = (chroma[i] / maxVal).toFloat()
            }
        }
        return result
    }

    /**
     * Algoritma Goertzel untuk mendeteksi energi spektral frekuensi tunggal secara efisien.
     */
    private fun goertzelMagnitude(
        samples: FloatArray,
        channels: Int,
        startFrame: Int,
        numSamples: Int,
        targetFreq: Float,
        sampleRate: Int
    ): Double {
        val omega = (2.0 * Math.PI * targetFreq) / sampleRate
        val coeff = 2.0 * cos(omega)

        var q0: Double
        var q1 = 0.0
        var q2 = 0.0

        for (n in 0 until numSamples) {
            val idx = (startFrame + n) * channels
            if (idx >= samples.size) break
            // Gunakan channel mono rata-rata jika stereo
            val sampleVal = if (channels == 2 && idx + 1 < samples.size) {
                (samples[idx] + samples[idx + 1]) * 0.5
            } else {
                samples[idx].toDouble()
            }

            q0 = coeff * q1 - q2 + sampleVal
            q2 = q1
            q1 = q0
        }

        return sqrt(q1 * q1 + q2 * q2 - q1 * q2 * coeff)
    }

    /**
     * Menghitung koefisien korelasi Pearson antara vektor chroma dan profil tangga nada.
     */
    private fun pearsonCorrelation(chroma: FloatArray, profile: DoubleArray): Double {
        val n = 12
        var sumX = 0.0
        var sumY = 0.0
        var sumXY = 0.0
        var sumX2 = 0.0
        var sumY2 = 0.0

        for (i in 0 until n) {
            val x = chroma[i].toDouble()
            val y = profile[i]
            sumX += x
            sumY += y
            sumXY += x * y
            sumX2 += x * x
            sumY2 += y * y
        }

        val numerator = n * sumXY - sumX * sumY
        val denominator = sqrt((n * sumX2 - sumX * sumX) * (n * sumY2 - sumY * sumY))

        return if (denominator > 1e-9) numerator / denominator else 0.0
    }
}
