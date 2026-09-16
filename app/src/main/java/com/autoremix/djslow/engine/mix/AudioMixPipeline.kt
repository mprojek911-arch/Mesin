package com.autoremix.djslow.engine.mix

import android.content.Context
import android.net.Uri
import com.autoremix.djslow.engine.analysis.BpmDetector
import com.autoremix.djslow.engine.analysis.KeyDetector
import com.autoremix.djslow.engine.music.ChordEngine
import com.autoremix.djslow.engine.music.MusicKey
import com.autoremix.djslow.engine.music.MusicMode
import com.autoremix.djslow.engine.music.PitchClass
import com.autoremix.djslow.engine.pcm.AudioPcmData
import com.autoremix.djslow.engine.pcm.AudioPcmDecoder
import com.autoremix.djslow.engine.synth.BassEngine
import com.autoremix.djslow.engine.synth.BassPatternType
import com.autoremix.djslow.engine.synth.ChordSynthEngine
import com.autoremix.djslow.engine.synth.ChordSynthPreset
import com.autoremix.djslow.engine.timeline.MasterTimeline
import com.autoremix.djslow.engine.wav.WavRenderer
import com.autoremix.djslow.engine.wav.WavValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pipeline Audio Nyata Tahap 3 — MUSIK (BPM + KEY + CHORD + BASS):
 * ANALISIS -> TIMELINE -> SINTESIS CHORD PCM -> SINTESIS BASS PCM -> MIXING 4-TRACK -> RENDER WAV -> VALIDASI
 */
object AudioMixPipeline {

    enum class PipelineStep(val label: String) {
        PERSIAPAN("Mempersiapkan render"),
        DECODE_VOKAL("Mendekode Vokal ke PCM"),
        DECODE_BEAT("Mendekode Beat ke PCM"),
        ANALISIS_MUSIK("Menganalisis BPM & Tangga Nada"),
        SINTESIS_CHORD("Mensintesis Akor PCM"),
        SINTESIS_BASS("Mensintesis Bass PCM"),
        MIXING("Proses Mixing 4-Trek Audio"),
        RENDER_WAV("Merender berkas WAV 44.1 kHz"),
        VALIDASI("Memvalidasi integritas berkas WAV"),
        SELESAI("Selesai")
    }

    data class AnalysisResult(
        val vocalBpm: BpmDetector.BpmResult?,
        val beatBpm: BpmDetector.BpmResult?,
        val targetBpm: Float,
        val isBpmEstimated: Boolean,
        val bpmConfidence: Float,
        val keyResult: KeyDetector.KeyDetectionResult?,
        val key: MusicKey,
        val isKeyEstimated: Boolean,
        val keyConfidence: Float,
        val chordProgression: ChordEngine.ProgressionResult,
        val timeline: MasterTimeline,
        val durationMs: Long
    )

    data class PipelineResult(
        val wavFile: File,
        val validation: WavValidator.ValidationResult,
        val durationMs: Long,
        val timeline: MasterTimeline,
        val key: MusicKey,
        val targetBpm: Float
    )

