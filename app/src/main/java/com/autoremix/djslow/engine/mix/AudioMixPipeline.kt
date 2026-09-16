package com.autoremix.djslow.engine.mix

import android.content.Context
import android.net.Uri
import com.autoremix.djslow.engine.analysis.BpmDetector
import com.autoremix.djslow.engine.analysis.KeyDetector
import com.autoremix.djslow.engine.arrangement.AutoArrangementPlan
import com.autoremix.djslow.engine.arrangement.AutoArranger
import com.autoremix.djslow.engine.arrangement.AutoDjPreset
import com.autoremix.djslow.engine.arrangement.SectionEngines
import com.autoremix.djslow.engine.dsp.LoudnessMeter
import com.autoremix.djslow.engine.drum.DrumEngine
import com.autoremix.djslow.engine.mastering.AutoMasteringEngine
import com.autoremix.djslow.engine.mastering.MasteringPreset
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
 * Pipeline Audio Studio Tahap 5 — KUALITAS AUDIO LENGKAP:
 * MIX BUS, AUTO GAIN STAGING, VOCAL PROCESSING & DUCKING, KICK/BASS SEPARATION,
 * DRUM & BASS PROCESSING, MUSIC BUS, STEREO ENGINE, AUTO MASTERING, LOUDNESS LUFS & ANTI CLIPPING.
 *
 * ALUR LENGKAP:
 * 1. Decode Vokal -> VocalProcessor (HPF 85Hz + EQ Presence + Kompresi + De-esser + Ambience)
 * 2. Decode Beat
 * 3. Timeline & Aransemen Struktur DJ
 * 4. Sintesis Akor, Bass, Drum, Melodi, Pad, FX
 * 5. DrumProcessor (EQ Kick punch/Snare crisp/Hi-hat smooth + Punch Comp + Limiter)
 * 6. KickBassEngine (Sidechain Ducking) + BassProcessor (HPF 30Hz + EQ Dip 75Hz + Kompresi + Saturasi + Mono Sub)
 * 7. VocalDucker (Smooth Ducking pada Music Bus: Akor, Melodi, Pad, FX)
 * 8. Mix Bus Summing & Auto Gain Staging (Headroom pre-master -1.4 dBFS)
 * 9. AutoMasteringEngine (Tonal EQ -> Glue Comp -> Saturasi -> Stereo Control -> Lookahead Limiter -> Peak Protection)
 * 10. Render RIFF WAV 44.1 kHz 16-bit
 * 11. Validasi Ketat (Peak, RMS, Silence, NaN, Infinity, Clipping, LUFS Integrated, True Peak)
 */
object AudioMixPipeline {

    enum class PipelineStep(val label: String) {
        PERSIAPAN("Mempersiapkan render aransemen & mastering"),
        DECODE_VOKAL("Mendekode Vokal ke PCM Float32"),
        PROSES_VOKAL("Memproses Vokal (HPF, EQ, Kompresi, De-esser, Ambience)"),
        DECODE_BEAT("Mendekode Trek Beat"),
        ANALISIS_DAN_STRUKTUR("Menyusun Struktur Lagu & Kurva Energi"),
        SINTESIS_INSTRUMEN("Mensintesis Akor, Melodi, Pad & FX"),
        PROSES_DRUM_BASS("Memproses Drum & Sub-Bass (EQ, Sidechain, Punch)"),
        DUCKING_VOKAL("Menerapkan Ducking Vokal Halus pada Bus Musik"),
        MIXING("Multi-Bus Mixing & Auto Gain Staging"),
        MASTERING("Auto Mastering Studio (Tonal EQ, Glue Comp, Saturation, Stereo, Limiter)"),
        RENDER_WAV("Merender Berkas WAV 44.1 kHz 16-bit"),
        VALIDASI("Memvalidasi Integritas WAV, Loudness LUFS & Anti-Clipping"),
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
        val arrangementPlan: AutoArrangementPlan? = null,
        val musicAnalysis: com.autoremix.djslow.engine.core.MusicUnderstandingEngine.MusicAnalysis? = null
    )

