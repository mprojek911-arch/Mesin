package com.autoremix.djslow.engine.analysis

import com.autoremix.djslow.engine.pcm.AudioPcmData
import kotlin.math.abs

/**
 * Level intensitas drum yang disintesis secara otomatis agar tidak tumpang tindih
 * dengan audio beat yang dimasukkan pengguna.
 */
enum class GeneratedDrumMode(val label: String, val volumeMultiplier: Float) {
    OFF("Drum Bawaan Sangat Padat (Drum Synth OFF)", 0.0f),
    LOW("Drum Bawaan Cukup Kuat (Drum Synth LOW)", 0.35f),
    MEDIUM("Beat Moderat (Drum Synth MEDIUM)", 0.70f),
    HIGH("Beat Ringan / Tanpa Drum (Drum Synth FULL HIGH)", 1.0f)
}

/**
 * Beat Content Analyzer:
 * Menganalisis keberadaan instrumen dalam file beat pengguna:
 * - Drum Presence (analisis transient attack tajam frekuensi tinggi & rendah)
 * - Bass Presence (kandungan energi sub 40-160 Hz)
 * - Spectral Density
 * - Otomatis menentukan intensitas Generated Drum (LOW / MEDIUM / HIGH / OFF)
 */
object BeatContentAnalyzer {

    data class BeatContentAnalysis(
        val drumPresence: Float,     // 0.0 .. 1.0
        val bassPresence: Float,     // 0.0 .. 1.0
        val spectralDensity: Float,  // 0.0 .. 1.0
        val isBeatEmptyOrSilent: Boolean,
        val recommendedDrumMode: GeneratedDrumMode,
        val explanation: String
    )

    /**
     * Menganalisis trek audio beat pengguna.
     */
    fun analyze(beatPcm: AudioPcmData?): BeatContentAnalysis {
        if (beatPcm == null || beatPcm.totalFrames < 44100 || beatPcm.isSilent(0.005f)) {
            return BeatContentAnalysis(
                drumPresence = 0.0f,
                bassPresence = 0.0f,
                spectralDensity = 0.0f,
                isBeatEmptyOrSilent = true,
                recommendedDrumMode = GeneratedDrumMode.HIGH,
                explanation = "Trek Beat tidak ada atau hening: Drum sintetis penuh diaktifkan."
            )
        }

        val samples = beatPcm.samples
        val channels = beatPcm.channels
        val sampleRate = beatPcm.sampleRate

        // Analisis potongan representative hingga 30 detik
        val framesToAnalyze = minOf(beatPcm.totalFrames, sampleRate * 30)
        val windowSize = 1024
        val blocks = framesToAnalyze / windowSize

        var transientPeaksCount = 0
        var totalEnergy = 0.0
        var subBassEnergy = 0.0
        var highFreqEnergy = 0.0

        var prevSample = 0.0f

        for (b in 0 until blocks) {
            val startIdx = b * windowSize * channels
            var blockMax = 0.0f
            var blockMin = 0.0f

            for (n in 0 until windowSize) {
                val idx = startIdx + n * channels
                if (idx >= samples.size) break
                val s = samples[idx]
                val absS = abs(s)

                totalEnergy += absS
                if (absS > blockMax) blockMax = absS

                // High-frequency energy via first difference
                val diff = abs(s - prevSample)
                highFreqEnergy += diff

                // Low-frequency energy via smoothing
                val smooth = abs(s + prevSample) * 0.5
                subBassEnergy += smooth

                prevSample = s
            }

            // Jika ada lonjakan transient tajam > 0.45
            if (blockMax > 0.45f) {
                transientPeaksCount++
            }
        }

        val totalAnalyzed = (blocks * windowSize).coerceAtLeast(1)
        val avgTotalEnergy = (totalEnergy / totalAnalyzed).toFloat()
        val transientRatio = (transientPeaksCount.toFloat() / blocks.toFloat()).coerceIn(0.0f, 1.0f)
        val highFreqRatio = (highFreqEnergy / (totalEnergy + 1e-6)).toFloat().coerceIn(0.0f, 1.0f)
        val bassRatio = (subBassEnergy / (totalEnergy + 1e-6)).toFloat().coerceIn(0.0f, 1.0f)

        // Drum presence didasarkan pada rasio transient & frekuensi tinggi tajam
        val drumPresence = (transientRatio * 0.6f + highFreqRatio * 0.4f).coerceIn(0.0f, 1.0f)
        val bassPresence = (bassRatio * 1.2f).coerceIn(0.0f, 1.0f)
        val spectralDensity = avgTotalEnergy.coerceIn(0.0f, 1.0f)

        val drumMode = when {
            drumPresence > 0.70f -> GeneratedDrumMode.LOW
            drumPresence > 0.40f -> GeneratedDrumMode.MEDIUM
            else -> GeneratedDrumMode.HIGH
        }

        val explanation = when (drumMode) {
            GeneratedDrumMode.OFF -> "Beat pengguna sudah memiliki drum padat. Drum sintetis dimatikan."
            GeneratedDrumMode.LOW -> "Beat pengguna memiliki drum jelas. Drum sintetis dikurangi agar tidak menumpuk."
            GeneratedDrumMode.MEDIUM -> "Beat pengguna moderat. Drum sintetis mengisi frekuensi yang seimbang."
            GeneratedDrumMode.HIGH -> "Beat pengguna ringan/ambient. Drum sintetis penuh diaktifkan."
        }

        return BeatContentAnalysis(
            drumPresence = drumPresence,
            bassPresence = bassPresence,
            spectralDensity = spectralDensity,
            isBeatEmptyOrSilent = false,
            recommendedDrumMode = drumMode,
            explanation = explanation
        )
    }
}
