package com.autoremix.djslow.engine.core

import com.autoremix.djslow.engine.music.Chord
import com.autoremix.djslow.engine.music.MusicKey
import com.autoremix.djslow.engine.structure.SongSection
import com.autoremix.djslow.engine.structure.SongSectionType
import com.autoremix.djslow.engine.timeline.BeatGridPoint
import com.autoremix.djslow.engine.timeline.MasterTimeline

/**
 * 3. MUSICAL MAP ENGINE (FASE 2 - Bagian B & F)
 *
 * Membangun peta kontekstual musik per Bar & Beat:
 * Setiap bar menyimpan konteks musik lengkap:
 * Bar
 * ├── section
 * ├── energy (level + kategori: CALM, LOW, MEDIUM, HIGH, DROP)
 * ├── chord
 * ├── beat (grid points, downbeat sample & time)
 * ├── vocal activity (hasVocalPhrase, vocalActivityRatio)
 * └── musical context (deskripsi naratif musikal per bar)
 *
 * Bukan hanya sekadar daftar timestamp.
 */
object MusicalMapEngine {

    /**
     * Kategori energi standar (FASE 2 - Bagian F).
     * 0.0–0.3  = CALM
     * 0.3–0.5  = LOW
     * 0.5–0.7  = MEDIUM
     * 0.7–0.85 = HIGH
     * 0.85–1.0 = DROP
     */
    enum class EnergyCategory(
        val min: Float,
        val max: Float,
        val label: String,
        val description: String
    ) {
        CALM(0.0f, 0.30f, "CALM", "Nuansa tenang, dinamika rendah, instrumen minimalis"),
        LOW(0.30f, 0.50f, "LOW", "Santai, ketukan lembut, vokal lebih leluasa"),
        MEDIUM(0.50f, 0.70f, "MEDIUM", "Energi seimbang, ritme reguler DJ Slow standar"),
        HIGH(0.70f, 0.85f, "HIGH", "Bertenaga tinggi, build-up agresif, instrumen padat"),
        DROP(0.85f, 1.0f, "DROP", "Klimaks penuh, dentuman kick-bass maksimal, intensitas tertinggi");

        companion object {
            fun fromLevel(level: Float): EnergyCategory {
                val clamped = level.coerceIn(0.0f, 1.0f)
                return when {
                    clamped < 0.30f -> CALM
                    clamped < 0.50f -> LOW
                    clamped < 0.70f -> MEDIUM
                    clamped < 0.85f -> HIGH
                    else -> DROP
                }
            }
        }
    }

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
        val beatGridIndices: List<Int>,
        val downbeatTimeMs: Long = if (startSample > 0) (downbeatSample * 1000L) / 44100L else 0L,
        val energyCategory: EnergyCategory = EnergyCategory.fromLevel(energyLevel),
        val vocalActivityRatio: Float = if (hasVocalPhrase) 1.0f else 0.0f,
        val sectionName: String = sectionType.label,
        val musicalContext: String = ""
    ) {
        val durationMs: Long get() = endTimeMs - startTimeMs
        val durationSamples: Long get() = endSample - startSample
        val totalBeats: Int get() = beatGridIndices.size
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

        fun getBarAtMs(timeMs: Long): BarContext? {
            return barContexts.firstOrNull { timeMs in it.startTimeMs until it.endTimeMs }
        }

        fun getBarAtIndex(index: Int): BarContext? {
            return barContexts.getOrNull(index)
        }

        fun getSectionForBar(barIndex: Int): SongSection? {
            return sections.firstOrNull { barIndex in it.startBar until it.endBar }
        }

        fun getAverageEnergy(): Float {
            if (barContexts.isEmpty()) return 0.5f
            return barContexts.map { it.energyLevel }.average().toFloat()
        }

        fun getVocalBars(): List<BarContext> {
            return barContexts.filter { it.hasVocalPhrase }
        }

        fun getTransitionBars(): List<BarContext> {
            return barContexts.filter { it.isTransitionBar }
        }

        fun getUniqueChords(): List<Chord> {
            return barContexts.map { it.chord }.distinct()
        }

        fun formatSummary(): String {
            val sectionSummary = sections.joinToString(", ") {
                "${it.sectionType.label} (Bar ${it.startBar + 1}-${it.endBar})"
            }
            return "Total Bar: $totalBars bar ($totalBeats beat @ ${bpm.toInt()} BPM) | Kunci: ${key.displayName} | $sectionSummary"
        }
    }

    /**
     * Membangun MusicalMap langsung dari MusicAnalysis (FASE 2 - Bagian B).
     */
    fun buildMap(analysis: MusicUnderstandingEngine.MusicAnalysis): MusicalMap {
        return buildMap(analysis, analysis.timeline)
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
            val downbeatTimeMs = startTimeMs

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
            val curveEnergy = (timeline.energyCurve?.getEnergyAtBar(bar) ?: baseEnergy).coerceIn(0.0f, 1.0f)
            val energyCategory = EnergyCategory.fromLevel(curveEnergy)

            // Periksa aktivitas frasa vokal nyata di rentang bar ini
            val barDurationSamples = maxOf(1L, endSample - startSample)
            val vocalOverlapSamples = analysis.vocalPhrases
                .filter { it.startSample < endSample && it.endSample > startSample }
                .sumOf { phrase ->
                    val overlapStart = maxOf(phrase.startSample, startSample)
                    val overlapEnd = minOf(phrase.endSample, endSample)
                    maxOf(0L, overlapEnd - overlapStart)
                }
            val vocalActivityRatio = (vocalOverlapSamples.toFloat() / barDurationSamples.toFloat()).coerceIn(0.0f, 1.0f)
            val hasVocal = vocalActivityRatio > 0.08f || analysis.vocalPhrases.any {
                it.startSample < endSample && it.endSample > startSample
            }

            // Apakah bar ini adalah bar transisi (bar terakhir dari sebuah seksi)
            val isTransition = section != null && bar == (section.endBar - 1)

            val beatIndices = (startBeat until endBeat).toList()

            // Bangun konteks musikal naratif yang deskriptif untuk bar ini
            val musicalContext = buildString {
                append("Bar ${bar + 1}/$totalBars [${sectionType.label}] ")
                append("• Akor ${chord.name} ")
                append("• Energi ${(curveEnergy * 100).toInt()}% (${energyCategory.label}) ")
                if (hasVocal) {
                    append("• Vokal Aktif (${(vocalActivityRatio * 100).toInt()}%) ")
                } else {
                    append("• Instrumen Murni ")
                }
                if (isTransition) {
                    append("• [TRANSISI SEKSI]")
                } else {
                    append("• [GROOVE]")
                }
            }

            barContexts.add(
                BarContext(
                    barIndex = bar,
                    startSample = startSample,
                    endSample = endSample,
                    startTimeMs = startTimeMs,
                    endTimeMs = endTimeMs,
                    downbeatSample = downbeatSample,
                    chord = chord,
                    energyLevel = curveEnergy,
                    sectionType = sectionType,
                    hasVocalPhrase = hasVocal,
                    isTransitionBar = isTransition,
                    beatGridIndices = beatIndices,
                    downbeatTimeMs = downbeatTimeMs,
                    energyCategory = energyCategory,
                    vocalActivityRatio = vocalActivityRatio,
                    sectionName = sectionType.label,
                    musicalContext = musicalContext
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
