package com.autoremix.djslow.engine.core

import com.autoremix.djslow.engine.arrangement.AutoDjPreset
import com.autoremix.djslow.engine.core.MusicalMapEngine.EnergyCategory
import com.autoremix.djslow.engine.mastering.MasteringPreset
import com.autoremix.djslow.engine.music.Chord
import com.autoremix.djslow.engine.music.MusicKey
import com.autoremix.djslow.engine.structure.SongSection
import com.autoremix.djslow.engine.structure.SongSectionType
import com.autoremix.djslow.engine.synth.BassPatternType
import com.autoremix.djslow.engine.synth.ChordSynthPreset
import kotlin.random.Random

/**
 * 4. REMIX BRAIN & STYLE SYSTEM (FASE 2 - Bagian D, E, F, G)
 *
 * Otak pusat pengambil keputusan musikal berbasis analisis nyata:
 * Input:
 * - MusicAnalysis (hasil DSP Fase 1)
 * - MusicalMap (peta per bar & beat)
 * - UserStyle (6 Remix Style spesifik)
 * - TargetBpm
 * - EnergyPreference (CALM, LOW, MEDIUM, HIGH, DROP)
 * - FocusPreference (Vokal, Beat, Harmoni, Seimbang)
 * - Seed (deterministik, variasi terkontrol)
 *
 * Output:
 * - RemixPlan lengkap dengan 12 sub-rencana nyata.
 */
object RemixBrain {

    /**
     * Keseimbangan level mix antar elemen trek (FASE 2 - Bagian E).
     */
    data class MixBalance(
        val vocalWeight: Float,
        val beatWeight: Float,
        val drumWeight: Float,
        val bassWeight: Float,
        val chordWeight: Float,
        val melodyWeight: Float,
        val padWeight: Float,
        val fxWeight: Float
    )

