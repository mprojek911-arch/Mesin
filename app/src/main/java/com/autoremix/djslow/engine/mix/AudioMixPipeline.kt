package com.autoremix.djslow.engine.mix

import android.content.Context
import android.net.Uri
import com.autoremix.djslow.engine.analysis.BpmDetector
import com.autoremix.djslow.engine.analysis.KeyDetector
import com.autoremix.djslow.engine.arrangement.AutoArrangementPlan
import com.autoremix.djslow.engine.arrangement.AutoArranger
import com.autoremix.djslow.engine.arrangement.AutoDjPreset
import com.autoremix.djslow.engine.arrangement.SectionEngines
import com.autoremix.djslow.engine.drum.DrumEngine
import com.autoremix.djslow.engine.melody.MelodyEngine
import com.autoremix.djslow.engine.music.ChordEngine
import com.autoremix.djslow.engine.music.MusicKey
import com.autoremix.djslow.engine.music.MusicMode
import com.autoremix.djslow.engine.music.PitchClass
import com.autoremix.djslow.engine.pad.PadEngine
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
 * Pipeline Audio Nyata Tahap 4 — ARRANGEMENT:
 * DRUM + MELODY + PAD + AUTO DJ ENERGY STRUCTURE ENGINE
 *
 * ALUR PIPELINE:
 * DECODE -> ANALISIS (BPM + Key + Energy + Beat Content) -> AUTO ARRANGER (Struktur, Kurva Energi, Pola)
 * -> SINTESIS CHORD PCM -> SINTESIS BASS PCM -> SINTESIS DRUM PCM -> SINTESIS MELODI PCM
 * -> SINTESIS PAD PCM -> FX TRANSISI -> KICK-BASS SIDECHAIN DUCKING -> MULTI-TRACK MIXING -> WAV RENDER -> VALIDASI
 */
object AudioMixPipeline {

    enum class PipelineStep(val label: String) {
        PERSIAPAN("Mempersiapkan render"),
        DECODE_VOKAL("Mendekode Vokal ke PCM"),
        DECODE_BEAT("Mendekode Beat ke PCM"),
        ANALISIS_DAN_STRUKTUR("Menganalisis Musik & Menyusun Struktur Lagu"),
        SINTESIS_CHORD("Mensintesis Akor PCM"),
        SINTESIS_BASS("Mensintesis Bass PCM"),
        SINTESIS_DRUM("Mensintesis Drum Pola DJ Slow"),
        SINTESIS_MELODI("Mensintesis Melodi Hook PCM"),
        SINTESIS_PAD("Mensintesis Pad Atmosfir"),
        PROSES_SIDECHAIN("Sinkronisasi Kick & Bass (Sidechain Ducking)"),
        MIXING("Proses Multi-Track Mixing"),
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
        val durationMs: Long,
        val arrangementPlan: AutoArrangementPlan? = null
    )

    data class PipelineResult(
        val wavFile: File,
        val validation: WavValidator.ValidationResult,
        val durationMs: Long,
        val timeline: MasterTimeline,
        val key: MusicKey,
        val targetBpm: Float,
        val arrangementPlan: AutoArrangementPlan?
    )