    /**
     * Menganalisis track vokal dan/atau beat untuk mendeteksi BPM, Tangga Nada (Key),
     * Progresi Akor, serta menyusun Master Timeline.
     */
    suspend fun analyzeAudio(
        context: Context,
        vocalUri: Uri?,
        beatUri: Uri?,
        manualBpm: Float? = null,
        manualKey: MusicKey? = null,
        onProgress: ((Float, String) -> Unit)? = null
    ): Result<AnalysisResult> = withContext(Dispatchers.IO) {
        try {
            if (vocalUri == null && beatUri == null) {
                return@withContext Result.failure(
                    IllegalArgumentException("Pilih setidaknya file vokal atau beat sebelum melakukan analisis.")
                )
            }

            onProgress?.invoke(0.10f, "Membaca berkas audio...")

            var vocalPcm: AudioPcmData? = null
            if (vocalUri != null) {
                onProgress?.invoke(0.20f, "Mendekode vokal untuk analisis DSP...")
                val vResult = AudioPcmDecoder.decodeToPcm(context, vocalUri)
                if (vResult.isSuccess) vocalPcm = vResult.getOrNull()
            }

            var beatPcm: AudioPcmData? = null
            if (beatUri != null) {
                onProgress?.invoke(0.40f, "Mendekode beat untuk analisis DSP...")
                val bResult = AudioPcmDecoder.decodeToPcm(context, beatUri)
                if (bResult.isSuccess) beatPcm = bResult.getOrNull()
            }

            if (vocalPcm == null && beatPcm == null) {
                return@withContext Result.failure(
                    IllegalStateException("Tidak ada audio yang dapat didekode untuk dianalisis.")
                )
            }

            onProgress?.invoke(0.60f, "Menganalisis BPM Vokal dan Beat...")
            val bpmAnalysis = BpmDetector.analyzeBoth(vocalPcm, beatPcm)
            val effectiveBpm = manualBpm ?: bpmAnalysis.targetBpm

            onProgress?.invoke(0.75f, "Mendeteksi Tangga Nada (Chroma & Key Profile)...")
            // Deteksi tangga nada diutamakan dari vokal, jika tidak ada vokal gunakan beat
            val keyAnalysis = KeyDetector.detectKey(vocalPcm ?: beatPcm)
            val effectiveKey = manualKey ?: keyAnalysis?.key ?: MusicKey(PitchClass.A, MusicMode.MINOR, 0.35f)
            val isKeyEstimated = manualKey == null && (keyAnalysis == null || keyAnalysis.isEstimated)
            val keyConfidence = manualKey?.confidence ?: keyAnalysis?.confidence ?: 0.35f

            onProgress?.invoke(0.85f, "Menyusun Master Timeline & Beat Grid...")
            val vocalDuration = vocalPcm?.durationMs ?: 0L
            val beatDuration = beatPcm?.durationMs ?: 0L
            val totalDurationMs = maxOf(vocalDuration, beatDuration, 12000L) // minimal 12 detik

            var timeline = MasterTimeline.build(
                bpm = effectiveBpm,
                totalDurationMs = totalDurationMs
            )

            onProgress?.invoke(0.95f, "Menyelaraskan Progresi Akor & Pola Bass...")
            val chordProgression = ChordEngine.buildChordProgression(timeline, effectiveKey, vocalPcm)
            timeline = timeline.copy(chordEvents = chordProgression.chordEvents)

            val bassEvents = BassEngine.generateBassEvents(timeline, effectiveKey, BassPatternType.DJ_SLOW_BASS)
            timeline = timeline.copy(bassEvents = bassEvents)

            onProgress?.invoke(1.0f, "Analisis musik selesai.")

            val result = AnalysisResult(
                vocalBpm = bpmAnalysis.vocalBpm,
                beatBpm = bpmAnalysis.beatBpm,
                targetBpm = effectiveBpm,
                isBpmEstimated = manualBpm == null && bpmAnalysis.isTargetEstimated,
                bpmConfidence = if (manualBpm != null) 1.0f else bpmAnalysis.targetConfidence,
                keyResult = keyAnalysis,
                key = effectiveKey,
                isKeyEstimated = isKeyEstimated,
                keyConfidence = keyConfidence,
                chordProgression = chordProgression,
                timeline = timeline,
                durationMs = totalDurationMs
            )

            return@withContext Result.success(result)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    /**
     * Menjalankan pipeline lengkap:
     * DECODE -> MUSICAL SYNTHESIS (Chord + Bass) -> 4-TRACK MIXING -> WAV RENDER -> VALIDATION
     */
    suspend fun run(
        context: Context,
        vocalUri: Uri?,
        beatUri: Uri?,
        targetBpm: Float = 80.0f,
        musicKey: MusicKey = MusicKey(PitchClass.A, MusicMode.MINOR),
        chordPreset: ChordSynthPreset = ChordSynthPreset.SOFT_PIANO,
        bassPattern: BassPatternType = BassPatternType.DJ_SLOW_BASS,
        params: MixEngine.MixParams = MixEngine.MixParams(),
        onProgress: (step: PipelineStep, progressFraction: Float, message: String) -> Unit
    ): Result<PipelineResult> = withContext(Dispatchers.IO) {
        try {
            if (vocalUri == null && beatUri == null) {
                return@withContext Result.failure(
                    IllegalArgumentException("Pilih setidaknya file vokal atau beat sebelum melakukan mixing.")
                )
            }

            onProgress(PipelineStep.PERSIAPAN, 0.05f, "Mempersiapkan pipeline render audio...")

            // 1. Decode Vokal
            var vocalPcm: AudioPcmData? = null
            if (vocalUri != null) {
                onProgress(PipelineStep.DECODE_VOKAL, 0.10f, "Mendekode trek Vokal ke PCM Float32...")
                val vResult = AudioPcmDecoder.decodeToPcm(context, vocalUri) { subProg, msg ->
                    onProgress(PipelineStep.DECODE_VOKAL, 0.10f + 0.15f * subProg, "Vokal: $msg")
                }
                if (vResult.isFailure) {
                    return@withContext Result.failure(
                        IllegalStateException("Gagal memproses audio vokal: ${vResult.exceptionOrNull()?.message}")
                    )
                }
                vocalPcm = vResult.getOrNull()
            }

            // 2. Decode Beat
            var beatPcm: AudioPcmData? = null
            if (beatUri != null) {
                onProgress(PipelineStep.DECODE_BEAT, 0.25f, "Mendekode trek Beat ke PCM Float32...")
                val bResult = AudioPcmDecoder.decodeToPcm(context, beatUri) { subProg, msg ->
                    onProgress(PipelineStep.DECODE_BEAT, 0.25f + 0.15f * subProg, "Beat: $msg")
                }
                if (bResult.isFailure) {
                    return@withContext Result.failure(
                        IllegalStateException("Gagal memproses audio beat: ${bResult.exceptionOrNull()?.message}")
                    )
                }
                beatPcm = bResult.getOrNull()
            }

            // 3. Bangun Master Timeline & Analisis Akor
            onProgress(PipelineStep.ANALISIS_MUSIK, 0.42f, "Menyusun Master Timeline (${targetBpm.toInt()} BPM, ${musicKey.displayName})...")
            val maxAudioDurationMs = maxOf(vocalPcm?.durationMs ?: 0L, beatPcm?.durationMs ?: 0L, 8000L)

            var timeline = MasterTimeline.build(
                bpm = targetBpm,
                totalDurationMs = maxAudioDurationMs
            )

            val chordProgression = ChordEngine.buildChordProgression(timeline, musicKey, vocalPcm)
            timeline = timeline.copy(chordEvents = chordProgression.chordEvents)

            val bassEvents = BassEngine.generateBassEvents(timeline, musicKey, bassPattern)
            timeline = timeline.copy(bassEvents = bassEvents)

            // 4. Sintesis Akor PCM Nyata
            var chordPcm: AudioPcmData? = null
            if (params.chordSettings.volume > 0.0f && !params.chordSettings.isMuted) {
                onProgress(PipelineStep.SINTESIS_CHORD, 0.50f, "Mensintesis audio Akor PCM (${chordPreset.label})...")
                chordPcm = ChordSynthEngine.renderProgressionPcm(
                    timeline = timeline,
                    preset = chordPreset,
                    volume = params.chordSettings.volume
                ) { subProg, msg ->
                    onProgress(PipelineStep.SINTESIS_CHORD, 0.50f + 0.10f * subProg, msg)
                }
            }

            // 5. Sintesis Bass PCM Nyata
            var bassPcm: AudioPcmData? = null
            if (params.bassSettings.volume > 0.0f && !params.bassSettings.isMuted) {
                onProgress(PipelineStep.SINTESIS_BASS, 0.60f, "Mensintesis audio Bass PCM (${bassPattern.label})...")
                bassPcm = BassEngine.renderBassPcm(
                    timeline = timeline,
                    bassEvents = bassEvents,
                    volume = params.bassSettings.volume
                ) { subProg, msg ->
                    onProgress(PipelineStep.SINTESIS_BASS, 0.60f + 0.10f * subProg, msg)
                }
            }

            // 6. 4-Track Mixing (Vokal + Beat + Chord + Bass)
            onProgress(PipelineStep.MIXING, 0.72f, "Mixing 4-Trek (Vokal, Beat, Chord, Bass) dengan Headroom...")
            val mixResult = MixEngine.mix(
                vocalPcm = vocalPcm,
                beatPcm = beatPcm,
                chordPcm = chordPcm,
                bassPcm = bassPcm,
                params = params
            ) { subProg, msg ->
                onProgress(PipelineStep.MIXING, 0.72f + 0.10f * subProg, "Mix: $msg")
            }

            if (mixResult.isFailure) {
                return@withContext Result.failure(
                    IllegalStateException("Proses mixing gagal: ${mixResult.exceptionOrNull()?.message}")
                )
            }
            val mixedPcm = mixResult.getOrThrow()

            // 7. Render ke Berkas WAV 44.1 kHz
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val outputDir = File(context.filesDir, "rendered_wav").apply { mkdirs() }
            val outputFile = File(outputDir, "DJ_SLOW_MIX_$timestamp.wav")

            onProgress(PipelineStep.RENDER_WAV, 0.83f, "Merender berkas audio WAV 44.1 kHz 16-bit...")
            val renderResult = WavRenderer.render(mixedPcm, outputFile) { subProg, msg ->
                onProgress(PipelineStep.RENDER_WAV, 0.83f + 0.10f * subProg, "WAV: $msg")
            }

            if (renderResult.isFailure) {
                return@withContext Result.failure(
                    IllegalStateException(renderResult.exceptionOrNull()?.message ?: "Rendering gagal. Silakan ulangi.")
                )
            }
            val wavFile = renderResult.getOrThrow()

            // 8. Validasi Integritas WAV Menyeluruh
            onProgress(PipelineStep.VALIDASI, 0.95f, "Memvalidasi struktur header RIFF & audio non-silent...")
            val validation = WavValidator.validate(wavFile)
            if (!validation.isValid) {
                if (wavFile.exists()) wavFile.delete()
                val errMsg = validation.errorMessage ?: "Rendering gagal. Silakan ulangi."
                return@withContext Result.failure(IllegalStateException(errMsg))
            }

            onProgress(
                PipelineStep.SELESAI,
                1.0f,
                "Render WAV berhasil & tervalidasi (${validation.durationMs / 1000} detik)."
            )

            return@withContext Result.success(
                PipelineResult(
                    wavFile = wavFile,
                    validation = validation,
                    durationMs = validation.durationMs,
                    timeline = timeline,
                    key = musicKey,
                    targetBpm = targetBpm
                )
            )
        } catch (e: Exception) {
            return@withContext Result.failure(
                IllegalStateException("Kesalahan eksekusi pipeline: ${e.localizedMessage ?: e.message}")
            )
        }
    }
}
