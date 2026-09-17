package com.autoremix.djslow.engine.core

import com.autoremix.djslow.engine.structure.SongSection
import com.autoremix.djslow.engine.structure.SongSectionType
import com.autoremix.djslow.engine.timeline.MasterMusicalClock
import kotlin.math.max

typealias Arrangement = ArrangementEngine.FullArrangement

/**
 * 5. ARRANGEMENT ENGINE
 * Mengatur susunan seksi lagu:
 * INTRO, BUILD, PRE-DROP, DROP, BREAK, BUILD 2, DROP 2, OUTRO.
 * Setiap seksi mengontrol mode dari setiap elemen musik (vocal, drum, bass, chord, melody, pad, fx).
 * Mengadaptasi durasi secara proporsional jika lagu pendek tanpa memotong atau memaksakan loop ganjil.
 */
object ArrangementEngine {

    enum class VocalMode { FULL, CHOP_PRE_DROP, DUCKED, REVERB_TAIL, SILENT }
    enum class DrumMode { SILENT, KICK_ONLY, BUILD_SNARE_ROLL, FULL_GROOVE, HALF_TIME }
    enum class BassMode { OFF, SUB_DRONE, FULL_PUNCH, GLIDE_SIDECHAIN, LIGHT_WALKING }
    enum class ChordMode { OFF, SOFT_WARM, RHYTHMIC_STABS, FULL_SWEEP }
    enum class MelodyMode { OFF, TEASER, FULL_HOOK, ARPEGGIO }
    enum class PadMode { OFF, AIRY_BACKGROUND, WIDE_WARM, DRAMATIC_SWELL }
    enum class FxMode { NONE, RISER_SWEEP, IMPACT_DROP, TENSION_PAUSE, REVERB_OUTRO }

    data class ArrangedSection(
        val sectionType: SongSectionType,
        val startBar: Int,
        val endBar: Int,
        val startSample: Long,
        val endSample: Long,
        val startTimeMs: Long,
        val endTimeMs: Long,
        val energy: Float,
        val vocalMode: VocalMode,
        val drumMode: DrumMode,
        val bassMode: BassMode,
        val chordMode: ChordMode,
        val melodyMode: MelodyMode,
        val padMode: PadMode,
        val fxMode: FxMode
    ) {
        val barCount: Int get() = max(1, endBar - startBar)
        val duration: Long get() = durationMs
        val durationMs: Long get() = endTimeMs - startTimeMs
        val durationSamples: Long get() = max(0L, endSample - startSample)

        fun toSongSection(): SongSection {
            return SongSection(
                sectionType = sectionType,
                startBar = startBar,
                endBar = endBar,
                startSample = startSample,
                endSample = endSample,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                targetEnergy = energy
            )
        }
    }

    data class FullArrangement(
        val totalBars: Int,
        val totalDurationMs: Long,
        val sections: List<ArrangedSection>
    ) {
        fun getSectionForBar(bar: Int): ArrangedSection? {
            return sections.firstOrNull { bar in it.startBar until it.endBar }
        }

        fun getSectionForSample(sample: Long): ArrangedSection? {
            return sections.firstOrNull { sample in it.startSample until it.endSample }
        }
    }

    /**
     * Membangun aransemen lengkap berdasarkan MusicAnalysis, MusicalMap, dan RemixPlan.
     * Mengadaptasi struktur seksi secara proporsional sesuai durasi musik sumber.
     */
    fun createArrangement(
        analysis: MusicUnderstandingEngine.MusicAnalysis,
        musicalMap: MusicalMapEngine.MusicalMap,
        remixPlan: RemixBrain.RemixPlan
    ): FullArrangement {
        return createArrangement(musicalMap, remixPlan)
    }