    /**
     * Menganalisis track vokal dan beat, mendeteksi struktur, energi, progresi akor, dan beat content.
     */
    suspend fun analyzeAudio(
        context: Context,
        vocalUri: Uri?,
        beatUri: Uri?,
        manualBpm: Float? = null,
        manualKey: MusicKey? = null,
        preset: AutoDjPreset = AutoDjPreset.DJ_SLOW,
        melodySeed: Long = 42L,
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

            onProgress?.invoke(0.55f, "Menganalisis BPM Vokal dan Beat...")
            val bpmAnalysis = BpmDetector.analyzeBoth(vocalPcm, beatPcm)
            val effectiveBpm = manualBpm ?: bpmAnalysis.targetBpm

            onProgress?.invoke(0.70f, "Mendeteksi Tangga Nada (Chroma & Key Profile)...")
            val keyAnalysis = KeyDetector.detectKey(vocalPcm ?: beatPcm)
            val effectiveKey = manualKey ?: keyAnalysis?.key ?: MusicKey(PitchClass.A, MusicMode.MINOR, 0.35f)
            val isKeyEstimated = manualKey == null && (keyAnalysis == null || keyAnalysis.isEstimated)
            val keyConfidence = manualKey?.confidence ?: keyAnalysis?.confidence ?: 0.35f

            onProgress?.invoke(0.80f, "Menyusun Master Timeline & Grid Ketukan...")
            val vocalDuration = vocalPcm?.durationMs ?: 0L
            val beatDuration = beatPcm?.durationMs ?: 0L
            val totalDurationMs = maxOf(vocalDuration, beatDuration, 12000L)

            var timeline = MasterTimeline.build(
                bpm = effectiveBpm,
                totalDurationMs = totalDurationMs
            )

            val chordProgression = ChordEngine.buildChordProgression(timeline, effectiveKey, vocalPcm)
            timeline = timeline.copy(chordEvents = chordProgression.chordEvents)

            val bassEvents = BassEngine.generateBassEvents(timeline, effectiveKey, BassPatternType.DJ_SLOW_BASS)
            timeline = timeline.copy(bassEvents = bassEvents)

            onProgress?.invoke(0.90f, "Menyusun Aransemen Lagu & Deteksi Seksi Otomatis...")
            val arrangementPlan = AutoArranger.arrange(
                vocalPcm = vocalPcm,
                beatPcm = beatPcm,
                timeline = timeline,
                key = effectiveKey,
                chords = chordProgression.chordProgressionSummary,
                preset = preset,
                melodySeed = melodySeed
            )
            timeline = arrangementPlan.updatedTimeline

            onProgress?.invoke(1.0f, "Analisis dan aransemen struktur selesai.")

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
                durationMs = totalDurationMs,
                arrangementPlan = arrangementPlan
            )