    /**
     * 6 Style Profile DJ Slow spesifik sesuai mandat (FASE 2 - Bagian E):
     * DJ SLOW, DJ SLOW BASS, DJ SLOW ROMANTIS, DJ SLOW DARK, DJ SLOW DEEP, DJ SLOW PARTY.
     * Masing-masing memiliki parameter nyata (BPM range, drum style, bass style, chord style,
     * melody style, energy curve, arrangement behavior, FX density, mix balance, master target).
     */
    enum class RemixStyle(
        val label: String,
        val description: String,
        val defaultBpm: Float,
        val minBpm: Float,
        val maxBpm: Float,
        val drumStyle: String,
        val bassStyle: String,
        val chordStyle: String,
        val melodyStyle: String,
        val energyCurveBehavior: String,
        val arrangementBehavior: String,
        val drumDensityMultiplier: Float,
        val bassBoostMultiplier: Float,
        val chordPreset: ChordSynthPreset,
        val bassPatternType: BassPatternType,
        val fxDensity: Float,
        val mixBalance: MixBalance,
        val masterTargetLufs: Float,
        val masteringPreset: MasteringPreset,
        val legacyPreset: AutoDjPreset
    ) {
        DJ_SLOW(
            label = "DJ SLOW",
            description = "Gaya remix santai, tempo 80 BPM, bass empuk & ketukan berbobot",
            defaultBpm = 80.0f,
            minBpm = 75.0f,
            maxBpm = 85.0f,
            drumStyle = "DJ Slow Half-Time Swing 1/8th",
            bassStyle = "Warm Sub 808 DJ Slow Pumping",
            chordStyle = "Acoustic Piano & Rhodes Chops",
            melodyStyle = "Vocal-Response Minimal Hook",
            energyCurveBehavior = "Moderate gradual build, steady relaxed drop",
            arrangementBehavior = "Classic 8-section DJ Slow arrangement with spacious vocal breaks",
            drumDensityMultiplier = 0.85f,
            bassBoostMultiplier = 1.0f,
            chordPreset = ChordSynthPreset.PIANO,
            bassPatternType = BassPatternType.DJ_SLOW_BASS,
            fxDensity = 0.70f,
            mixBalance = MixBalance(
                vocalWeight = 1.0f,
                beatWeight = 0.80f,
                drumWeight = 0.90f,
                bassWeight = 0.85f,
                chordWeight = 0.70f,
                melodyWeight = 0.65f,
                padWeight = 0.60f,
                fxWeight = 0.70f
            ),
            masterTargetLufs = -10.0f,
            masteringPreset = MasteringPreset.DJ_SLOW,
            legacyPreset = AutoDjPreset.DJ_SLOW
        ),
        DJ_SLOW_BASS(
            label = "DJ SLOW BASS",
            description = "Sub-bass bertenaga tinggi, sidechain mendalam untuk jedag-jedug santai",
            defaultBpm = 78.0f,
            minBpm = 72.0f,
            maxBpm = 84.0f,
            drumStyle = "Punchy Sub Kick Heavy Snare",
            bassStyle = "Deep Saturated Sub-Bass 40-70Hz with Heavy Sidechain",
            chordStyle = "Detuned Saw Plucks with Low-Pass Filter",
            melodyStyle = "Dark Staccato Accent Plucks",
            energyCurveBehavior = "Deep sub swells leading to explosive low-end drops",
            arrangementBehavior = "Extended drops with massive kick-bass focus and aggressive build rolls",
            drumDensityMultiplier = 0.95f,
            bassBoostMultiplier = 1.45f,
            chordPreset = ChordSynthPreset.SOFT_SYNTH,
            bassPatternType = BassPatternType.DJ_SLOW_BASS,
            fxDensity = 0.85f,
            mixBalance = MixBalance(
                vocalWeight = 0.95f,
                beatWeight = 0.95f,
                drumWeight = 1.00f,
                bassWeight = 1.25f,
                chordWeight = 0.60f,
                melodyWeight = 0.60f,
                padWeight = 0.50f,
                fxWeight = 0.80f
            ),
            masterTargetLufs = -8.5f,
            masteringPreset = MasteringPreset.BASS_STRONG,
            legacyPreset = AutoDjPreset.DJ_SLOW_BASS
        ),
        DJ_SLOW_ROMANTIS(
            label = "DJ SLOW ROMANTIS",
            description = "Melodi manis, piano lembut, progresi akor emosional menyentuh hati",
            defaultBpm = 76.0f,
            minBpm = 70.0f,
            maxBpm = 82.0f,
            drumStyle = "Gentle Rimshot & Soft Acoustic Hi-Hat 1/4th",
            bassStyle = "Smooth Acoustic Warm Sine Bass",
            chordStyle = "Expressive Grand Piano & Warm Strings",
            melodyStyle = "Emotional Legato Piano Melodies",
            energyCurveBehavior = "Gentle dynamic curves prioritizing emotional vocal climaxes",
            arrangementBehavior = "Melodic intros and extended breakdown piano solos",
            drumDensityMultiplier = 0.65f,
            bassBoostMultiplier = 0.85f,
            chordPreset = ChordSynthPreset.SOFT_PIANO,
            bassPatternType = BassPatternType.ROOT,
            fxDensity = 0.50f,
            mixBalance = MixBalance(
                vocalWeight = 1.15f,
                beatWeight = 0.65f,
                drumWeight = 0.70f,
                bassWeight = 0.75f,
                chordWeight = 0.85f,
                melodyWeight = 0.85f,
                padWeight = 0.80f,
                fxWeight = 0.50f
            ),
            masterTargetLufs = -12.0f,
            masteringPreset = MasteringPreset.NATURAL,
            legacyPreset = AutoDjPreset.DJ_EMOTIONAL
        ),
        DJ_SLOW_DARK(
            label = "DJ SLOW DARK",
            description = "Suasana misterius, synth bass garang, drop menegangkan dan pad gelap",
            defaultBpm = 82.0f,
            minBpm = 76.0f,
            maxBpm = 88.0f,
            drumStyle = "Tight Trap-Inspired Hat Stutters & Snappy Claps",
            bassStyle = "Gritty Reese Bass & Syncopated Sub Stabs",
            chordStyle = "Minor Suspended Dark Pads & Reverse FX",
            melodyStyle = "Mysterious Minor Arpeggios",
            energyCurveBehavior = "High tension builds with prolonged pre-drop suspensions",
            arrangementBehavior = "Tension-filled arrangement with abrupt drop impacts",
            drumDensityMultiplier = 1.10f,
            bassBoostMultiplier = 1.25f,
            chordPreset = ChordSynthPreset.SOFT_SYNTH,
            bassPatternType = BassPatternType.SYNCOPATED,
            fxDensity = 1.0f,
            mixBalance = MixBalance(
                vocalWeight = 1.0f,
                beatWeight = 0.90f,
                drumWeight = 0.95f,
                bassWeight = 1.10f,
                chordWeight = 0.70f,
                melodyWeight = 0.75f,
                padWeight = 0.75f,
                fxWeight = 0.95f
            ),
            masterTargetLufs = -9.0f,
            masteringPreset = MasteringPreset.VOCAL_CLEAR,
            legacyPreset = AutoDjPreset.DJ_FULL_POWER
        ),
        DJ_SLOW_DEEP(
            label = "DJ SLOW DEEP",
            description = "Nuansa deep slow house, rolling sub-bass empuk, reverb luas atmosferik",
            defaultBpm = 84.0f,
            minBpm = 78.0f,
            maxBpm = 90.0f,
            drumStyle = "Hypnotic 4-on-the-Floor Deep Shaker Groove",
            bassStyle = "Rolling Deep Organ Sub-Bass",
            chordStyle = "Muted Electric Piano Chord Stabs",
            melodyStyle = "Minimalist Atmospheric Lead Echoes",
            energyCurveBehavior = "Smooth hypnotic rolling energy without jarring jumps",
            arrangementBehavior = "Subtle continuous groove transitions with evolving filters",
            drumDensityMultiplier = 0.90f,
            bassBoostMultiplier = 1.20f,
            chordPreset = ChordSynthPreset.PIANO,
            bassPatternType = BassPatternType.DJ_SLOW_BASS,
            fxDensity = 0.75f,
            mixBalance = MixBalance(
                vocalWeight = 0.95f,
                beatWeight = 0.85f,
                drumWeight = 0.90f,
                bassWeight = 1.05f,
                chordWeight = 0.75f,
                melodyWeight = 0.70f,
                padWeight = 0.85f,
                fxWeight = 0.75f
            ),
            masterTargetLufs = -10.5f,
            masteringPreset = MasteringPreset.NATURAL,
            legacyPreset = AutoDjPreset.DJ_SLOW
        ),
        DJ_SLOW_PARTY(
            label = "DJ SLOW PARTY",
            description = "Tempo enerjik, snare roll cepat, riser membahana, drop klimaks penuh",
            defaultBpm = 90.0f,
            minBpm = 84.0f,
            maxBpm = 100.0f,
            drumStyle = "Aggressive 1/16th Rolling Dance Drums with Fast Snare Builds",
            bassStyle = "Driving Punchy Dance Bass with Active Slides",
            chordStyle = "Bright High-Energy Saw Chords",
            melodyStyle = "Anthem Lead Hooks with Double Octave Layering",
            energyCurveBehavior = "Steep energy ramps leading to maximum intensity drop peaks",
            arrangementBehavior = "High-energy festival-style arrangement with dual massive drops",
            drumDensityMultiplier = 1.30f,
            bassBoostMultiplier = 1.20f,
            chordPreset = ChordSynthPreset.PAD,
            bassPatternType = BassPatternType.SYNCOPATED,
            fxDensity = 1.20f,
            mixBalance = MixBalance(
                vocalWeight = 1.0f,
                beatWeight = 1.0f,
                drumWeight = 1.15f,
                bassWeight = 1.15f,
                chordWeight = 0.85f,
                melodyWeight = 0.90f,
                padWeight = 0.70f,
                fxWeight = 1.10f
            ),
            masterTargetLufs = -8.0f,
            masteringPreset = MasteringPreset.LOUD_CLEAN,
            legacyPreset = AutoDjPreset.DJ_PARTY
        );

        val recommendedBpmRange: ClosedFloatingPointRange<Float>
            get() = minBpm..maxBpm
    }

