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
import com.autoremix.djslow.engine.mastering.MasterBlockProcessor
import com.autoremix.djslow.engine.mastering.MasteringPreset
import com.autoremix.djslow.engine.melody.MelodyEngine
import com.autoremix.djslow.engine.music.ChordEngine
import com.autoremix.djslow.engine.music.MusicKey
import com.autoremix.djslow.engine.music.MusicMode
import com.autoremix.djslow.engine.music.PitchClass
import com.autoremix.djslow.engine.pad.PadEngine
import com.autoremix.djslow.engine.pcm.AudioMemoryManager
import com.autoremix.djslow.engine.pcm.AudioPcmData
import com.autoremix.djslow.engine.pcm.AudioPcmDecoder
import com.autoremix.djslow.engine.structure.SongSectionType
import com.autoremix.djslow.engine.synth.BassEngine
import com.autoremix.djslow.engine.synth.BassPatternType
import com.autoremix.djslow.engine.synth.ChordSynthEngine
import com.autoremix.djslow.engine.synth.ChordSynthPreset
import com.autoremix.djslow.engine.timeline.MasterTimeline
import com.autoremix.djslow.engine.wav.StreamingWavWriter
import com.autoremix.djslow.engine.wav.WavRenderer
import com.autoremix.djslow.engine.wav.WavValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.pow

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

            // 5. Inisialisasi Voice Aransemen Akor & Voice State
            onProgress(PipelineStep.SINTESIS_INSTRUMEN, 0.40f, "Mempersiapkan voice instrumen sintetis (Streaming Mode)...")
            val totalFramesLong = totalFrames
            val chordVolumeFinal = params.chordSettings.volume
            val chordPresetFinal = chordPreset
            val chordVoices = timeline.chordEvents.mapNotNull { event ->
                val eventStartFrame = event.startSample
                val eventEndFrame = minOf(event.endSample, totalFramesLong)
                val eventFrames = eventEndFrame - eventStartFrame
                if (eventFrames <= 0 || eventStartFrame >= totalFramesLong) {
                    null
                } else {
                    val sec = arrangementPlan.sections.firstOrNull { event.barIndex >= it.startBar && event.barIndex < it.endBar }
                    val secVolume = when (sec?.sectionType) {
                        SongSectionType.DROP, SongSectionType.MAIN_DROP, SongSectionType.PEAK, SongSectionType.FINAL_DROP -> chordVolumeFinal * 1.0f
                        SongSectionType.BREAK, SongSectionType.BREAKDOWN -> chordVolumeFinal * 0.65f
                        SongSectionType.BUILD_UP, SongSectionType.BUILD_UP_2, SongSectionType.FINAL_BUILD -> chordVolumeFinal * 0.80f
                        SongSectionType.PRE_DROP -> chordVolumeFinal * 0.60f
                        SongSectionType.INTRO, SongSectionType.OUTRO -> chordVolumeFinal * 0.60f
                        else -> chordVolumeFinal
                    }
                    ChordSynthEngine.ChordVoiceState(
                        event = event,
                        totalFrames = eventFrames,
                        sampleRate = sampleRate,
                        preset = chordPresetFinal,
                        volume = secVolume
                    )
                }
            }

            // 6. Inisialisasi DSP Processor & Streaming Writers
            val drumProcessor = DrumBlockProcessor(sampleRate, 2)
            val bassProcessor = BassBlockProcessor(sampleRate, 2)
            val vocalDucker = VocalDucker.VocalDuckProcessor(
                sampleRate = sampleRate,
                duckingDepthDb = masteringPreset.vocalPocketDepthDb
            )
            val masterProcessor = MasterBlockProcessor(
                preset = masteringPreset,
                sampleRate = sampleRate,
                channels = 2
            )
            val mixGains = MixEngine.computeEffectiveGains(params)

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val tempDir = com.autoremix.djslow.engine.temp.TempFileManager.getTempDir(context)
            val unmasteredFile = File(tempDir, "TEMP_UNMASTERED_$timestamp.wav")
            val outputDir = File(context.filesDir, "rendered_wav").apply { mkdirs() }
            val outputFile = File(outputDir, "DJ_SLOW_MIX_$timestamp.wav")

            val unmasteredWriter = StreamingWavWriter(unmasteredFile, sampleRate, 2)
            val masteredWriter = StreamingWavWriter(outputFile, sampleRate, 2)

            val blockSize = 16384
            val channels = 2

            val drumBlock = FloatArray(blockSize * channels)
            val bassBlock = FloatArray(blockSize * channels)
            val chordBlock = FloatArray(blockSize * channels)
            val melodyBlock = FloatArray(blockSize * channels)
            val padBlock = FloatArray(blockSize * channels)
            val fxBlock = FloatArray(blockSize * channels)
            val vocalBlock = FloatArray(blockSize * channels)
            val beatBlock = FloatArray(blockSize * channels)
            val mixedBlock = FloatArray(blockSize * channels)
            val masterBlock = FloatArray(blockSize * channels)

            val totalBlocks = ((totalFrames + blockSize - 1) / blockSize).toInt().coerceAtLeast(1)

            try {
                for (blockIndex in 0 until totalBlocks) {
                    val startFrame = blockIndex.toLong() * blockSize
                    val frameCount = minOf(blockSize.toLong(), totalFrames - startFrame).toInt()
                    val sampleCount = frameCount * channels

                    // 1. Drum Block
                    drumBlock.fill(0f, 0, sampleCount)
                    if (params.drumSettings.volume > 0.0f && !params.drumSettings.isMuted) {
                        DrumEngine.renderDrumBlock(
                            events = arrangementPlan.drumEvents,
                            startFrame = startFrame,
                            frameCount = frameCount,
                            sampleRate = sampleRate,
                            outBuffer = drumBlock,
                            offset = 0
                        )
                        drumProcessor.processBlock(drumBlock)
                    }

                    // 2. Bass Block
                    bassBlock.fill(0f, 0, sampleCount)
                    if (params.bassSettings.volume > 0.0f && !params.bassSettings.isMuted) {
                        BassEngine.renderBassBlock(
                            bassEvents = bassEvents,
                            startFrame = startFrame,
                            frameCount = frameCount,
                            sampleRate = sampleRate,
                            outBuffer = bassBlock,
                            offset = 0,
                            volume = params.bassSettings.volume * preset.bassMultiplier
                        )
                        if (arrangementPlan.drumEvents.isNotEmpty()) {
                            KickBassEngine.applySidechainDuckingBlock(
                                bassBlock = bassBlock,
                                startFrame = startFrame,
                                frameCount = frameCount,
                                drumEvents = arrangementPlan.drumEvents,
                                sampleRate = sampleRate,
                                channels = channels
                            )
                        }
                        bassProcessor.processBlock(bassBlock)
                    }

                    // 3. Chord Block
                    chordBlock.fill(0f, 0, sampleCount)
                    if (params.chordSettings.volume > 0.0f && !params.chordSettings.isMuted) {
                        val endFrame = startFrame + frameCount
                        val activeVoices = chordVoices.filter { voice ->
                            voice.event.endSample > startFrame && voice.event.startSample < endFrame
                        }
                        if (activeVoices.isNotEmpty()) {
                            ChordSynthEngine.renderChordBlock(
                                activeVoices = activeVoices,
                                startFrame = startFrame,
                                frameCount = frameCount,
                                outBuffer = chordBlock,
                                offset = 0,
                                channels = channels
                            )
                        }
                    }

                    // 4. Melody Block
                    melodyBlock.fill(0f, 0, sampleCount)
                    if (params.melodySettings.volume > 0.0f && !params.melodySettings.isMuted) {
                        MelodyEngine.renderMelodyBlock(
                            events = arrangementPlan.melodyEvents,
                            startFrame = startFrame,
                            frameCount = frameCount,
                            sampleRate = sampleRate,
                            outBuffer = melodyBlock,
                            offset = 0
                        )
                    }

                    // 5. Pad Block
                    padBlock.fill(0f, 0, sampleCount)
                    if (params.padSettings.volume > 0.0f && !params.padSettings.isMuted) {
                        PadEngine.renderPadBlock(
                            events = arrangementPlan.padEvents,
                            startFrame = startFrame,
                            frameCount = frameCount,
                            sampleRate = sampleRate,
                            outBlock = padBlock,
                            offset = 0
                        )
                    }

                    // 6. Transition FX Block
                    fxBlock.fill(0f, 0, sampleCount)
                    if (arrangementPlan.transitionEvents.isNotEmpty()) {
                        SectionEngines.renderTransitionBlock(
                            events = arrangementPlan.transitionEvents,
                            chunkStartFrame = startFrame,
                            frameCount = frameCount,
                            sampleRate = sampleRate,
                            outBuffer = fxBlock,
                            offset = 0
                        )
                    }

                    // 7. Vocal Block
                    vocalBlock.fill(0f, 0, sampleCount)
                    if (vocalPcm != null && !vocalPcm.isSilent()) {
                        val vocalStartSample = (startFrame * channels).toInt()
                        val vocalLen = vocalPcm.samples.size
                        for (i in 0 until sampleCount) {
                            val srcIdx = vocalStartSample + i
                            if (srcIdx < vocalLen) {
                                vocalBlock[i] = vocalPcm.samples[srcIdx]
                            }
                        }
                    }

                    // 8. Beat Block
                    beatBlock.fill(0f, 0, sampleCount)
                    if (beatPcm != null && !beatPcm.isSilent()) {
                        val beatStartSample = (startFrame * channels).toInt()
                        val beatLen = beatPcm.samples.size
                        for (i in 0 until sampleCount) {
                            val srcIdx = beatStartSample + i
                            if (srcIdx < beatLen) {
                                beatBlock[i] = beatPcm.samples[srcIdx]
                            }
                        }
                    }

                    // 9. Vocal Ducking pada bus musik
                    if (vocalPcm != null && !vocalPcm.isSilent()) {
                        vocalDucker.processBlock(chordBlock, vocalBlock, frameCount, channels)
                        vocalDucker.processBlock(melodyBlock, vocalBlock, frameCount, channels)
                        vocalDucker.processBlock(padBlock, vocalBlock, frameCount, channels)
                    }

                    // 10. Multi-Bus Mixing
                    mixedBlock.fill(0f, 0, sampleCount)
                    MixEngine.mixBlock(
                        vocalBlock = vocalBlock,
                        beatBlock = beatBlock,
                        drumBlock = drumBlock,
                        bassBlock = bassBlock,
                        chordBlock = chordBlock,
                        melodyBlock = melodyBlock,
                        padBlock = padBlock,
                        fxBlock = fxBlock,
                        frameCount = frameCount,
                        gains = mixGains,
                        outMixed = mixedBlock,
                        offset = 0
                    )

                    // Simpan pre-master audio ke berkas temporer
                    unmasteredWriter.writeChunk(mixedBlock, 0, sampleCount)

                    // 11. Auto Mastering Block
                    System.arraycopy(mixedBlock, 0, masterBlock, 0, sampleCount)
                    masterProcessor.processBlock(masterBlock)

                    // Tulis hasil master langsung ke disk
                    masteredWriter.writeChunk(masterBlock, 0, sampleCount)

                    // Telemetri RAM
                    if (blockIndex % 4 == 0 || blockIndex == totalBlocks - 1) {
                        AudioMemoryManager.logBlockMemory("PIPELINE_STREAMING", startFrame, frameCount)
                    }

                    val progressFrac = (blockIndex + 1).toFloat() / totalBlocks
                    onProgress(
                        PipelineStep.RENDER_WAV,
                        0.45f + 0.45f * progressFrac,
                        "Streaming render (${(progressFrac * 100).toInt()}%)..."
                    )
                }
            } finally {
                unmasteredWriter.close()
                masteredWriter.close()
            }

            // 12. Validasi Integritas Berkas Master WAV
            onProgress(PipelineStep.VALIDASI, 0.95f, "Memvalidasi WAV RIFF, LUFS, True Peak & Anti-Clipping...")
            val validation = WavValidator.validate(outputFile)
            val unmasteredValidation = WavValidator.validate(unmasteredFile)

            if (!validation.isValid) {
                if (outputFile.exists()) outputFile.delete()
                com.autoremix.djslow.engine.temp.TempFileManager.deleteSafe(unmasteredFile)
                val errMsg = validation.errorMessage ?: "Rendering gagal. Silakan ulangi."
                return@withContext Result.failure(IllegalStateException(errMsg))
            }

            val loudnessReport = LoudnessMeter.LoudnessReport(
                lufsIntegrated = validation.lufsIntegrated,
                truePeakDbtp = validation.truePeakDbtp,
                peakLinear = validation.peakAmplitude,
                peakDbfs = validation.peakDbfs,
                rmsLinear = kotlin.math.max(0.00001f, kotlin.math.min(1.0f, 10.0.pow(validation.rmsDbfs / 20.0).toFloat())),
                rmsDbfs = validation.rmsDbfs,
                dynamicRangeLu = 8.0f,
                isClipping = validation.isClipping
            )

            onProgress(
                PipelineStep.SELESAI,
                1.0f,
                "Master WAV berhasil & tervalidasi (${validation.durationMs / 1000}s, ${validation.formattedLufs}, ${validation.formattedTruePeak})."
            )

            return@withContext Result.success(
                PipelineResult(
                    wavFile = outputFile,
                    validation = validation,
                    durationMs = validation.durationMs,
                    timeline = timeline,
                    key = musicKey,
                    targetBpm = targetBpm,
                    arrangementPlan = arrangementPlan,
                    loudnessReport = loudnessReport,
                    masteringPreset = masteringPreset,
                    unmasteredWavFile = unmasteredFile,
                    unmasteredLufs = unmasteredValidation.lufsIntegrated
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
