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
        val mixedPcm: AudioPcmData = AudioPcmData.createEmpty(44100, 2),
        val masteredPcm: AudioPcmData = AudioPcmData.createEmpty(44100, 2),
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

            // Durasi efektif: jika hanya membuat preview 30s, batasi timeline tepat 30 detik untuk menghemat 90% memori RAM
            val effectiveDurationMs = if (generate30sPreviewOnly) {
                minOf(30_000L, musicalMap.totalDurationMs)
            } else {
                musicalMap.totalDurationMs
            }

            val maxFrames = (effectiveDurationMs * 44100L / 1000L).toInt()
            val effectiveVocalPcm = if (generate30sPreviewOnly && vocalPcm != null && vocalPcm.totalFrames > maxFrames) {
                vocalPcm.slice(0, maxFrames)
            } else {
                vocalPcm
            }
            val effectiveBeatPcm = if (generate30sPreviewOnly && beatPcm != null && beatPcm.totalFrames > maxFrames) {
                beatPcm.slice(0, maxFrames)
            } else {
                beatPcm
            }

            // Sinkronkan Master Timeline dengan BPM target dari Remix Brain
            val masterTimeline = MasterTimeline.build(
                bpm = remixPlan.targetBpm,
                totalDurationMs = effectiveDurationMs
            ).copy(
                chordEvents = analysis.timeline.chordEvents,
                sections = arrangement.sections.map { it.toSongSection() }
            )

            com.autoremix.djslow.engine.pcm.AudioMemoryManager.logMemoryUsage("Workflow.PreGeneration(frames=${masterTimeline.totalFrames})")
            com.autoremix.djslow.engine.pcm.AudioMemoryManager.trimMemoryIfNeeded("Workflow.PreGeneration")

            // ==============================================================
            // 6. MUSIC GENERATOR ENGINE (Event Scheduling - 0 Full-Track PCM)
            // ==============================================================
            onStatusChanged?.invoke(OutputEngine.OutputStatus.GENERATING, 0.40f, "MEMBUAT JADWAL MUSIK...")
            val scheduledEvents = MusicGeneratorEngine.scheduleAllEvents(
                timeline = masterTimeline,
                remixPlan = remixPlan,
                arrangement = arrangement
            )
            val generatedMusic = MusicGeneratorEngine.GeneratedMusic(
                drumEvents = scheduledEvents.drumEvents,
                bassEvents = scheduledEvents.bassEvents,
                melodyEvents = scheduledEvents.melodyEvents,
                transitionEvents = scheduledEvents.transitionEvents
            )
            LogChatManager.updatePipeline(PipelineStage.GENERATOR, StepStatus.SUCCESS, "Drum, Bass, Akor, Melodi (Streaming)")

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
                rawVocalPcm = effectiveVocalPcm,
                timeline = masterTimeline,
                remixPlan = remixPlan,
                arrangement = arrangement,
                config = vocalProcessConfig
            )
            LogChatManager.updatePipeline(PipelineStage.VOCAL_FX, StepStatus.SUCCESS, "Presence & Ambience OK")

            // ==============================================================
            // 8, 9, 10. STREAMING MIXING, MASTERING & DIRECT WAV WRITER
            // Pemrosesan blok per blok (16384 frames) langsung ke disk via StreamingWavWriter.
            // TIDAK ADA ALOKASI FloatArray(totalFrames * channels) untuk instrumen dan master.
            // ==============================================================
            onStatusChanged?.invoke(OutputEngine.OutputStatus.MIXING, 0.65f, "STREAMING MIX & MASTER...")
            LogChatManager.updatePipeline(PipelineStage.MIX, StepStatus.PENDING, "Streaming 16384-frame blocks...")

            val prefix = if (generate30sPreviewOnly) "Preview30s_${style.name}" else "MasterRemix_${style.name}"
            val targetFile = OutputEngine.createTempWavFile(context, prefix)

            val totalFrames = masterTimeline.totalFrames.toInt()
            val sampleRate = masterTimeline.sampleRate
            val masterPreset = remixPlan.masterPlan.preset
            val masterProcessor = com.autoremix.djslow.engine.mastering.MasterBlockProcessor(
                preset = masterPreset,
                sampleRate = sampleRate,
                channels = 2
            )
            val streamingWriter = com.autoremix.djslow.engine.wav.StreamingWavWriter(targetFile, sampleRate, 2)

            val blockSize = 16384
            val channels = 2
            val musicBlock = FloatArray(blockSize * channels)
            val mixedBlock = FloatArray(blockSize * channels)

            val vocalVol = remixPlan.mixPlan.vocalVolume
            val beatVol = remixPlan.mixPlan.beatVolume
            val masterGain = remixPlan.mixPlan.masterGain

            val vocalSamples = processedVocal.samples
            val beatSamples = effectiveBeatPcm?.samples

            val totalBlocks = ((totalFrames + blockSize - 1) / blockSize).coerceAtLeast(1)

            try {
                for (blockIndex in 0 until totalBlocks) {
                    val startFrame = blockIndex.toLong() * blockSize
                    val frameCount = minOf(blockSize.toLong(), totalFrames.toLong() - startFrame).toInt()
                    val sampleCount = frameCount * channels

                    // Render instrumen musik langsung untuk blok ini
                    MusicGeneratorEngine.renderMusicBlock(
                        startFrame = startFrame,
                        frameCount = frameCount,
                        sampleRate = sampleRate,
                        timeline = masterTimeline,
                        events = scheduledEvents,
                        arrangement = arrangement,
                        outStereo = musicBlock
                    )

                    // Summing vokal dan beat ke mixedBlock
                    val vocalStartSample = (startFrame * channels).toInt()
                    for (i in 0 until sampleCount) {
                        var s = musicBlock[i]
                        val vocalIdx = vocalStartSample + i
                        if (vocalIdx < vocalSamples.size) {
                            s += vocalSamples[vocalIdx] * vocalVol
                        }
                        if (beatSamples != null && vocalIdx < beatSamples.size) {
                            s += beatSamples[vocalIdx] * beatVol
                        }
                        mixedBlock[i] = s * masterGain
                    }

                    // Master processor untuk blok ini (in-place)
                    if (sampleCount < mixedBlock.size) {
                        val activeSlice = mixedBlock.copyOfRange(0, sampleCount)
                        masterProcessor.processBlock(activeSlice)
                        streamingWriter.writeChunk(activeSlice, 0, sampleCount)
                    } else {
                        masterProcessor.processBlock(mixedBlock)
                        streamingWriter.writeChunk(mixedBlock, 0, sampleCount)
                    }

                    val progressFrac = (blockIndex + 1).toFloat() / totalBlocks
                    onStatusChanged?.invoke(
                        OutputEngine.OutputStatus.MIXING,
                        0.65f + (progressFrac * 0.25f),
                        "STREAMING MIX & MASTER... ${(progressFrac * 100).toInt()}%"
                    )
                }
            } finally {
                streamingWriter.close()
            }
            val finalWavFile = targetFile

            LogChatManager.updatePipeline(PipelineStage.MIX, StepStatus.SUCCESS, "Streaming Mix OK")
            LogChatManager.updatePipeline(PipelineStage.MASTER, StepStatus.SUCCESS, "Preset ${masterPreset.label}")
            onStatusChanged?.invoke(OutputEngine.OutputStatus.RENDERING, 0.90f, "RENDERING SELESAI")

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
                detail = "Ukuran: ${finalWavFile.length() / 1024} KB",
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
                masterWavFile = finalWavFile,
                preview30sFile = if (generate30sPreviewOnly) finalWavFile else null,
                loudnessReport = null
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