    data class PipelineResult(
        val wavFile: File,
        val validation: WavValidator.ValidationResult,
        val durationMs: Long,
        val timeline: MasterTimeline,
        val key: MusicKey,
        val targetBpm: Float,
        val arrangementPlan: AutoArrangementPlan?,
        val loudnessReport: LoudnessMeter.LoudnessReport? = null,
        val masteringPreset: MasteringPreset = MasteringPreset.DJ_SLOW,
        val unmasteredWavFile: File? = null,
        val unmasteredLufs: Float = -14.0f
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

            val downbeats = timeline.beatGrid.filter { it.beatInBar == 0 }.map { it.sampleOffset }
            val vocalPhrases = com.autoremix.djslow.engine.core.MusicUnderstandingEngine.detectVocalPhrases(vocalPcm)
            val energyAvg = arrangementPlan.energyAnalysis?.overallRms ?: 0.5f

            val musicAnalysis = com.autoremix.djslow.engine.core.MusicUnderstandingEngine.MusicAnalysis(
                bpm = effectiveBpm,
                bpmConfidence = if (manualBpm != null) 1.0f else bpmAnalysis.targetConfidence,
                isBpmEstimated = manualBpm == null && bpmAnalysis.isTargetEstimated,
                key = effectiveKey,
                keyConfidence = keyConfidence,
                isKeyEstimated = isKeyEstimated,
                chords = chordProgression.chordProgressionSummary,
                chordConfidence = chordProgression.confidence,
                sections = arrangementPlan.sections,
                energyAverage = energyAvg,
                energyAnalysis = arrangementPlan.energyAnalysis,
                beatAnalysis = arrangementPlan.beatAnalysis,
                downbeats = downbeats,
                vocalPhrases = vocalPhrases,
                durationMs = totalDurationMs,
                timeline = timeline,
                energyCurve = arrangementPlan.energyCurve
            )

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
                arrangementPlan = arrangementPlan,
                musicAnalysis = musicAnalysis
            )