    /**
     * Preferensi Energi Remix (FASE 2 - Bagian F):
     * 0.0–0.3  = CALM
     * 0.3–0.5  = LOW
     * 0.5–0.7  = MEDIUM
     * 0.7–0.85 = HIGH
     * 0.85–1.0 = DROP
     */
    enum class EnergyPreference(
        val label: String,
        val minLevel: Float,
        val maxLevel: Float,
        val targetEnergy: Float,
        val description: String,
        val category: EnergyCategory
    ) {
        CALM("Calm (0.0-0.3)", 0.0f, 0.30f, 0.25f, "Nuansa akustik/pad rileks tanpa dentuman berlebih", EnergyCategory.CALM),
        LOW("Low (0.3-0.5)", 0.30f, 0.50f, 0.40f, "Ketukan halus, instrumen santai di latar belakang", EnergyCategory.LOW),
        MEDIUM("Medium (0.5-0.7)", 0.50f, 0.70f, 0.60f, "Keseimbangan ideal vokal dan ketukan DJ Slow standar", EnergyCategory.MEDIUM),
        HIGH("High (0.7-0.85)", 0.70f, 0.85f, 0.78f, "Kick tegas, bass padat, instrumen aktif bertenaga", EnergyCategory.HIGH),
        DROP("Drop (0.85-1.0)", 0.85f, 1.0f, 0.95f, "Dentuman penuh klimaks, energi maksimal seluruh elemen", EnergyCategory.DROP)
    }

