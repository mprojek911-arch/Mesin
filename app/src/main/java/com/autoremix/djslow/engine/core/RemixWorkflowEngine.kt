package com.autoremix.djslow.engine.core

import android.content.Context
import com.autoremix.djslow.engine.pcm.AudioPcmData
import com.autoremix.djslow.engine.timeline.MasterTimeline
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
        val preview30sFile: File? = null
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
                return@withContext Result.failure(
                    IllegalArgumentException("Pilih minimal satu berkas audio (Vokal atau Beat) untuk memulai Auto Remix.")
                )
            }

            // ==============================================================
            // 2. MUSIC UNDERSTANDING
            // ==============================================================
            onStatusChanged?.invoke(OutputEngine.OutputStatus.ANALYZING, 0.10f, "1/6 Menganalisis BPM, Key, dan Frasa Vokal...")
            val analysisRes = MusicUnderstandingEngine.understandAudio(
                context = context,
                vocalPcm = vocalPcm,
                beatPcm = beatPcm,
                presetBpm = targetBpmOverride
            ) { frac, desc ->
                onStatusChanged?.invoke(OutputEngine.OutputStatus.ANALYZING, 0.10f + (frac * 0.10f), desc)
            }
            if (analysisRes.isFailure) {
                return@withContext Result.failure(analysisRes.exceptionOrNull()!!)
            }
            val analysis = analysisRes.getOrThrow()

            // ==============================================================
            // 3. MUSICAL MAP
            // ==============================================================
            onStatusChanged?.invoke(OutputEngine.OutputStatus.PLANNING, 0.22f, "2/6 Membangun Musical Map per Bar & Beat...")
            val musicalMap = MusicalMapEngine.buildMap(analysis, analysis.timeline)

            // ==============================================================
            // 4. REMIX BRAIN (Pengambil Keputusan Utama)
            // ==============================================================
            onStatusChanged?.invoke(OutputEngine.OutputStatus.PLANNING, 0.28f, "2/6 Remix Brain merancang aransemen gaya ${style.label}...")
            val remixPlan = RemixBrain.createPlan(
                analysis = analysis,
                musicalMap = musicalMap,
                style = style,
                targetBpmOverride = targetBpmOverride,
                energyPreference = energyPreference,
                focusPreference = focusPreference,
                seed = seed
            )

            // ==============================================================
            // 5. ARRANGEMENT ENGINE
            // ==============================================================
            onStatusChanged?.invoke(OutputEngine.OutputStatus.PLANNING, 0.35f, "2/6 Menyusun timeline struktur 8 seksi lagu...")
            val arrangement = ArrangementEngine.createArrangement(musicalMap, remixPlan)

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
            onStatusChanged?.invoke(OutputEngine.OutputStatus.GENERATING, 0.40f, "3/6 Menyintesis instrumen (Kick, Bass, Akor, Melodi, Pad, FX)...")
            val musicGenRes = MusicGeneratorEngine.generateMusic(
                timeline = masterTimeline,
                remixPlan = remixPlan,
                arrangement = arrangement
            ) { frac, desc ->
                onStatusChanged?.invoke(OutputEngine.OutputStatus.GENERATING, 0.40f + (frac * 0.15f), desc)
            }
            if (musicGenRes.isFailure) {
                return@withContext Result.failure(musicGenRes.exceptionOrNull()!!)
            }
            val generatedMusic = musicGenRes.getOrThrow()

            // ==============================================================
            // 7. VOCAL & FX ENGINE
            // ==============================================================
            onStatusChanged?.invoke(OutputEngine.OutputStatus.GENERATING, 0.58f, "3/6 Memproses vokal (Chop, Echo, Pitch, Alignment)...")
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

            // ==============================================================
            // 8. INTELLIGENT MIX ENGINE
            // ==============================================================
            onStatusChanged?.invoke(OutputEngine.OutputStatus.MIXING, 0.65f, "4/6 Intelligent Multi-Bus Summing & Auto Mix...")
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
                onStatusChanged?.invoke(OutputEngine.OutputStatus.MIXING, 0.65f + (frac * 0.12f), desc)
            }
            if (mixRes.isFailure) {
                return@withContext Result.failure(mixRes.exceptionOrNull()!!)
            }
            val mixedPcm = mixRes.getOrThrow()

            // ==============================================================
            // 9. MASTER ENGINE (Tonal EQ, Glue Comp, Saturation, True Peak Limiter)
            // ==============================================================
            onStatusChanged?.invoke(OutputEngine.OutputStatus.MASTERING, 0.80f, "5/6 Auto Mastering & True Peak Protection...")
            val masterRes = MasterEngine.masterAudio(
                inputPcm = mixedPcm,
                preset = remixPlan.masterPlan.preset
            ) { frac, desc ->
                onStatusChanged?.invoke(OutputEngine.OutputStatus.MASTERING, 0.80f + (frac * 0.10f), desc)
            }
            if (masterRes.isFailure) {
                return@withContext Result.failure(masterRes.exceptionOrNull()!!)
            }
            val masteredPcm = masterRes.getOrThrow().pcmData

            // ==============================================================
            // 10. OUTPUT ENGINE (Render Berkas Master WAV & Preview 30s)
            // ==============================================================
            onStatusChanged?.invoke(OutputEngine.OutputStatus.RENDERING, 0.90f, "6/6 Merender berkas WAV master 44.1 kHz 16-bit...")

            val pcmToRender = if (generate30sPreviewOnly) {
                OutputEngine.extract30SecondPreviewPcm(masteredPcm, arrangement)
            } else {
                masteredPcm
            }

            val prefix = if (generate30sPreviewOnly) "Preview30s_${style.name}" else "MasterRemix_${style.name}"
            val targetFile = OutputEngine.createTempWavFile(context, prefix)

            val renderFileRes = OutputEngine.renderWavFile(context, pcmToRender, targetFile) { frac, desc ->
                onStatusChanged?.invoke(OutputEngine.OutputStatus.RENDERING, 0.90f + (frac * 0.08f), desc)
            }
            if (renderFileRes.isFailure) {
                return@withContext Result.failure(renderFileRes.exceptionOrNull()!!)
            }
            val finalWavFile = renderFileRes.getOrThrow()

            onStatusChanged?.invoke(OutputEngine.OutputStatus.READY, 1.0f, "Selesai! Audio remix master siap diputar.")

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
                preview30sFile = if (generate30sPreviewOnly) finalWavFile else null
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
