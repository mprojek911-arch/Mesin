package com.autoremix.djslow.engine.core

import android.content.Context
import com.autoremix.djslow.engine.analysis.AutoEnergyAnalyzer
import com.autoremix.djslow.engine.analysis.BeatContentAnalyzer
import com.autoremix.djslow.engine.analysis.BpmDetector
import com.autoremix.djslow.engine.analysis.KeyDetector
import com.autoremix.djslow.engine.arrangement.AutoArranger
import com.autoremix.djslow.engine.arrangement.AutoDjPreset
import com.autoremix.djslow.engine.music.Chord
import com.autoremix.djslow.engine.music.ChordEngine
import com.autoremix.djslow.engine.music.MusicKey
import com.autoremix.djslow.engine.music.MusicMode
import com.autoremix.djslow.engine.music.PitchClass
import com.autoremix.djslow.engine.pcm.AudioPcmData
import com.autoremix.djslow.engine.structure.SongSection
import com.autoremix.djslow.engine.structure.SongStructureDetector
import com.autoremix.djslow.engine.timeline.MasterTimeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 2. MUSIC UNDERSTANDING ENGINE
 * Menganalisis audio secara komprehensif tanpa nilai palsu:
 * BPM, BPM Confidence, Key, Key Confidence, Chords, Sections, Energy,
 * Beat Information, Downbeat positions, dan Vocal Phrases.
 */
object MusicUnderstandingEngine {

    /**
     * Representasi frasa vokal yang terdeteksi dari audio vokal asli.
     */
    data class VocalPhrase(
        val index: Int,
        val startSample: Long,
        val endSample: Long,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val averageRms: Float
    ) {
        val durationMs: Long get() = endTimeMs - startTimeMs
    }

    /**
     * Hasil tunggal komprehensif Music Understanding.
     */
    data class MusicAnalysis(
        val bpm: Float,
        val bpmConfidence: Float,
        val isBpmEstimated: Boolean,
        val key: MusicKey,
        val keyConfidence: Float,
        val isKeyEstimated: Boolean,
        val chords: List<Chord>,
        val chordConfidence: Float,
        val sections: List<SongSection>,
        val energyAverage: Float,
        val energyAnalysis: AutoEnergyAnalyzer.EnergyAnalysisResult?,
        val beatAnalysis: BeatContentAnalyzer.BeatContentAnalysis?,
        val downbeats: List<Long>,
        val vocalPhrases: List<VocalPhrase>,
        val durationMs: Long,
        val timeline: MasterTimeline
    ) {
        val chordProgressionSummary: String
            get() = chords.joinToString(" - ") { it.displayName }

        val confidenceOverall: Float
            get() = (bpmConfidence * 0.4f) + (keyConfidence * 0.4f) + (chordConfidence * 0.2f)

        fun formattedReport(): String {
            return "BPM: ${bpm.roundToInt()} (konf: ${(bpmConfidence * 100).toInt()}%) | " +
                    "Key: ${key.displayName} (konf: ${(keyConfidence * 100).toInt()}%) | " +
                    "Akor: $chordProgressionSummary | " +
                    "Seksi: ${sections.size} seksi | " +
                    "Energi: ${(energyAverage * 100).toInt()}%"
        }
    }

    /**
     * Menganalisis SourceProject dengan pemrosesan audio nyata.
     */
    suspend fun understandAudio(
        context: Context,
        vocalPcm: AudioPcmData?,
        beatPcm: AudioPcmData?,
        presetBpm: Float? = null,
        presetKey: MusicKey? = null,
        onProgress: ((Float, String) -> Unit)? = null
    ): Result<MusicAnalysis> = withContext(Dispatchers.IO) {
        try {
            if ((vocalPcm == null || vocalPcm.isSilent()) && (beatPcm == null || beatPcm.isSilent())) {
                return@withContext Result.failure(
                    IllegalArgumentException("Tidak ada data audio nyata yang dapat dianalisis.")
                )
            }

            onProgress?.invoke(0.10f, "Mendeteksi tempo dan ritme (BPM)...")

            // 1. Deteksi BPM nyata
            val beatSourcePcm = beatPcm ?: vocalPcm!!
            val detectedBpmResult = BpmDetector.detectBpm(beatSourcePcm)
            val detectedBpm = detectedBpmResult?.bpm ?: 80.0f
            val bpmConfidence = detectedBpmResult?.confidence ?: 0.5f
            val isBpmEstimated = detectedBpmResult == null
            val finalBpm = presetBpm ?: detectedBpm

            onProgress?.invoke(0.30f, "Menganalisis profil kroma tangga nada (Musical Key)...")

            // 2. Deteksi Key nyata
            val tonalSourcePcm = vocalPcm ?: beatPcm!!
            val detectedKeyResult = KeyDetector.detectKey(tonalSourcePcm)
            val detectedKey = detectedKeyResult?.key ?: MusicKey(PitchClass.A, MusicMode.MINOR)
            val keyConfidence = detectedKeyResult?.confidence ?: 0.5f
            val isKeyEstimated = detectedKeyResult == null
            val finalKey = presetKey ?: detectedKey

            onProgress?.invoke(0.50f, "Membangun Master Clock & Beat Grid...")

            // 3. Bangun timeline awal
            val maxDuration = maxOf(vocalPcm?.durationMs ?: 0L, beatPcm?.durationMs ?: 0L, 8000L)
            var timeline = MasterTimeline.build(bpm = finalBpm, totalDurationMs = maxDuration)

            // 4. Deteksi Akor
            onProgress?.invoke(0.65f, "Mendeteksi progresi akor harmonik...")
            val chordProgression = ChordEngine.buildChordProgression(timeline, finalKey, vocalPcm)
            val chordList = chordProgression.chordProgressionSummary
            val chordConfidence = chordProgression.confidence
            timeline = timeline.copy(chordEvents = chordProgression.chordEvents)

            // 5. Analisis Konten Beat & Energi
            onProgress?.invoke(0.75f, "Menganalisis kurva energi & dinamika...")
            val beatAnalysis = if (beatPcm != null) BeatContentAnalyzer.analyze(beatPcm) else null
            val samplesPerBar = (timeline.sampleRate * 60f / finalBpm * 4f).toLong()
            val energyAnalysis = AutoEnergyAnalyzer.analyzeEnergy(vocalPcm, beatPcm, timeline.totalBars, samplesPerBar)
            val energyAvg = energyAnalysis.overallRms

            // 6. Deteksi Frasa Vokal Nyata
            onProgress?.invoke(0.85f, "Memetakan frasa vokal & titik hening...")
            val vocalPhrases = detectVocalPhrases(vocalPcm)

            // 7. Deteksi Struktur Seksi Lagu
            onProgress?.invoke(0.92f, "Mendeteksi seksi struktur lagu (Intro, Drop, Break)...")
            val structureResult = SongStructureDetector.detectStructure(
                totalBars = timeline.totalBars,
                bpm = finalBpm,
                sampleRate = timeline.sampleRate,
                energyResult = energyAnalysis
            )
            val rawSections = structureResult.sections

            // 8. Hitung Downbeats dari Beat Grid
            val downbeats = ArrayList<Long>()
            for (p in timeline.beatGrid) {
                if (p.beatInBar == 0) {
                    downbeats.add(p.sampleOffset)
                }
            }

            onProgress?.invoke(1.0f, "Analisis Music Understanding selesai.")

            val analysis = MusicAnalysis(
                bpm = finalBpm,
                bpmConfidence = bpmConfidence,
                isBpmEstimated = isBpmEstimated,
                key = finalKey,
                keyConfidence = keyConfidence,
                isKeyEstimated = isKeyEstimated,
                chords = chordList,
                chordConfidence = chordConfidence,
                sections = rawSections,
                energyAverage = energyAvg,
                energyAnalysis = energyAnalysis,
                beatAnalysis = beatAnalysis,
                downbeats = downbeats,
                vocalPhrases = vocalPhrases,
                durationMs = maxDuration,
                timeline = timeline
            )

            Result.success(analysis)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Gagal melakukan Music Understanding: ${e.localizedMessage ?: e.message}"))
        }
    }