    /**
     * Preferensi Fokus Remix:
     * VOCAL_DOMINANT, BEAT_DRIVEN, BALANCED, INSTRUMENTAL.
     */
    enum class FocusPreference(
        val label: String,
        val description: String,
        val vocalVolumeOffset: Float,
        val beatVolumeOffset: Float,
        val synthVolumeOffset: Float,
        val duckingDepth: Float
    ) {
        VOCAL_DOMINANT("Fokus Vokal", "Vokal paling jernih & dominan di depan, musik memberi ruang", 0.15f, -0.10f, -0.15f, 0.40f),
        BEAT_DRIVEN("Fokus Beat & Bass", "Kick dan Sub-bass sangat tegas menghentak, vokal berpadu", -0.05f, 0.20f, 0.0f, 0.25f),
        BALANCED("Seimbang (Radio Mix)", "Keseimbangan standar studio rekaman profesional", 0.0f, 0.0f, 0.0f, 0.30f),
        INSTRUMENTAL("Fokus Harmoni & Melodi", "Akor, synth, dan instrumen pengiring lebih menonjol", -0.15f, 0.05f, 0.20f, 0.20f)
    }

    // --- Sub-plans (FASE 2 - Bagian D) ---

    data class ArrangementPlan(
        val totalBars: Int,
        val totalDurationMs: Long,
        val sections: List<SongSection>,
        val introBars: Int,
        val buildBars: Int,
        val dropBars: Int,
        val breakBars: Int,
        val outroBars: Int,
        val transitionBars: List<Int>,
        val behaviorDescription: String
    )

    data class DrumPlan(
        val densityMultiplier: Float,
        val useSnareRollsInBuild: Boolean = true,
        val kickSidechainEnabled: Boolean = true,
        val kickVelocity: Float = 1.0f,
        val snareVelocity: Float = 0.90f,
        val hihatStyle: String = "1/8th Swing DJ Slow",
        val patternVariant: Int = 0,
        val rollComplexity: Int = 1
    )

    data class BassPlan(
        val patternType: BassPatternType,
        val subBoostGain: Float,
        val sidechainDuckAmount: Float,
        val slideProbability: Float,
        val bassVolume: Float,
        val bassStyleName: String = "Warm Sub 808",
        val syncopationVariant: Int = 0
    )

