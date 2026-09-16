package com.autoremix.djslow.engine.core

import android.content.Context
import com.autoremix.djslow.engine.dsp.LoudnessMeter
import com.autoremix.djslow.engine.pcm.AudioPcmData
import com.autoremix.djslow.engine.timeline.MasterTimeline
import com.autoremix.djslow.logchat.LogChatManager
import com.autoremix.djslow.logchat.LogModule
import com.autoremix.djslow.logchat.PipelineStage
import com.autoremix.djslow.logchat.StepStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * REMIX WORKFLOW ENGINE
 * Koordinator tunggal seluruh alur Intelligent Remix Engine:
 * SOURCE
 * -> MUSIC UNDERSTANDING
 * -> MUSICAL MAP
 * -> REMIX BRAIN
 * -> ARRANGEMENT
 * -> MUSIC GENERATION
 * -> VOCAL & FX
 * -> INTELLIGENT MIX
 * -> MASTER
 * -> OUTPUT (VALIDATE & PREVIEW/RENDER)
 */
object RemixWorkflowEngine {

    data class WorkflowResult(
        val analysis: MusicUnderstandingEngine.MusicAnalysis,
        val musicalMap: MusicalMapEngine.MusicalMap,
        val remixPlan: RemixBrain.RemixPlan,
        val arrangement: ArrangementEngine.FullArrangement,
        val generatedMusic: MusicGeneratorEngine.GeneratedMusic,
        val vocalProcessedPcm: AudioPcmData,
        val mixedPcm: AudioPcmData,
        val masteredPcm: AudioPcmData,
        val masterWavFile: File,
        val preview30sFile: File? = null,
        val loudnessReport: LoudnessMeter.LoudnessReport? = null
    )