    /**
     * Membangun aransemen lengkap berdasarkan MusicalMap dan RemixPlan.
     */
    fun createArrangement(
        musicalMap: MusicalMapEngine.MusicalMap,
        remixPlan: RemixBrain.RemixPlan
    ): FullArrangement {
        val totalBars = musicalMap.totalBars
        val sampleRate = musicalMap.sampleRate
        val samplesPerBar = ((sampleRate * 60.0 * 4.0) / remixPlan.targetBpm).toLong()

        val arrangedSections = ArrayList<ArrangedSection>()

        if (totalBars <= 8) {
            // Lagu Sangat Pendek (misal 8-20 detik): INTRO -> PRE-DROP -> DROP -> OUTRO
            var cur = 0
            val bIntro = if (totalBars >= 4) 1 else 0
            val bPreDrop = if (totalBars >= 3) 1 else 0
            val bOutro = if (totalBars >= 5) 1 else 0
            val bDrop = maxOf(1, totalBars - bIntro - bPreDrop - bOutro)

            if (bIntro > 0) {
                addSection(arrangedSections, SongSectionType.INTRO, cur, cur + bIntro, samplesPerBar, remixPlan, 0.25f,
                    VocalMode.FULL, DrumMode.SILENT, BassMode.SUB_DRONE, ChordMode.SOFT_WARM, MelodyMode.OFF, PadMode.AIRY_BACKGROUND, FxMode.NONE)
                cur += bIntro
            }

            if (bPreDrop > 0 && cur < totalBars) {
                addSection(arrangedSections, SongSectionType.PRE_DROP, cur, cur + bPreDrop, samplesPerBar, remixPlan, 0.65f,
                    VocalMode.CHOP_PRE_DROP, DrumMode.BUILD_SNARE_ROLL, BassMode.OFF, ChordMode.RHYTHMIC_STABS, MelodyMode.TEASER, PadMode.DRAMATIC_SWELL, FxMode.RISER_SWEEP)
                cur += bPreDrop
            }

            val eDrop = if (bOutro > 0 && cur + bDrop < totalBars) cur + bDrop else totalBars
            if (cur < eDrop) {
                addSection(arrangedSections, SongSectionType.DROP, cur, eDrop, samplesPerBar, remixPlan, 0.95f,
                    VocalMode.FULL, DrumMode.FULL_GROOVE, BassMode.FULL_PUNCH, ChordMode.FULL_SWEEP, MelodyMode.FULL_HOOK, PadMode.WIDE_WARM, FxMode.IMPACT_DROP)
                cur = eDrop
            }

            if (cur < totalBars) {
                addSection(arrangedSections, SongSectionType.OUTRO, cur, totalBars, samplesPerBar, remixPlan, 0.30f,
                    VocalMode.REVERB_TAIL, DrumMode.KICK_ONLY, BassMode.SUB_DRONE, ChordMode.SOFT_WARM, MelodyMode.OFF, PadMode.AIRY_BACKGROUND, FxMode.REVERB_OUTRO)
            }

        } else if (totalBars <= 16) {
            // Lagu Pendek (misal 20-40 detik): INTRO (2 bar) -> PRE-DROP (2 bar) -> DROP (6-8 bar) -> OUTRO (2 bar)
            var cur = 0
            val bIntro = maxOf(1, totalBars / 5)
            val bPreDrop = 2
            val bOutro = maxOf(1, totalBars / 6)
            val bDrop = maxOf(2, totalBars - bIntro - bPreDrop - bOutro)

            addSection(arrangedSections, SongSectionType.INTRO, cur, cur + bIntro, samplesPerBar, remixPlan, 0.25f,
                VocalMode.FULL, DrumMode.SILENT, BassMode.SUB_DRONE, ChordMode.SOFT_WARM, MelodyMode.OFF, PadMode.AIRY_BACKGROUND, FxMode.NONE)
            cur += bIntro

            addSection(arrangedSections, SongSectionType.PRE_DROP, cur, cur + bPreDrop, samplesPerBar, remixPlan, 0.65f,
                VocalMode.CHOP_PRE_DROP, DrumMode.BUILD_SNARE_ROLL, BassMode.OFF, ChordMode.RHYTHMIC_STABS, MelodyMode.TEASER, PadMode.DRAMATIC_SWELL, FxMode.RISER_SWEEP)
            cur += bPreDrop

            val eDrop = cur + bDrop
            addSection(arrangedSections, SongSectionType.DROP, cur, eDrop, samplesPerBar, remixPlan, 0.95f,
                VocalMode.FULL, DrumMode.FULL_GROOVE, BassMode.FULL_PUNCH, ChordMode.FULL_SWEEP, MelodyMode.FULL_HOOK, PadMode.WIDE_WARM, FxMode.IMPACT_DROP)
            cur = eDrop

            if (cur < totalBars) {
                addSection(arrangedSections, SongSectionType.OUTRO, cur, totalBars, samplesPerBar, remixPlan, 0.30f,
                    VocalMode.REVERB_TAIL, DrumMode.KICK_ONLY, BassMode.SUB_DRONE, ChordMode.SOFT_WARM, MelodyMode.OFF, PadMode.AIRY_BACKGROUND, FxMode.REVERB_OUTRO)
            }

        } else if (totalBars <= 24) {
            // Lagu Sedang (misal 45-60 detik): INTRO -> BUILD -> PRE-DROP -> DROP -> BREAK -> OUTRO
            var cur = 0
            val introBars = minOf(4, totalBars / 6)
            addSection(arrangedSections, SongSectionType.INTRO, cur, cur + introBars, samplesPerBar, remixPlan, 0.25f,
                VocalMode.FULL, DrumMode.SILENT, BassMode.SUB_DRONE, ChordMode.SOFT_WARM, MelodyMode.OFF, PadMode.AIRY_BACKGROUND, FxMode.NONE)
            cur += introBars

            val buildBars = 4
            addSection(arrangedSections, SongSectionType.BUILD_UP, cur, cur + buildBars, samplesPerBar, remixPlan, 0.50f,
                VocalMode.FULL, DrumMode.KICK_ONLY, BassMode.LIGHT_WALKING, ChordMode.RHYTHMIC_STABS, MelodyMode.TEASER, PadMode.WIDE_WARM, FxMode.RISER_SWEEP)
            cur += buildBars

            val preDropBars = 2
            addSection(arrangedSections, SongSectionType.PRE_DROP, cur, cur + preDropBars, samplesPerBar, remixPlan, 0.70f,
                VocalMode.CHOP_PRE_DROP, DrumMode.BUILD_SNARE_ROLL, BassMode.OFF, ChordMode.RHYTHMIC_STABS, MelodyMode.OFF, PadMode.DRAMATIC_SWELL, FxMode.TENSION_PAUSE)
            cur += preDropBars

            val dropBars = minOf(8, totalBars - cur - 4)
            addSection(arrangedSections, SongSectionType.DROP, cur, cur + dropBars, samplesPerBar, remixPlan, 0.95f,
                VocalMode.FULL, DrumMode.FULL_GROOVE, BassMode.FULL_PUNCH, ChordMode.FULL_SWEEP, MelodyMode.FULL_HOOK, PadMode.WIDE_WARM, FxMode.IMPACT_DROP)
            cur += dropBars

            val outroBars = totalBars - cur
            addSection(arrangedSections, SongSectionType.OUTRO, cur, totalBars, samplesPerBar, remixPlan, 0.30f,
                VocalMode.REVERB_TAIL, DrumMode.KICK_ONLY, BassMode.SUB_DRONE, ChordMode.SOFT_WARM, MelodyMode.OFF, PadMode.AIRY_BACKGROUND, FxMode.REVERB_OUTRO)

        } else {
            // Lagu Standar / Panjang (Struktur DJ Slow Lengkap 8 Seksi):
            // INTRO -> BUILD -> PRE-DROP -> DROP -> BREAK -> BUILD 2 -> DROP 2 -> OUTRO
            val bIntro = max(4, (totalBars * 0.10f).toInt())
            val bBuild = max(4, (totalBars * 0.12f).toInt())
            val bPreDrop = 2
            val bDrop = max(8, (totalBars * 0.25f).toInt())
            val bBreak = max(4, (totalBars * 0.12f).toInt())
            val bBuild2 = max(4, (totalBars * 0.12f).toInt())
            val bDrop2 = max(8, (totalBars * 0.20f).toInt())

            var cur = 0
            // 1. INTRO
            val eIntro = minOf(cur + bIntro, totalBars)
            addSection(arrangedSections, SongSectionType.INTRO, cur, eIntro, samplesPerBar, remixPlan, 0.25f,
                VocalMode.FULL, DrumMode.SILENT, BassMode.SUB_DRONE, ChordMode.SOFT_WARM, MelodyMode.OFF, PadMode.AIRY_BACKGROUND, FxMode.NONE)
            cur = eIntro

            // 2. BUILD
            val eBuild = minOf(cur + bBuild, totalBars)
            addSection(arrangedSections, SongSectionType.BUILD_UP, cur, eBuild, samplesPerBar, remixPlan, 0.50f,
                VocalMode.FULL, DrumMode.KICK_ONLY, BassMode.LIGHT_WALKING, ChordMode.RHYTHMIC_STABS, MelodyMode.TEASER, PadMode.WIDE_WARM, FxMode.RISER_SWEEP)
            cur = eBuild

            // 3. PRE-DROP
            val ePreDrop = minOf(cur + bPreDrop, totalBars)
            addSection(arrangedSections, SongSectionType.PRE_DROP, cur, ePreDrop, samplesPerBar, remixPlan, 0.70f,
                VocalMode.CHOP_PRE_DROP, DrumMode.BUILD_SNARE_ROLL, BassMode.OFF, ChordMode.RHYTHMIC_STABS, MelodyMode.OFF, PadMode.DRAMATIC_SWELL, FxMode.TENSION_PAUSE)
            cur = ePreDrop

            // 4. DROP
            val eDrop = minOf(cur + bDrop, totalBars)
            addSection(arrangedSections, SongSectionType.DROP, cur, eDrop, samplesPerBar, remixPlan, 0.95f,
                VocalMode.FULL, DrumMode.FULL_GROOVE, BassMode.FULL_PUNCH, ChordMode.FULL_SWEEP, MelodyMode.FULL_HOOK, PadMode.WIDE_WARM, FxMode.IMPACT_DROP)
            cur = eDrop

            // 5. BREAK
            if (cur < totalBars) {
                val eBreak = minOf(cur + bBreak, totalBars)
                addSection(arrangedSections, SongSectionType.BREAK, cur, eBreak, samplesPerBar, remixPlan, 0.40f,
                    VocalMode.FULL, DrumMode.SILENT, BassMode.SUB_DRONE, ChordMode.SOFT_WARM, MelodyMode.OFF, PadMode.AIRY_BACKGROUND, FxMode.NONE)
                cur = eBreak
            }

            // 6. BUILD 2
            if (cur < totalBars) {
                val eBuild2 = minOf(cur + bBuild2, totalBars)
                addSection(arrangedSections, SongSectionType.BUILD_UP_2, cur, eBuild2, samplesPerBar, remixPlan, 0.65f,
                    VocalMode.FULL, DrumMode.BUILD_SNARE_ROLL, BassMode.LIGHT_WALKING, ChordMode.RHYTHMIC_STABS, MelodyMode.TEASER, PadMode.WIDE_WARM, FxMode.RISER_SWEEP)
                cur = eBuild2
            }

            // 7. DROP 2 (MAIN DROP)
            if (cur < totalBars) {
                val eDrop2 = minOf(cur + bDrop2, totalBars - 2)
                addSection(arrangedSections, SongSectionType.MAIN_DROP, cur, eDrop2, samplesPerBar, remixPlan, 1.0f,
                    VocalMode.FULL, DrumMode.FULL_GROOVE, BassMode.GLIDE_SIDECHAIN, ChordMode.FULL_SWEEP, MelodyMode.FULL_HOOK, PadMode.WIDE_WARM, FxMode.IMPACT_DROP)
                cur = eDrop2
            }

            // 8. OUTRO
            if (cur < totalBars) {
                addSection(arrangedSections, SongSectionType.OUTRO, cur, totalBars, samplesPerBar, remixPlan, 0.25f,
                    VocalMode.REVERB_TAIL, DrumMode.KICK_ONLY, BassMode.SUB_DRONE, ChordMode.SOFT_WARM, MelodyMode.OFF, PadMode.AIRY_BACKGROUND, FxMode.REVERB_OUTRO)
            }
        }

        return FullArrangement(
            totalBars = totalBars,
            totalDurationMs = musicalMap.totalDurationMs,
            sections = arrangedSections
        )
    }