    data class ChordPlan(
        val preset: ChordSynthPreset,
        val volume: Float,
        val voicingWidth: Float,
        val progression: List<Chord>,
        val chordStyleName: String = "Piano Chops"
    )

    data class MelodyPlan(
        val presence: Float,
        val volume: Float,
        val seed: Long,
        val hookScale: String,
        val octaves: Int = 1,
        val melodyStyleName: String = "Vocal-Response Hook"
    )

    data class PadPlan(
        val warmth: Float,
        val volume: Float,
        val stereoSpread: Float = 0.85f,
        val duckDuringDrop: Boolean = true
    )

    data class FxPlan(
        val densityMultiplier: Float,
        val useRisersInBuild: Boolean = true,
        val useImpactOnDrop: Boolean = true,
        val useVocalChopInPreDrop: Boolean = true,
        val volume: Float = 0.75f,
        val riserVariant: Int = 0
    )

    data class VocalPlan(
        val pitchShiftSemitones: Int = 0,
        val timeStretchRatio: Float = 1.0f,
        val duckingDepth: Float,
        val highPassFreqHz: Float = 95.0f,
        val presenceBoostDb: Float = 2.5f,
        val echoSend: Float = 0.20f,
        val reverbSend: Float = 0.25f,
        val stutterInPreDrop: Boolean = true
    )

    data class MixPlan(
        val vocalVolume: Float,
        val beatVolume: Float,
        val drumVolume: Float,
        val bassVolume: Float,
        val chordVolume: Float,
        val melodyVolume: Float,
        val padVolume: Float,
        val fxVolume: Float,
        val masterGain: Float = 1.0f,
        val isAutoMixEnabled: Boolean = true
    )

    data class MasterPlan(
        val preset: MasteringPreset,
        val targetLufs: Float,
        val ceilingDb: Float = -0.5f,
        val saturationAmount: Float,
        val glueCompressionThresholdDb: Float = -12.0f
    )

    /**
     * Rencana Remix Utuh yang dihasilkan oleh RemixBrain (FASE 2 - Bagian D).
     * Minimal memiliki 12 komponen:
     * targetBpm, targetKey, arrangementPlan, drumPlan, bassPlan, chordPlan,
     * melodyPlan, padPlan, fxPlan, vocalPlan, mixPlan, masterPlan.
     */
    data class RemixPlan(
        val targetBpm: Float,
        val targetKey: MusicKey,
        val style: RemixStyle,
        val energyPreference: EnergyPreference,
        val focusPreference: FocusPreference,
        val seed: Long,
        val arrangementPlan: ArrangementPlan,
        val drumPlan: DrumPlan,
        val bassPlan: BassPlan,
        val chordPlan: ChordPlan,
        val melodyPlan: MelodyPlan,
        val padPlan: PadPlan,
        val fxPlan: FxPlan,
        val vocalPlan: VocalPlan,
        val mixPlan: MixPlan,
        val masterPlan: MasterPlan,
        val planSummary: List<String>,
        // Aksesor kompatibilitas untuk kode lama
        val totalBars: Int = arrangementPlan.totalBars,
        val sections: List<SongSection> = arrangementPlan.sections
    ) {
        val seedHex: String get() = seed.toString(16).uppercase()

        fun formatReport(): String {
            return "Rencana Remix [Style: ${style.label}, BPM: $targetBpm, Key: ${targetKey.displayName}, Seed: #$seedHex]\n" +
                    "• Seksi: ${sections.size} seksi (${totalBars} bar)\n" +
                    "• Drum: ${drumPlan.hihatStyle} (${style.drumStyle})\n" +
                    "• Bass: ${bassPlan.patternType.displayName} (${style.bassStyle})\n" +
                    "• Vokal: Ducking ${(vocalPlan.duckingDepth * 100).toInt()}%, Presens +${vocalPlan.presenceBoostDb} dB\n" +
                    "• Master Target: ${masterPlan.targetLufs} LUFS (Ceiling ${masterPlan.ceilingDb} dB)"
        }
    }