            return@withContext Result.success(result)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    /**
     * Menjalankan rendering lengkap aransemen Tahap 4:
     * DECODE -> ARRANGE -> SYNTHESIZE ALL INSTRUMENTS -> MIX -> RENDER WAV -> VALIDATE
     */
    suspend fun run(
        context: Context,
        vocalUri: Uri?,
        beatUri: Uri?,
        targetBpm: Float = 80.0f,
        musicKey: MusicKey = MusicKey(PitchClass.A, MusicMode.MINOR),
        chordPreset: ChordSynthPreset = ChordSynthPreset.SOFT_PIANO,
        bassPattern: BassPatternType = BassPatternType.DJ_SLOW_BASS,
        preset: AutoDjPreset = AutoDjPreset.DJ_SLOW,
        melodySeed: Long = 42L,
        params: MixEngine.MixParams = MixEngine.MixParams(),
        onProgress: (step: PipelineStep, progressFraction: Float, message: String) -> Unit
    ): Result<PipelineResult> = withContext(Dispatchers.IO) {
        try {
            if (vocalUri == null && beatUri == null) {
                return@withContext Result.failure(
                    IllegalArgumentException("Pilih setidaknya file vokal atau beat sebelum melakukan mixing.")
                )
            }

            onProgress(PipelineStep.PERSIAPAN, 0.04f, "Mempersiapkan pipeline aransemen audio...")

            // 1. Decode Vokal
            var vocalPcm: AudioPcmData? = null
            if (vocalUri != null) {
                onProgress(PipelineStep.DECODE_VOKAL, 0.08f, "Mendekode trek Vokal ke PCM Float32...")
                val vResult = AudioPcmDecoder.decodeToPcm(context, vocalUri) { subProg, msg ->
                    onProgress(PipelineStep.DECODE_VOKAL, 0.08f + 0.10f * subProg, "Vokal: $msg")
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
                onProgress(PipelineStep.DECODE_BEAT, 0.20f, "Mendekode trek Beat ke PCM Float32...")
                val bResult = AudioPcmDecoder.decodeToPcm(context, beatUri) { subProg, msg ->
                    onProgress(PipelineStep.DECODE_BEAT, 0.20f + 0.10f * subProg, "Beat: $msg")
                }
                if (bResult.isFailure) {
                    return@withContext Result.failure(
                        IllegalStateException("Gagal memproses audio beat: ${bResult.exceptionOrNull()?.message}")
                    )
                }
                beatPcm = bResult.getOrNull()
            }

            // 3. Bangun Master Timeline, Progresi Akor & Aransemen Struktur Lagu
            onProgress(PipelineStep.ANALISIS_DAN_STRUKTUR, 0.32f, "Menyusun struktur seksi lagu & kurva energi...")
            val maxAudioDurationMs = maxOf(vocalPcm?.durationMs ?: 0L, beatPcm?.durationMs ?: 0L, 8000L)

            var timeline = MasterTimeline.build(
                bpm = targetBpm,
                totalDurationMs = maxAudioDurationMs
            )

            val chordProgression = ChordEngine.buildChordProgression(timeline, musicKey, vocalPcm)
            timeline = timeline.copy(chordEvents = chordProgression.chordEvents)

            val bassEvents = BassEngine.generateBassEvents(timeline, musicKey, bassPattern)
            timeline = timeline.copy(bassEvents = bassEvents)

            val arrangementPlan = AutoArranger.arrange(
                vocalPcm = vocalPcm,
                beatPcm = beatPcm,
                timeline = timeline,
                key = musicKey,
                chords = chordProgression.chordProgressionSummary,
                preset = preset,
                melodySeed = melodySeed
            )
            timeline = arrangementPlan.updatedTimeline
            val totalFrames = timeline.totalFrames
            val sampleRate = timeline.sampleRate

            // 4. Sintesis Akor PCM
            var chordPcm: AudioPcmData? = null
            if (params.chordSettings.volume > 0.0f && !params.chordSettings.isMuted) {
                onProgress(PipelineStep.SINTESIS_CHORD, 0.40f, "Mensintesis Akor PCM (${chordPreset.label})...")
                chordPcm = ChordSynthEngine.renderProgressionPcm(
                    timeline = timeline,
                    preset = chordPreset,
                    volume = params.chordSettings.volume
                ) { subProg, msg ->
                    onProgress(PipelineStep.SINTESIS_CHORD, 0.40f + 0.06f * subProg, msg)
                }
            }

            // 5. Sintesis Bass PCM
            var bassPcm: AudioPcmData? = null
            if (params.bassSettings.volume > 0.0f && !params.bassSettings.isMuted) {
                onProgress(PipelineStep.SINTESIS_BASS, 0.48f, "Mensintesis Bass PCM (${bassPattern.label})...")
                bassPcm = BassEngine.renderBassPcm(
                    timeline = timeline,
                    bassEvents = bassEvents,
                    volume = params.bassSettings.volume * preset.bassMultiplier
                ) { subProg, msg ->
                    onProgress(PipelineStep.SINTESIS_BASS, 0.48f + 0.06f * subProg, msg)
                }
            }

            // 6. Sintesis Drum PCM
            var drumPcm: AudioPcmData? = null
            if (params.drumSettings.volume > 0.0f && !params.drumSettings.isMuted) {
                onProgress(PipelineStep.SINTESIS_DRUM, 0.55f, "Mensintesis Drum Pola DJ Slow (Kick, Snare, Claps, Fills)...")
                drumPcm = DrumEngine.renderDrums(
                    events = arrangementPlan.drumEvents,
                    totalSamples = totalFrames,
                    sampleRate = sampleRate
                )
            }

            // 7. Sintesis Melodi Hook PCM
            var melodyPcm: AudioPcmData? = null
            if (params.melodySettings.volume > 0.0f && !params.melodySettings.isMuted) {
                onProgress(PipelineStep.SINTESIS_MELODI, 0.62f, "Mensintesis Melodi Hook (Algorithmic Lead & Counter)...")
                melodyPcm = MelodyEngine.renderMelody(
                    events = arrangementPlan.melodyEvents,
                    totalSamples = totalFrames,
                    sampleRate = sampleRate
                )
            }

            // 8. Sintesis Pad Atmosfir PCM
            var padPcm: AudioPcmData? = null
            if (params.padSettings.volume > 0.0f && !params.padSettings.isMuted) {
                onProgress(PipelineStep.SINTESIS_PAD, 0.68f, "Mensintesis Pad Atmosfir Harmonis...")
                padPcm = PadEngine.renderPad(
                    events = arrangementPlan.padEvents,
                    totalSamples = totalFrames,
                    sampleRate = sampleRate
                )
            }

            // 9. FX Transisi & Risers
            onProgress(PipelineStep.PROSES_SIDECHAIN, 0.72f, "Menghasilkan FX Transisi & Risers...")
            val transitionPcm = SectionEngines.renderTransitions(
                events = arrangementPlan.transitionEvents,
                totalSamples = totalFrames,
                sampleRate = sampleRate
            )

            // 10. Terapkan Kick-Bass Sidechain Ducking
            if (bassPcm != null && arrangementPlan.drumEvents.isNotEmpty()) {
                onProgress(PipelineStep.PROSES_SIDECHAIN, 0.75f, "Menerapkan Sidechain Ducking (Kick & Sub-Bass)...")
                bassPcm = com.autoremix.djslow.engine.mix.KickBassEngine.applySidechainDucking(
                    bassPcm = bassPcm,
                    drumEvents = arrangementPlan.drumEvents,
                    sampleRate = sampleRate
                )
            }

            // 11. Multi-Track Mixing
            onProgress(PipelineStep.MIXING, 0.78f, "Mixing multi-track dengan proteksi Headroom (-0.5 dB)...")
            val mixResult = MixEngine.mix(
                vocalPcm = vocalPcm,
                beatPcm = beatPcm,
                chordPcm = chordPcm,
                bassPcm = bassPcm,
                drumPcm = drumPcm,
                melodyPcm = melodyPcm,
                padPcm = padPcm,
                transitionPcm = transitionPcm,
                params = params
            ) { subProg, msg ->
                onProgress(PipelineStep.MIXING, 0.78f + 0.08f * subProg, "Mix: $msg")
            }

            if (mixResult.isFailure) {
                return@withContext Result.failure(
                    IllegalStateException("Proses mixing gagal: ${mixResult.exceptionOrNull()?.message}")
                )
            }
            val mixedPcm = mixResult.getOrThrow()

            // 12. Render ke Berkas WAV 44.1 kHz
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val outputDir = File(context.filesDir, "rendered_wav").apply { mkdirs() }
            val outputFile = File(outputDir, "DJ_SLOW_MIX_$timestamp.wav")

            onProgress(PipelineStep.RENDER_WAV, 0.88f, "Merender berkas audio WAV 44.1 kHz 16-bit...")
            val renderResult = WavRenderer.render(mixedPcm, outputFile) { subProg, msg ->
                onProgress(PipelineStep.RENDER_WAV, 0.88f + 0.07f * subProg, "WAV: $msg")
            }

            if (renderResult.isFailure) {
                return@withContext Result.failure(
                    IllegalStateException(renderResult.exceptionOrNull()?.message ?: "Rendering gagal. Silakan ulangi.")
                )
            }
            val wavFile = renderResult.getOrThrow()

            // 13. Validasi Integritas WAV
            onProgress(PipelineStep.VALIDASI, 0.96f, "Memvalidasi struktur RIFF WAV & audio non-silent...")
            val validation = WavValidator.validate(wavFile)
            if (!validation.isValid) {
                if (wavFile.exists()) wavFile.delete()
                val errMsg = validation.errorMessage ?: "Rendering gagal. Silakan ulangi."
                return@withContext Result.failure(IllegalStateException(errMsg))
            }

            onProgress(
                PipelineStep.SELESAI,
                1.0f,
                "Aransemen WAV berhasil & tervalidasi (${validation.durationMs / 1000} detik)."
            )

            return@withContext Result.success(
                PipelineResult(
                    wavFile = wavFile,
                    validation = validation,
                    durationMs = validation.durationMs,
                    timeline = timeline,
                    key = musicKey,
                    targetBpm = targetBpm,
                    arrangementPlan = arrangementPlan
                )
            )
        } catch (e: Exception) {
            return@withContext Result.failure(
                IllegalStateException("Kesalahan eksekusi pipeline: ${e.localizedMessage ?: e.message}")
            )
        }
    }
}
