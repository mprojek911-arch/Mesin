package com.autoremix.djslow.engine.core

import com.autoremix.djslow.engine.music.Chord
import com.autoremix.djslow.engine.music.MusicKey
import com.autoremix.djslow.engine.structure.SongSection
import com.autoremix.djslow.engine.structure.SongSectionType
import com.autoremix.djslow.engine.timeline.BeatGridPoint
import com.autoremix.djslow.engine.timeline.MasterTimeline

/**
 * 3. MUSICAL MAP ENGINE
 * Membangun peta kontekstual musik per Bar & Beat:
 * Setiap bar menyimpan konteks musik lengkap (Chord, Downbeat, Section, Energy, Vocal Phrase, dsb).
 */
object MusicalMapEngine {

    /**
     * Konteks musik lengkap untuk sebuah Bar (4 ketukan).
     */
    data class BarContext(
        val barIndex: Int,
        val startSample: Long,
        val endSample: Long,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val downbeatSample: Long,
        val chord: Chord,
        val energyLevel: Float, // 0.0 .. 1.0
        val sectionType: SongSectionType,
        val hasVocalPhrase: Boolean,
        val isTransitionBar: Boolean,
        val beatGridIndices: List<Int>
    ) {
        val durationMs: Long get() = endTimeMs - startTimeMs
        val durationSamples: Long get() = endSample - startSample
    }

    /**
     * Peta musikal lengkap yang memandu Remix Brain dan Aransemen.
     */
    data class MusicalMap(
        val bpm: Float,
        val key: MusicKey,
        val beatsPerBar: Int = 4,
        val sampleRate: Int = 44100,
        val totalBars: Int,
        val totalBeats: Int,
        val totalDurationMs: Long,
        val barContexts: List<BarContext>,
        val beatGrid: List<BeatGridPoint>,
        val sections: List<SongSection>
    ) {
        fun getBarAtSample(sample: Long): BarContext? {
            return barContexts.firstOrNull { sample in it.startSample until it.endSample }
        }

        fun getBarAtIndex(index: Int): BarContext? {
            return barContexts.getOrNull(index)
        }

        fun getSectionForBar(barIndex: Int): SongSection? {
            return sections.firstOrNull { barIndex in it.startBar until it.endBar }
        }

        fun formatSummary(): String {
            val sectionSummary = sections.joinToString(", ") { "${it.sectionType.label} (Bar ${it.startBar + 1}-${it.endBar})" }
            return "Total Bar: $totalBars bar ($totalBeats beat) | $sectionSummary"
        }
    }

    /**
     * Membangun MusicalMap dari hasil MusicAnalysis dan MasterTimeline.
     */
    fun buildMap(
        analysis: MusicUnderstandingEngine.MusicAnalysis,
        timeline: MasterTimeline
    ): MusicalMap {
        val beatsPerBar = timeline.beatsPerBar
        val sampleRate = timeline.sampleRate
        val totalBars = timeline.totalBars
        val totalBeats = timeline.totalBeats

        val barContexts = ArrayList<BarContext>(totalBars)

        for (bar in 0 until totalBars) {
            val startBeat = bar * beatsPerBar
            val endBeat = minOf(startBeat + beatsPerBar, totalBeats)

            val startSample = timeline.beatToSample(startBeat.toFloat())
            val endSample = timeline.beatToSample(endBeat.toFloat())
            val startTimeMs = timeline.beatToMs(startBeat.toFloat())
            val endTimeMs = timeline.beatToMs(endBeat.toFloat())
            val downbeatSample = startSample

            // Akor aktif pada bar ini
            val chord = timeline.getChordAtSample(startSample)
                ?: analysis.chords.getOrElse(bar % maxOf(1, analysis.chords.size)) {
                    analysis.chords.firstOrNull() ?: Chord(analysis.key.tonic, com.autoremix.djslow.engine.music.ChordType.MINOR)
                }

            // Seksi lagu aktif pada bar ini
            val section = analysis.sections.firstOrNull { bar in it.startBar until it.endBar }
            val sectionType = section?.sectionType ?: determineDefaultSectionType(bar, totalBars)

            // Tingkat energi bar berdasarkan seksi dan analisis energi
            val baseEnergy = section?.targetEnergy ?: (sectionType.defaultEnergyPercent / 100.0f)
            val curveEnergy = timeline.energyCurve?.getEnergyAtBar(bar) ?: baseEnergy

            // Periksa apakah ada frasa vokal nyata di rentang bar ini
            val hasVocal = analysis.vocalPhrases.any { phrase ->
                phrase.startSample < endSample && phrase.endSample > startSample
            }

            // Apakah bar ini adalah bar transisi (bar terakhir dari sebuah seksi)
            val isTransition = section != null && bar == (section.endBar - 1)

            val beatIndices = (startBeat until endBeat).toList()

            barContexts.add(
                BarContext(
                    barIndex = bar,
                    startSample = startSample,
                    endSample = endSample,
                    startTimeMs = startTimeMs,
                    endTimeMs = endTimeMs,
                    downbeatSample = downbeatSample,
                    chord = chord,
                    energyLevel = curveEnergy.coerceIn(0.0f, 1.0f),
                    sectionType = sectionType,
                    hasVocalPhrase = hasVocal,
                    isTransitionBar = isTransition,
                    beatGridIndices = beatIndices
                )
            )
        }

        return MusicalMap(
            bpm = timeline.bpm,
            key = analysis.key,
            beatsPerBar = beatsPerBar,
            sampleRate = sampleRate,
            totalBars = totalBars,
            totalBeats = totalBeats,
            totalDurationMs = timeline.totalDurationMs,
            barContexts = barContexts,
            beatGrid = timeline.beatGrid,
            sections = analysis.sections
        )
    }

    private fun determineDefaultSectionType(bar: Int, totalBars: Int): SongSectionType {
        if (totalBars <= 0) return SongSectionType.GROOVE
        val frac = bar.toFloat() / totalBars.toFloat()
        return when {
            frac < 0.10f -> SongSectionType.INTRO
            frac < 0.25f -> SongSectionType.BUILD_UP
            frac < 0.30f -> SongSectionType.PRE_DROP
            frac < 0.60f -> SongSectionType.DROP
            frac < 0.75f -> SongSectionType.BREAK
            frac < 0.90f -> SongSectionType.MAIN_DROP
            else -> SongSectionType.OUTRO
        }
    }
}