            return@withContext Result.success(result)
        } catch (e: Exception) {
            return@withContext Result.failure(e)
        }
    }

    /**
     * Menjalankan rendering lengkap aransemen & mastering Tahap 5.
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
        masteringPreset: MasteringPreset = MasteringPreset.DJ_SLOW,
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

            onProgress(PipelineStep.PERSIAPAN, 0.03f, "Mempersiapkan pipeline aransemen & mastering audio...")

            // 1. Decode Vokal
            var vocalPcm: AudioPcmData? = null
            if (vocalUri != null) {
                onProgress(PipelineStep.DECODE_VOKAL, 0.06f, "Mendekode trek Vokal ke PCM Float32...")
                val vResult = AudioPcmDecoder.decodeToPcm(context, vocalUri) { subProg, msg ->
                    onProgress(PipelineStep.DECODE_VOKAL, 0.06f + 0.08f * subProg, "Vokal: $msg")
                }
                if (vResult.isFailure) {
                    return@withContext Result.failure(
                        IllegalStateException("Gagal memproses audio vokal: ${vResult.exceptionOrNull()?.message}")
                    )
                }
                vocalPcm = vResult.getOrNull()
            }

            // 2. Pemrosesan Vokal Studio (Tahap 5)
            if (vocalPcm != null && !vocalPcm.isSilent()) {
                onProgress(PipelineStep.PROSES_VOKAL, 0.16f, "Memproses Vokal (HPF 85Hz, EQ Presence, Kompresi, De-esser, Ambience)...")
                vocalPcm = VocalProcessor.process(vocalPcm)
            }

            // 3. Decode Beat
            var beatPcm: AudioPcmData? = null
            if (beatUri != null) {
                onProgress(PipelineStep.DECODE_BEAT, 0.22f, "Mendekode trek Beat ke PCM Float32...")
                val bResult = AudioPcmDecoder.decodeToPcm(context, beatUri) { subProg, msg ->
                    onProgress(PipelineStep.DECODE_BEAT, 0.22f + 0.08f * subProg, "Beat: $msg")
                }
                if (bResult.isFailure) {
                    return@withContext Result.failure(
                        IllegalStateException("Gagal memproses audio beat: ${bResult.exceptionOrNull()?.message}")
                    )
                }
                beatPcm = bResult.getOrNull()
            }

            // 4. Bangun Master Timeline & Aransemen Struktur
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

            // 5. Sintesis Instrumen Pengiring
            onProgress(PipelineStep.SINTESIS_INSTRUMEN, 0.40f, "Mensintesis Akor, Melodi, Pad, dan FX...")

            var chordPcm: AudioPcmData? = null
            if (params.chordSettings.volume > 0.0f && !params.chordSettings.isMuted) {
                chordPcm = ChordSynthEngine.renderProgressionPcm(
                    timeline = timeline,
                    preset = chordPreset,
                    volume = params.chordSettings.volume
                )
            }

            var melodyPcm: AudioPcmData? = null
            if (params.melodySettings.volume > 0.0f && !params.melodySettings.isMuted) {
                melodyPcm = MelodyEngine.renderMelody(
                    events = arrangementPlan.melodyEvents,
                    totalSamples = totalFrames,
                    sampleRate = sampleRate
                )
            }

            var padPcm: AudioPcmData? = null
            if (params.padSettings.volume > 0.0f && !params.padSettings.isMuted) {
                padPcm = PadEngine.renderPad(
                    events = arrangementPlan.padEvents,
                    totalSamples = totalFrames,
                    sampleRate = sampleRate
                )
            }

            val transitionPcm = SectionEngines.renderTransitions(
                events = arrangementPlan.transitionEvents,
                totalSamples = totalFrames,
                sampleRate = sampleRate
            )

            // 6. Sintesis & Pemrosesan Drum (DrumProcessor)
            var drumPcm: AudioPcmData? = null
            if (params.drumSettings.volume > 0.0f && !params.drumSettings.isMuted) {
                onProgress(PipelineStep.PROSES_DRUM_BASS, 0.52f, "Memproses Drum Bus (EQ Punch, Snare Snap, Limiter)...")
                val rawDrum = DrumEngine.renderDrums(
                    events = arrangementPlan.drumEvents,
                    totalSamples = totalFrames,
                    sampleRate = sampleRate
                )
                drumPcm = DrumProcessor.process(rawDrum)
            }

            // 7. Sintesis & Pemrosesan Bass (Sidechain Ducking + BassProcessor)
            var bassPcm: AudioPcmData? = null
            if (params.bassSettings.volume > 0.0f && !params.bassSettings.isMuted) {
                onProgress(PipelineStep.PROSES_DRUM_BASS, 0.58f, "Memproses Bass Bus (Sidechain Ducking, Sub EQ, Mono Bass)...")
                val rawBass = BassEngine.renderBassPcm(
                    timeline = timeline,
                    bassEvents = bassEvents,
                    volume = params.bassSettings.volume * preset.bassMultiplier
                )

                // a. Sidechain ducking dari ketukan kick drum
                val sidechainedBass = if (arrangementPlan.drumEvents.isNotEmpty()) {
                    KickBassEngine.applySidechainDucking(
                        bassPcm = rawBass,
                        drumEvents = arrangementPlan.drumEvents,
                        sampleRate = sampleRate
                    )
                } else {
                    rawBass
                }

                // b. BassProcessor: HPF 30Hz, Notch 75Hz (Kick separation), Kompresi, Saturasi hangat, Mono Sub
                bassPcm = BassProcessor.process(sidechainedBass)
            }

            // 8. Ducking Vokal Halus pada Bus Musik (Akor, Melodi, Pad, FX)
            if (vocalPcm != null && !vocalPcm.isSilent()) {
                onProgress(PipelineStep.DUCKING_VOKAL, 0.64f, "Menerapkan Ducking Vokal Halus pada Bus Musik...")
                val duckDepth = masteringPreset.vocalPocketDepthDb
                if (chordPcm != null) {
                    chordPcm = VocalDucker.applyVocalDucking(chordPcm, vocalPcm, duckingDepthDb = duckDepth)
                }
                if (melodyPcm != null) {
                    melodyPcm = VocalDucker.applyVocalDucking(melodyPcm, vocalPcm, duckingDepthDb = duckDepth)
                }
                if (padPcm != null) {
                    padPcm = VocalDucker.applyVocalDucking(padPcm, vocalPcm, duckingDepthDb = duckDepth)
                }
            }

            // 9. Multi-Bus Mixing & Auto Gain Staging (Headroom pre-master -1.4 dBFS)
            onProgress(PipelineStep.MIXING, 0.70f, "Mixing multi-bus & Auto Gain Staging...")
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
                onProgress(PipelineStep.MIXING, 0.70f + 0.08f * subProg, "Mix: $msg")
            }

            if (mixResult.isFailure) {
                return@withContext Result.failure(
                    IllegalStateException("Proses mixing gagal: ${mixResult.exceptionOrNull()?.message}")
                )
            }
            val preMasterPcm = mixResult.getOrThrow()

            // Analisis loudness pre-master untuk Loudness Matching pada A/B Preview
            val preMasterReport = LoudnessMeter.analyze(preMasterPcm)

            // Render WAV unmastered (pre-master mix) untuk perbandingan A/B instan
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val tempDir = com.autoremix.djslow.engine.temp.TempFileManager.getTempDir(context)
            val unmasteredFile = File(tempDir, "TEMP_UNMASTERED_$timestamp.wav")
            WavRenderer.render(preMasterPcm, unmasteredFile)

            // 10. Auto Mastering Studio (Tahap 5)
            onProgress(
                PipelineStep.MASTERING,
                0.80f,
                "Auto Mastering (${masteringPreset.label}): Tonal EQ, Glue Comp, Saturation, Stereo, Limiter..."
            )
            val masterResult = AutoMasteringEngine.master(preMasterPcm, masteringPreset)
            if (masterResult.isFailure) {
                com.autoremix.djslow.engine.temp.TempFileManager.deleteSafe(unmasteredFile)
                return@withContext Result.failure(
                    IllegalStateException("Auto Mastering gagal: ${masterResult.exceptionOrNull()?.message}")
                )
            }
            val masteredResult = masterResult.getOrThrow()
            val finalMasterPcm = masteredResult.masteredPcm
            val loudnessReport = masteredResult.report

            // 11. Render ke Berkas WAV 44.1 kHz 16-bit
            val outputDir = File(context.filesDir, "rendered_wav").apply { mkdirs() }
            val outputFile = File(outputDir, "DJ_SLOW_MIX_$timestamp.wav")

            onProgress(PipelineStep.RENDER_WAV, 0.88f, "Merender berkas audio WAV 44.1 kHz 16-bit PCM...")
            val renderResult = WavRenderer.render(finalMasterPcm, outputFile) { subProg, msg ->
                onProgress(PipelineStep.RENDER_WAV, 0.88f + 0.06f * subProg, "WAV: $msg")
            }

            if (renderResult.isFailure) {
                com.autoremix.djslow.engine.temp.TempFileManager.deleteSafe(unmasteredFile)
                return@withContext Result.failure(
                    IllegalStateException(renderResult.exceptionOrNull()?.message ?: "Rendering gagal. Silakan ulangi.")
                )
            }
            val wavFile = renderResult.getOrThrow()

            // 12. Validasi Integritas WAV, Loudness LUFS & Anti-Clipping
            onProgress(PipelineStep.VALIDASI, 0.95f, "Memvalidasi WAV RIFF, LUFS (${loudnessReport.formattedLufs}), True Peak & Anti-Clipping...")
            val validation = WavValidator.validate(wavFile)
            if (!validation.isValid) {
                if (wavFile.exists()) wavFile.delete()
                com.autoremix.djslow.engine.temp.TempFileManager.deleteSafe(unmasteredFile)
                val errMsg = validation.errorMessage ?: "Rendering gagal. Silakan ulangi."
                return@withContext Result.failure(IllegalStateException(errMsg))
            }

            onProgress(
                PipelineStep.SELESAI,
                1.0f,
                "Master WAV berhasil & tervalidasi (${validation.durationMs / 1000}s, ${validation.formattedLufs}, ${validation.formattedTruePeak})."
            )

            return@withContext Result.success(
                PipelineResult(
                    wavFile = wavFile,
                    validation = validation,
                    durationMs = validation.durationMs,
                    timeline = timeline,
                    key = musicKey,
                    targetBpm = targetBpm,
                    arrangementPlan = arrangementPlan,
                    loudnessReport = loudnessReport,
                    masteringPreset = masteringPreset,
                    unmasteredWavFile = unmasteredFile,
                    unmasteredLufs = preMasterReport.lufsIntegrated
                )
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            com.autoremix.djslow.engine.temp.TempFileManager.cleanAllTempFiles(context)
            throw e
        } catch (e: Exception) {
            com.autoremix.djslow.engine.temp.TempFileManager.cleanAllTempFiles(context)
            return@withContext Result.failure(
                IllegalStateException("Kesalahan eksekusi pipeline: ${e.localizedMessage ?: e.message}")
            )
        }
    }
}