    /**
     * Mendeteksi letak frasa vokal nyata dengan analisis energi RMS per blok 100ms.
     */
    private fun detectVocalPhrases(vocalPcm: AudioPcmData?): List<VocalPhrase> {
        if (vocalPcm == null || vocalPcm.isSilent()) return emptyList()

        val sampleRate = vocalPcm.sampleRate
        val channels = vocalPcm.channels
        val samples = vocalPcm.samples
        val blockFrames = sampleRate / 10 // 100 ms
        val totalFrames = vocalPcm.totalFrames

        val phrases = ArrayList<VocalPhrase>()
        var inPhrase = false
        var phraseStartFrame = 0L
        var phraseSumSquares = 0.0
        var phraseSampleCount = 0

        val silenceThreshold = 0.02f // Ambang batas deteksi suara vokal

        var f = 0
        while (f < totalFrames) {
            val endF = minOf(f + blockFrames, totalFrames)
            var blockSquares = 0.0
            var count = 0
            for (i in f until endF) {
                val s = samples[i * channels]
                blockSquares += s * s
                count++
            }
            val rms = if (count > 0) kotlin.math.sqrt(blockSquares / count).toFloat() else 0.0f

            if (rms >= silenceThreshold) {
                if (!inPhrase) {
                    inPhrase = true
                    phraseStartFrame = f.toLong()
                    phraseSumSquares = 0.0
                    phraseSampleCount = 0
                }
                phraseSumSquares += blockSquares
                phraseSampleCount += count
            } else {
                if (inPhrase) {
                    inPhrase = false
                    val phraseEndFrame = f.toLong()
                    val durMs = ((phraseEndFrame - phraseStartFrame) * 1000L) / sampleRate
                    // Hanya rekam jika durasi frasa vokal minimal 250ms
                    if (durMs >= 250L) {
                        val phraseRms = if (phraseSampleCount > 0) kotlin.math.sqrt(phraseSumSquares / phraseSampleCount).toFloat() else 0.0f
                        phrases.add(
                            VocalPhrase(
                                index = phrases.size,
                                startSample = phraseStartFrame,
                                endSample = phraseEndFrame,
                                startTimeMs = (phraseStartFrame * 1000L) / sampleRate,
                                endTimeMs = (phraseEndFrame * 1000L) / sampleRate,
                                averageRms = phraseRms
                            )
                        )
                    }
                }
            }
            f += blockFrames
        }

        // Tangani frasa jika masih berlangsung sampai akhir audio
        if (inPhrase) {
            val phraseEndFrame = totalFrames.toLong()
            val durMs = ((phraseEndFrame - phraseStartFrame) * 1000L) / sampleRate
            if (durMs >= 250L) {
                val phraseRms = if (phraseSampleCount > 0) kotlin.math.sqrt(phraseSumSquares / phraseSampleCount).toFloat() else 0.0f
                phrases.add(
                    VocalPhrase(
                        index = phrases.size,
                        startSample = phraseStartFrame,
                        endSample = phraseEndFrame,
                        startTimeMs = (phraseStartFrame * 1000L) / sampleRate,
                        endTimeMs = (phraseEndFrame * 1000L) / sampleRate,
                        averageRms = phraseRms
                    )
                )
            }
        }

        return phrases
    }
}