    /**
     * Menjalankan ONE-CLICK AUTO REMIX dari awal sampai berkas audio WAV siap diputar.
     */
    suspend fun executeAutoRemix(
        context: Context,
        vocalPcm: AudioPcmData?,
        beatPcm: AudioPcmData?,
        voiceTagPcm: AudioPcmData? = null,
        style: RemixBrain.RemixStyle = RemixBrain.RemixStyle.DJ_SLOW,
        targetBpmOverride: Float? = null,
        energyPreference: RemixBrain.EnergyPreference = RemixBrain.EnergyPreference.MEDIUM,
        focusPreference: RemixBrain.FocusPreference = RemixBrain.FocusPreference.BALANCED,
        seed: Long = System.currentTimeMillis(),
        generate30sPreviewOnly: Boolean = false,
        onStatusChanged: ((OutputEngine.OutputStatus, Float, String) -> Unit)? = null
    ): Result<WorkflowResult> = withContext(Dispatchers.Default) {
        try {
            // ==============================================================
            // 1. VALIDASI SUMBER AUDIO
            // ==============================================================
            if ((vocalPcm == null || vocalPcm.isSilent()) && (beatPcm == null || beatPcm.isSilent())) {
                val ex = IllegalArgumentException("Pilih minimal satu berkas audio (Vokal atau Beat) untuk memulai Auto Remix.")
                LogChatManager.error(LogModule.REMIX, "Validasi sumber audio gagal: trek kosong", ex, stage = "INPUT")
                LogChatManager.updatePipeline(PipelineStage.INPUT, StepStatus.FAILED, "Trek kosong")
                return@withContext Result.failure(ex)
            }

            LogChatManager.info(LogModule.REMIX, "REMIX_START: Memulai Auto Remix Workflow (${style.label})...", stage = "REMIX")
            LogChatManager.updatePipeline(PipelineStage.INPUT, StepStatus.SUCCESS, "Audio input valid")

            // Bersihkan file sementara lama di cache agar hemat memori & storage
            OutputEngine.cleanupTempFiles(context)

            // ==============================================================
            // 2. MUSIC UNDERSTANDING
            // ==============================================================
            onStatusChanged?.invoke(OutputEngine.OutputStatus.ANALYZING, 0.10f, "MENGANALISIS...")
            LogChatManager.info(LogModule.BPM, "Memulai analisis tempo & musikal...", stage = "ANALISIS")
            val analysisRes = MusicUnderstandingEngine.understandAudio(
                context = context,
                vocalPcm = vocalPcm,
                beatPcm = beatPcm,
                presetBpm = targetBpmOverride
            ) { frac, desc ->
                onStatusChanged?.invoke(OutputEngine.OutputStatus.ANALYZING, 0.10f + (frac * 0.10f), "MENGANALISIS... $desc")
            }
            if (analysisRes.isFailure) {
                val ex = analysisRes.exceptionOrNull()!!
                LogChatManager.error(LogModule.BPM, "Analisis audio gagal: ${ex.message}", ex, stage = "ANALISIS")
                LogChatManager.updatePipeline(PipelineStage.ANALYSIS, StepStatus.FAILED, ex.message ?: "Analisis gagal")
                return@withContext Result.failure(ex)
            }
            val analysis = analysisRes.getOrThrow()
            LogChatManager.updatePipeline(PipelineStage.ANALYSIS, StepStatus.SUCCESS, "BPM: ${analysis.bpm.toInt()} | Key: ${analysis.key.displayName}")

            // ==============================================================
            // 3. MUSICAL MAP & 4. REMIX BRAIN
            // ==============================================================
            onStatusChanged?.invoke(OutputEngine.OutputStatus.PLANNING, 0.22f, "MERENCANAKAN REMIX...")
            val musicalMap = MusicalMapEngine.buildMap(analysis, analysis.timeline)
            LogChatManager.updatePipeline(PipelineStage.MUSICAL_MAP, StepStatus.SUCCESS, "${musicalMap.totalBars} Bar")

            val remixPlan = RemixBrain.createPlan(
                analysis = analysis,
                musicalMap = musicalMap,
                style = style,
                targetBpmOverride = targetBpmOverride,
                energyPreference = energyPreference,
                focusPreference = focusPreference,
                seed = seed
            )
            LogChatManager.updatePipeline(PipelineStage.REMIX, StepStatus.SUCCESS, "${style.label} (${remixPlan.targetBpm.toInt()} BPM)")

            // ==============================================================
            // 5. ARRANGEMENT ENGINE
            // ==============================================================
            onStatusChanged?.invoke(OutputEngine.OutputStatus.ARRANGING, 0.32f, "MEMBUAT ARANSEMEN...")
            val arrangement = ArrangementEngine.createArrangement(musicalMap, remixPlan)
            LogChatManager.updatePipeline(PipelineStage.ARRANGEMENT, StepStatus.SUCCESS, "${arrangement.sections.size} Bagian DJ")

            // Sinkronkan Master Timeline dengan BPM target dari Remix Brain
            val masterTimeline = MasterTimeline.build(
                bpm = remixPlan.targetBpm,
                totalDurationMs = musicalMap.totalDurationMs
            ).copy(
                chordEvents = analysis.timeline.chordEvents,
                sections = arrangement.sections.map { it.toSongSection() }
            )

            // ==============================================================
            // 6. MUSIC GENERATOR ENGINE
            // ==============================================================
            onStatusChanged?.invoke(OutputEngine.OutputStatus.GENERATING, 0.40f, "MEMBUAT MUSIK...")
            val musicGenRes = MusicGeneratorEngine.generateMusic(
                timeline = masterTimeline,
                remixPlan = remixPlan,
                arrangement = arrangement
            ) { frac, desc ->
                onStatusChanged?.invoke(OutputEngine.OutputStatus.GENERATING, 0.40f + (frac * 0.15f), "MEMBUAT MUSIK... $desc")
            }
            if (musicGenRes.isFailure) {
                val ex = musicGenRes.exceptionOrNull()!!
                LogChatManager.error(LogModule.ARRANGEMENT, "Sintesis musik gagal: ${ex.message}", ex, stage = "GENERATOR")
                LogChatManager.updatePipeline(PipelineStage.GENERATOR, StepStatus.FAILED, ex.message ?: "Generator error")
                return@withContext Result.failure(ex)
            }
            val generatedMusic = musicGenRes.getOrThrow()
            LogChatManager.updatePipeline(PipelineStage.GENERATOR, StepStatus.SUCCESS, "Drum, Bass, Akor, Melodi")

            // ==============================================================
            // 7. VOCAL & FX ENGINE
            // ==============================================================
            onStatusChanged?.invoke(OutputEngine.OutputStatus.VOCAL_PROCESSING, 0.58f, "MEMPROSES VOKAL...")
            val vocalProcessConfig = VocalFxEngine.VocalProcessConfig(
                targetBpm = remixPlan.targetBpm,
                sourceBpm = analysis.bpm,
                duckingDepth = remixPlan.vocalPlan.duckingDepth,
                highPassFreqHz = remixPlan.vocalPlan.highPassFreqHz,
                presenceBoostDb = remixPlan.vocalPlan.presenceBoostDb,
                echoSend = remixPlan.vocalPlan.echoSend,
                reverbSend = remixPlan.vocalPlan.reverbSend,
                enableVocalChopInPreDrop = remixPlan.fxPlan.useVocalChopInPreDrop,
                voiceTagSample = voiceTagPcm
            )
            val processedVocal = VocalFxEngine.processVocal(
                rawVocalPcm = vocalPcm,
                timeline = masterTimeline,
                remixPlan = remixPlan,
                arrangement = arrangement,
                config = vocalProcessConfig
            )
            LogChatManager.updatePipeline(PipelineStage.VOCAL_FX, StepStatus.SUCCESS, "Presence & Ambience OK")

            // ==============================================================
            // 8. INTELLIGENT MIX ENGINE
            // ==============================================================
            onStatusChanged?.invoke(OutputEngine.OutputStatus.MIXING, 0.65f, "MIXING...")
            val mixConfig = IntelligentMixEngine.IntelligentMixConfig(
                vocalControls = IntelligentMixEngine.TrackControls(volume = remixPlan.mixPlan.vocalVolume),
                beatControls = IntelligentMixEngine.TrackControls(volume = remixPlan.mixPlan.beatVolume),
                drumControls = IntelligentMixEngine.TrackControls(volume = remixPlan.mixPlan.drumVolume),
                bassControls = IntelligentMixEngine.TrackControls(volume = remixPlan.mixPlan.bassVolume),
                chordControls = IntelligentMixEngine.TrackControls(volume = remixPlan.mixPlan.chordVolume),
                melodyControls = IntelligentMixEngine.TrackControls(volume = remixPlan.mixPlan.melodyVolume),
                padControls = IntelligentMixEngine.TrackControls(volume = remixPlan.mixPlan.padVolume),
                fxControls = IntelligentMixEngine.TrackControls(volume = remixPlan.mixPlan.fxVolume),
                masterGain = remixPlan.mixPlan.masterGain,
                isAutoMixEnabled = remixPlan.mixPlan.isAutoMixEnabled
            )
            val mixRes = IntelligentMixEngine.performIntelligentMix(
                vocalPcm = processedVocal,
                beatPcm = beatPcm,
                drumPcm = generatedMusic.drumPcm,
                bassPcm = generatedMusic.bassPcm,
                chordPcm = generatedMusic.chordPcm,
                melodyPcm = generatedMusic.melodyPcm,
                padPcm = generatedMusic.padPcm,
                fxPcm = generatedMusic.fxPcm,
                drumEvents = generatedMusic.drumEvents,
                remixPlan = remixPlan,
                arrangement = arrangement,
                config = mixConfig
            ) { frac, desc ->
                onStatusChanged?.invoke(OutputEngine.OutputStatus.MIXING, 0.65f + (frac * 0.12f), "MIXING... $desc")
            }
            if (mixRes.isFailure) {
                val ex = mixRes.exceptionOrNull()!!
                LogChatManager.error(LogModule.MIX, "Mixing gagal: ${ex.message}", ex, stage = "MIX")
                LogChatManager.updatePipeline(PipelineStage.MIX, StepStatus.FAILED, ex.message ?: "Mix error")
                return@withContext Result.failure(ex)
            }
            val mixedPcm = mixRes.getOrThrow()
            LogChatManager.updatePipeline(PipelineStage.MIX, StepStatus.SUCCESS, "Auto Gain Staging OK")

            // ==============================================================
            // 9. MASTER ENGINE (Tonal EQ, Glue Comp, Saturation, True Peak Limiter)
            // ==============================================================
            onStatusChanged?.invoke(OutputEngine.OutputStatus.MASTERING, 0.80f, "MASTERING...")
            val masterRes = MasterEngine.masterAudio(
                inputPcm = mixedPcm,
                preset = remixPlan.masterPlan.preset
            ) { frac, desc ->
                onStatusChanged?.invoke(OutputEngine.OutputStatus.MASTERING, 0.80f + (frac * 0.10f), "MASTERING... $desc")
            }
            if (masterRes.isFailure) {
                val ex = masterRes.exceptionOrNull()!!
                LogChatManager.error(LogModule.MASTER, "Mastering audio gagal: ${ex.message}", ex, stage = "MASTER")
                LogChatManager.updatePipeline(PipelineStage.MASTER, StepStatus.FAILED, ex.message ?: "Master error")
                return@withContext Result.failure(ex)
            }
            val masterOutput = masterRes.getOrThrow()
            val masteredPcm = masterOutput.pcmData
            LogChatManager.updatePipeline(PipelineStage.MASTER, StepStatus.SUCCESS, "Preset ${remixPlan.masterPlan.preset.label}")

            // ==============================================================
            // 10. OUTPUT ENGINE (Render Berkas Master WAV & Preview 30s)
            // ==============================================================
            onStatusChanged?.invoke(OutputEngine.OutputStatus.RENDERING, 0.90f, "RENDERING...")

            val pcmToRender = if (generate30sPreviewOnly) {
                OutputEngine.extract30SecondPreviewPcm(masteredPcm, arrangement)
            } else {
                masteredPcm
            }

            val prefix = if (generate30sPreviewOnly) "Preview30s_${style.name}" else "MasterRemix_${style.name}"
            val targetFile = OutputEngine.createTempWavFile(context, prefix)

            val renderFileRes = OutputEngine.renderWavFile(context, pcmToRender, targetFile) { frac, desc ->
                onStatusChanged?.invoke(OutputEngine.OutputStatus.RENDERING, 0.90f + (frac * 0.06f), "RENDERING... $desc")
            }
            if (renderFileRes.isFailure) {
                val ex = renderFileRes.exceptionOrNull()!!
                LogChatManager.error(LogModule.EXPORT, "Render berkas WAV gagal: ${ex.message}", ex, stage = "EXPORT")
                LogChatManager.updatePipeline(PipelineStage.EXPORT, StepStatus.FAILED, ex.message ?: "Render error")
                return@withContext Result.failure(ex)
            }
            val finalWavFile = renderFileRes.getOrThrow()

            // ==============================================================
            // 11. VALIDASI INTEGRITAS BERKAS KELUARAN
            // ==============================================================
            onStatusChanged?.invoke(OutputEngine.OutputStatus.VALIDATING, 0.97f, "MEMVALIDASI...")
            val validation = OutputEngine.validateOutputFile(finalWavFile)
            if (!validation.isValid) {
                val reason = "Validasi berkas master audio gagal: ${validation.errorMessage ?: "Berkas audio rusak atau clipping"}"
                LogChatManager.error(LogModule.EXPORT, reason, stage = "EXPORT", file = finalWavFile.name)
                LogChatManager.updatePipeline(PipelineStage.EXPORT, StepStatus.FAILED, "Validasi gagal")
                onStatusChanged?.invoke(OutputEngine.OutputStatus.ERROR, 0f, reason)
                return@withContext Result.failure(IllegalStateException(reason))
            }

            LogChatManager.info(
                LogModule.EXPORT,
                "REMIX_COMPLETE: Hasil render ${finalWavFile.name} valid dan siap.",
                detail = "Ukuran: ${finalWavFile.length() / 1024} KB | LUFS: ${masterOutput.loudnessReport.formattedLufs}",
                stage = "EXPORT",
                file = finalWavFile.name
            )
            LogChatManager.updatePipeline(PipelineStage.EXPORT, StepStatus.SUCCESS, "${finalWavFile.name}")
            LogChatManager.updateLastSuccessfulStep("Export")

            onStatusChanged?.invoke(OutputEngine.OutputStatus.READY, 1.0f, "SIAP.")

            val workflowResult = WorkflowResult(
                analysis = analysis,
                musicalMap = musicalMap,
                remixPlan = remixPlan,
                arrangement = arrangement,
                generatedMusic = generatedMusic,
                vocalProcessedPcm = processedVocal,
                mixedPcm = mixedPcm,
                masteredPcm = masteredPcm,
                masterWavFile = finalWavFile,
                preview30sFile = if (generate30sPreviewOnly) finalWavFile else null,
                loudnessReport = masterOutput.loudnessReport
            )

            Result.success(workflowResult)
        } catch (e: CancellationException) {
            onStatusChanged?.invoke(OutputEngine.OutputStatus.CANCELLED, 0f, "Proses Auto Remix dibatalkan.")
            throw e
        } catch (e: Exception) {
            onStatusChanged?.invoke(OutputEngine.OutputStatus.ERROR, 0f, "Terjadi kesalahan: ${e.localizedMessage ?: e.message}")
            Result.failure(e)
        }
    }
}
