package com.autoremix.djslow.engine.arrangement

import com.autoremix.djslow.engine.analysis.AutoEnergyAnalyzer
import com.autoremix.djslow.engine.analysis.BeatContentAnalyzer.BeatContentAnalysis
import com.autoremix.djslow.engine.analysis.BeatContentAnalyzer
import com.autoremix.djslow.engine.drum.DrumEngine
import com.autoremix.djslow.engine.drum.DrumEvent
import com.autoremix.djslow.engine.melody.MelodyEngine
import com.autoremix.djslow.engine.melody.MelodyEvent
import com.autoremix.djslow.engine.music.Chord
import com.autoremix.djslow.engine.music.MusicKey
import com.autoremix.djslow.engine.pad.PadEngine
import com.autoremix.djslow.engine.pad.PadEvent
import com.autoremix.djslow.engine.pcm.AudioPcmData
import com.autoremix.djslow.engine.structure.EnergyCurve
import com.autoremix.djslow.engine.structure.EnergyCurveEngine
import com.autoremix.djslow.engine.structure.SongSection
import com.autoremix.djslow.engine.structure.SongStructureDetector
import com.autoremix.djslow.engine.timeline.MasterTimeline
import kotlin.math.max

/**
 * Hasil keluaran lengkap dari AutoArranger.
 */
data class AutoArrangementPlan(
    val sections: List<SongSection>,
    val energyCurve: EnergyCurve,
    val beatAnalysis: BeatContentAnalysis,
    val energyAnalysis: AutoEnergyAnalyzer.EnergyAnalysisResult,
    val drumEvents: List<DrumEvent>,
    val melodyEvents: List<MelodyEvent>,
    val padEvents: List<PadEvent>,
    val transitionEvents: List<TransitionFxEvent>,
    val preset: AutoDjPreset,
    val melodySeed: Long,
    val updatedTimeline: MasterTimeline
)

/**
 * Auto Arranger:
 * Otak aransemen musik otomatis DJ Slow:
 * Mengintegrasikan struktur seksi lagu, otomasi kurva energi, pola drum adaptif,
 * melodi algoritmik, pad harmonis, bass punchy, dan transisi mulus.
 */
object AutoArranger {

    fun arrange(
        vocalPcm: AudioPcmData?,
        beatPcm: AudioPcmData?,
        timeline: MasterTimeline,
        key: MusicKey,
        chords: List<Chord>,
        preset: AutoDjPreset,
        melodySeed: Long
    ): AutoArrangementPlan {
        val totalBars = max(4, timeline.totalBars)
        val bpm = timeline.bpm
        val sampleRate = timeline.sampleRate
        val totalFrames = timeline.totalFrames
        val samplesPerBar = ((sampleRate * 60.0 / bpm) * 4.0).toLong()

        // 1. Analisis energi audio nyata
        val energyResult = AutoEnergyAnalyzer.analyzeEnergy(
            vocalPcm = vocalPcm,
            beatPcm = beatPcm,
            totalBars = totalBars,
            samplesPerBar = samplesPerBar
        )

        // 2. Analisis trek beat pengguna (mendeteksi apakah beat sudah memiliki drum)
        val beatAnalysis = BeatContentAnalyzer.analyze(beatPcm)

        // 3. Deteksi struktur seksi lagu dengan perlindungan frasa vokal
        val structureResult = SongStructureDetector.detectStructure(
            totalBars = totalBars,
            bpm = bpm,
            sampleRate = sampleRate,
            energyResult = energyResult
        )
        val sections = structureResult.sections

        // 4. Bangun kurva energi dinamis
        val energyCurve = EnergyCurveEngine.buildEnergyCurve(sections, totalFrames)

        // 5. Jadwalkan pola drum bervariasi
        val drumEvents = DrumEngine.scheduleDrumEvents(
            sections = sections,
            energyCurve = energyCurve,
            bpm = bpm,
            sampleRate = sampleRate,
            totalBars = totalBars,
            beatAnalysis = beatAnalysis,
            preset = preset
        )

        // 6. Jadwalkan melodi algoritmik deterministik sesuai Seed
        val melodyEvents = MelodyEngine.scheduleMelodyEvents(
            key = key,
            chords = chords,
            bpm = bpm,
            sampleRate = sampleRate,
            totalBars = totalBars,
            sections = sections,
            energyCurve = energyCurve,
            seed = melodySeed,
            preset = preset
        )

        // 7. Jadwalkan Pad hangat
        val hasVocal = vocalPcm != null && !vocalPcm.isSilent(0.01f)
        val padEvents = PadEngine.schedulePadEvents(
            key = key,
            chords = chords,
            bpm = bpm,
            sampleRate = sampleRate,
            totalBars = totalBars,
            sections = sections,
            energyCurve = energyCurve,
            preset = preset,
            hasVocal = hasVocal
        )

        // 8. Jadwalkan transisi dan risers
        val transitionEvents = SectionEngines.scheduleTransitionEvents(
            sections = sections,
            sampleRate = sampleRate,
            samplesPerBar = samplesPerBar
        )

        // 9. Update Master Timeline dengan event aransemen lengkap
        val updatedTimeline = timeline.copy(
            sections = sections,
            energyCurve = energyCurve,
            drumEvents = drumEvents,
            melodyEvents = melodyEvents,
            transitionEvents = transitionEvents
        )

        return AutoArrangementPlan(
            sections = sections,
            energyCurve = energyCurve,
            beatAnalysis = beatAnalysis,
            energyAnalysis = energyResult,
            drumEvents = drumEvents,
            melodyEvents = melodyEvents,
            padEvents = padEvents,
            transitionEvents = transitionEvents,
            preset = preset,
            melodySeed = melodySeed,
            updatedTimeline = updatedTimeline
        )
    }
}