    private fun addSection(
        list: MutableList<ArrangedSection>,
        type: SongSectionType,
        startBar: Int,
        endBar: Int,
        samplesPerBar: Long,
        remixPlan: RemixBrain.RemixPlan,
        baseEnergy: Float,
        vocalMode: VocalMode,
        drumMode: DrumMode,
        bassMode: BassMode,
        chordMode: ChordMode,
        melodyMode: MelodyMode,
        padMode: PadMode,
        fxMode: FxMode
    ) {
        if (endBar <= startBar) return
        val clock = MasterMusicalClock(remixPlan.targetBpm, 44100)
        val startSample = clock.getDownbeatSampleForBar(startBar)
        val endSample = clock.getDownbeatSampleForBar(endBar)
        val startTimeMs = clock.beatToMs(startBar * 4f)
        val endTimeMs = clock.beatToMs(endBar * 4f)
        val energy = (baseEnergy * (remixPlan.energyPreference.targetEnergy / 0.60f)).coerceIn(0.1f, 1.0f)

        list.add(
            ArrangedSection(
                sectionType = type,
                startBar = startBar,
                endBar = endBar,
                startSample = startSample,
                endSample = endSample,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs,
                energy = energy,
                vocalMode = vocalMode,
                drumMode = drumMode,
                bassMode = bassMode,
                chordMode = chordMode,
                melodyMode = melodyMode,
                padMode = padMode,
                fxMode = fxMode
            )
        )
    }
}