    /**
     * Membuat RemixPlan deterministik berbasis data musik nyata (FASE 2 - Bagian D, E, F, G).
     */
    fun createPlan(
        analysis: MusicUnderstandingEngine.MusicAnalysis,
        musicalMap: MusicalMapEngine.MusicalMap,
        style: RemixStyle,
        targetBpmOverride: Float? = null,
        energyPreference: EnergyPreference = EnergyPreference.MEDIUM,
        focusPreference: FocusPreference = FocusPreference.BALANCED,
        seed: Long = System.currentTimeMillis()
    ): RemixPlan {
        // Pseudo-random generator terikat seed (FASE 2 - Bagian G)
        val random = Random(seed)

        // 1. Tentukan BPM Target (pilih override atau sesuaikan BPM sumber ke rentang style nyata)
        val selectedBpm = targetBpmOverride ?: run {
            val sourceBpm = analysis.bpm
            if (sourceBpm in style.minBpm..style.maxBpm) {
                sourceBpm
            } else {
                style.defaultBpm
            }
        }
        val safeBpm = selectedBpm.coerceIn(style.minBpm, style.maxBpm)

        // 2. Tentukan Key Target (mengikuti analisis asli vokal agar alami tanpa distorsi pitch)
        val targetKey = analysis.key

        // 3. Modifikasi Energi berdasarkan preferensi user dan kategori energi (FASE 2 - Bagian F)
        val energyTarget = energyPreference.targetEnergy
        val energyScale = (energyTarget / 0.60f).coerceIn(0.5f, 1.5f)

        // Variasi terkontrol berbasis SEED (FASE 2 - Bagian G)
        val hihatPatternVariant = random.nextInt(3)
        val rollComplexity = 1 + random.nextInt(3)
        val bassSyncopationVariant = random.nextInt(4)
        val riserVariant = random.nextInt(2)

        // 4. Perencanaan Drum (dipengaruhi oleh style, energi, dan seed)
        val drumDensity = (style.drumDensityMultiplier * energyScale).coerceIn(0.4f, 1.6f)
        val kickVelocity = when (energyPreference.category) {
            EnergyCategory.CALM -> 0.70f
            EnergyCategory.LOW -> 0.80f
            EnergyCategory.MEDIUM -> 0.90f
            EnergyCategory.HIGH -> 0.98f
            EnergyCategory.DROP -> 1.0f
        }
        val snareVelocity = when (energyPreference.category) {
            EnergyCategory.CALM -> 0.60f
            EnergyCategory.LOW -> 0.75f
            EnergyCategory.MEDIUM -> 0.88f
            EnergyCategory.HIGH -> 0.95f
            EnergyCategory.DROP -> 1.0f
        }

        val drumPlan = DrumPlan(
            densityMultiplier = drumDensity,
            useSnareRollsInBuild = energyPreference.category >= EnergyCategory.LOW,
            kickSidechainEnabled = true,
            kickVelocity = kickVelocity,
            snareVelocity = snareVelocity,
            hihatStyle = "${style.drumStyle} (Pola #${hihatPatternVariant + 1})",
            patternVariant = hihatPatternVariant,
            rollComplexity = rollComplexity
        )

        // 5. Perencanaan Bass (dipengaruhi oleh style, energi, fokus, dan seed)
        val bassBoost = style.bassBoostMultiplier * (if (focusPreference == FocusPreference.BEAT_DRIVEN) 1.15f else 1.0f)
        val sidechainDuckAmount = when (energyPreference.category) {
            EnergyCategory.CALM -> 0.35f
            EnergyCategory.LOW -> 0.45f
            EnergyCategory.MEDIUM -> 0.60f
            EnergyCategory.HIGH -> 0.75f
            EnergyCategory.DROP -> 0.85f
        }
        val slideChance = if (style == RemixStyle.DJ_SLOW_BASS || style == RemixStyle.DJ_SLOW_DARK) 0.35f else 0.12f

        val bassPlan = BassPlan(
            patternType = style.bassPatternType,
            subBoostGain = bassBoost,
            sidechainDuckAmount = sidechainDuckAmount,
            slideProbability = slideChance,
            bassVolume = (style.mixBalance.bassWeight * bassBoost * (0.75f + energyTarget * 0.35f)).coerceIn(0.4f, 1.2f),
            bassStyleName = style.bassStyle,
            syncopationVariant = bassSyncopationVariant
        )

        // 6. Perencanaan Akor
        val chordPlan = ChordPlan(
            preset = style.chordPreset,
            volume = (style.mixBalance.chordWeight + focusPreference.synthVolumeOffset).coerceIn(0.3f, 0.95f),
            voicingWidth = if (style == RemixStyle.DJ_SLOW_ROMANTIS || style == RemixStyle.DJ_SLOW_DEEP) 0.90f else 0.70f,
            progression = analysis.chords,
            chordStyleName = style.chordStyle
        )

        // 7. Perencanaan Melodi Hook (prosedural dengan seed terisolasi)
        val melodySeed = seed xor 0x5DEECE66DL
        val melodyPlan = MelodyPlan(
            presence = if (style == RemixStyle.DJ_SLOW_ROMANTIS || style == RemixStyle.DJ_SLOW_PARTY) 1.0f else 0.75f,
            volume = (style.mixBalance.melodyWeight + focusPreference.synthVolumeOffset).coerceIn(0.3f, 0.90f),
            seed = melodySeed,
            hookScale = targetKey.displayName,
            octaves = if (style == RemixStyle.DJ_SLOW_PARTY) 2 else 1,
            melodyStyleName = style.melodyStyle
        )

        // 8. Perencanaan Pad Atmosfir
        val padPlan = PadPlan(
            warmth = if (style == RemixStyle.DJ_SLOW_ROMANTIS || style == RemixStyle.DJ_SLOW_DEEP) 1.2f else 0.9f,
            volume = (style.mixBalance.padWeight * (if (energyPreference.category == EnergyCategory.CALM) 1.15f else 0.90f)).coerceIn(0.3f, 0.95f),
            stereoSpread = 0.85f,
            duckDuringDrop = energyPreference.category >= EnergyCategory.HIGH
        )

        // 9. Perencanaan FX Transisi
        val fxPlan = FxPlan(
            densityMultiplier = style.fxDensity * (0.75f + energyTarget * 0.45f),
            useRisersInBuild = energyPreference.category >= EnergyCategory.LOW,
            useImpactOnDrop = true,
            useVocalChopInPreDrop = style != RemixStyle.DJ_SLOW_ROMANTIS,
            volume = (style.mixBalance.fxWeight).coerceIn(0.4f, 1.0f),
            riserVariant = riserVariant
        )

        // 10. Perencanaan Vokal
        val vocalPlan = VocalPlan(
            pitchShiftSemitones = 0,
            timeStretchRatio = if (analysis.bpm > 0) safeBpm / analysis.bpm else 1.0f,
            duckingDepth = focusPreference.duckingDepth,
            highPassFreqHz = if (style == RemixStyle.DJ_SLOW_BASS) 110.0f else 95.0f,
            presenceBoostDb = if (focusPreference == FocusPreference.VOCAL_DOMINANT) 3.5f else 2.0f,
            echoSend = if (style == RemixStyle.DJ_SLOW_DEEP) 0.30f else 0.18f,
            reverbSend = if (style == RemixStyle.DJ_SLOW_ROMANTIS || style == RemixStyle.DJ_SLOW_DEEP) 0.35f else 0.22f,
            stutterInPreDrop = style == RemixStyle.DJ_SLOW_DARK || style == RemixStyle.DJ_SLOW_PARTY
        )

        // 11. Perencanaan Mix
        val mixPlan = MixPlan(
            vocalVolume = (style.mixBalance.vocalWeight + focusPreference.vocalVolumeOffset).coerceIn(0.4f, 1.25f),
            beatVolume = (style.mixBalance.beatWeight + focusPreference.beatVolumeOffset).coerceIn(0.0f, 1.20f),
            drumVolume = (style.mixBalance.drumWeight + focusPreference.beatVolumeOffset).coerceIn(0.3f, 1.25f),
            bassVolume = bassPlan.bassVolume,
            chordVolume = chordPlan.volume,
            melodyVolume = melodyPlan.volume,
            padVolume = padPlan.volume,
            fxVolume = fxPlan.volume,
            masterGain = 1.0f,
            isAutoMixEnabled = true
        )

        // 12. Perencanaan Mastering
        val masterPlan = MasterPlan(
            preset = style.masteringPreset,
            targetLufs = style.masterTargetLufs,
            ceilingDb = -0.5f,
            saturationAmount = if (style == RemixStyle.DJ_SLOW_DARK || style == RemixStyle.DJ_SLOW_BASS) 0.22f else 0.12f
        )

        // Susun ArrangementPlan nyata
        val sections = musicalMap.sections
        val introBars = sections.filter { it.sectionType == SongSectionType.INTRO }.sumOf { it.barCount }
        val buildBars = sections.filter { it.sectionType == SongSectionType.BUILD_UP }.sumOf { it.barCount }
        val dropBars = sections.filter { it.sectionType == SongSectionType.DROP || it.sectionType == SongSectionType.MAIN_DROP }.sumOf { it.barCount }
        val breakBars = sections.filter { it.sectionType == SongSectionType.BREAK }.sumOf { it.barCount }
        val outroBars = sections.filter { it.sectionType == SongSectionType.OUTRO }.sumOf { it.barCount }
        val transitionBars = musicalMap.getTransitionBars().map { it.barIndex }

        val arrangementPlan = ArrangementPlan(
            totalBars = musicalMap.totalBars,
            totalDurationMs = musicalMap.totalDurationMs,
            sections = sections,
            introBars = introBars,
            buildBars = buildBars,
            dropBars = dropBars,
            breakBars = breakBars,
            outroBars = outroBars,
            transitionBars = transitionBars,
            behaviorDescription = style.arrangementBehavior
        )

        // Ringkasan rencana musikal nyata
        val summaryList = listOf(
            "Gaya Remix: ${style.label} @ ${safeBpm.toInt()} BPM (${targetKey.displayName})",
            "Preferensi: Energi ${energyPreference.label} (${energyPreference.category.label}) • Fokus ${focusPreference.label}",
            "Struktur: ${sections.size} Bagian Aransemen, ${musicalMap.totalBars} Bar (Intro: $introBars, Drop: $dropBars bar)",
            "Drum & Bass: ${style.drumStyle} • ${style.bassStyle} (Sub x${String.format("%.2f", bassBoost)})",
            "Harmoni: ${style.chordStyle} • Pad Atmosfir • Melodi ${style.melodyStyle}",
            "Vokal: Ducking ${(vocalPlan.duckingDepth * 100).toInt()}%, Presens +${vocalPlan.presenceBoostDb} dB, Stretch x${String.format("%.2f", vocalPlan.timeStretchRatio)}",
            "Master: Target ${masterPlan.targetLufs} LUFS (Ceiling ${masterPlan.ceilingDb} dB)"
        )

        return RemixPlan(
            targetBpm = safeBpm,
            targetKey = targetKey,
            style = style,
            energyPreference = energyPreference,
            focusPreference = focusPreference,
            seed = seed,
            arrangementPlan = arrangementPlan,
            drumPlan = drumPlan,
            bassPlan = bassPlan,
            chordPlan = chordPlan,
            melodyPlan = melodyPlan,
            padPlan = padPlan,
            fxPlan = fxPlan,
            vocalPlan = vocalPlan,
            mixPlan = mixPlan,
            masterPlan = masterPlan,
            planSummary = summaryList
        )
    }
}
