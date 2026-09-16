package com.autoremix.djslow.engine.core

import com.autoremix.djslow.engine.arrangement.AutoDjPreset
import com.autoremix.djslow.engine.mastering.MasteringPreset
import com.autoremix.djslow.engine.music.Chord
import com.autoremix.djslow.engine.music.MusicKey
import com.autoremix.djslow.engine.structure.SongSection
import com.autoremix.djslow.engine.structure.SongSectionType
import com.autoremix.djslow.engine.synth.BassPatternType
import com.autoremix.djslow.engine.synth.ChordSynthPreset
import kotlin.random.Random

/**
 * 4. REMIX BRAIN & STYLE SYSTEM
 * Otak pusat pengambil keputusan aransemen remix.
 * Semua engine lain mengeksekusi rencana terpadu ini.
 */
object RemixBrain {

    /**
     * 6 Style Profile DJ Slow spesifik sesuai mandat:
     * DJ SLOW, DJ SLOW BASS, DJ SLOW ROMANTIS, DJ SLOW DARK, DJ SLOW DEEP, DJ SLOW PARTY.
     */
    enum class RemixStyle(
        val label: String,
        val description: String,
        val defaultBpm: Float,
        val minBpm: Float,
        val maxBpm: Float,
        val drumDensityMultiplier: Float,
        val bassBoostMultiplier: Float,
        val chordPreset: ChordSynthPreset,
        val bassPatternType: BassPatternType,
        val fxDensity: Float,
        val masteringPreset: MasteringPreset,
        val legacyPreset: AutoDjPreset
    ) {
        DJ_SLOW(
            label = "DJ SLOW",
            description = "Gaya remix santai, tempo 80 BPM, bass empuk & ketukan berbobot",
            defaultBpm = 80.0f,
            minBpm = 75.0f,
            maxBpm = 85.0f,
            drumDensityMultiplier = 0.85f,
            bassBoostMultiplier = 1.0f,
            chordPreset = ChordSynthPreset.PIANO,
            bassPatternType = BassPatternType.DJ_SLOW_BASS,
            fxDensity = 0.7f,
            masteringPreset = MasteringPreset.DJ_SLOW,
            legacyPreset = AutoDjPreset.DJ_SLOW
        ),
        DJ_SLOW_BASS(
            label = "DJ SLOW BASS",
            description = "Sub-bass bertenaga tinggi, sidechain mendalam untuk jedag-jedug santai",
            defaultBpm = 78.0f,
            minBpm = 72.0f,
            maxBpm = 84.0f,
            drumDensityMultiplier = 0.95f,
            bassBoostMultiplier = 1.45f,
            chordPreset = ChordSynthPreset.SOFT_SYNTH,
            bassPatternType = BassPatternType.DJ_SLOW_BASS,
            fxDensity = 0.85f,
            masteringPreset = MasteringPreset.BASS_STRONG,
            legacyPreset = AutoDjPreset.DJ_SLOW_BASS
        ),
        DJ_SLOW_ROMANTIS(
            label = "DJ SLOW ROMANTIS",
            description = "Melodi manis, piano lembut, progresi akor emosional menyentuh hati",
            defaultBpm = 76.0f,
            minBpm = 70.0f,
            maxBpm = 82.0f,
            drumDensityMultiplier = 0.65f,
            bassBoostMultiplier = 0.85f,
            chordPreset = ChordSynthPreset.SOFT_PIANO,
            bassPatternType = BassPatternType.ROOT,
            fxDensity = 0.5f,
            masteringPreset = MasteringPreset.NATURAL,
            legacyPreset = AutoDjPreset.DJ_EMOTIONAL
        ),
        DJ_SLOW_DARK(
            label = "DJ SLOW DARK",
            description = "Suasana misterius, synth bass garang, drop menegangkan dan pad gelap",
            defaultBpm = 82.0f,
            minBpm = 76.0f,
            maxBpm = 88.0f,
            drumDensityMultiplier = 1.10f,
            bassBoostMultiplier = 1.25f,
            chordPreset = ChordSynthPreset.SOFT_SYNTH,
            bassPatternType = BassPatternType.SYNCOPATED,
            fxDensity = 1.0f,
            masteringPreset = MasteringPreset.VOCAL_CLEAR,
            legacyPreset = AutoDjPreset.DJ_FULL_POWER
        ),
        DJ_SLOW_DEEP(
            label = "DJ SLOW DEEP",
            description = "Nuansa deep slow house, rolling sub-bass empuk, reverb luas atmosferik",
            defaultBpm = 84.0f,
            minBpm = 78.0f,
            maxBpm = 90.0f,
            drumDensityMultiplier = 0.90f,
            bassBoostMultiplier = 1.20f,
            chordPreset = ChordSynthPreset.PIANO,
            bassPatternType = BassPatternType.DJ_SLOW_BASS,
            fxDensity = 0.75f,
            masteringPreset = MasteringPreset.NATURAL,
            legacyPreset = AutoDjPreset.DJ_SLOW
        ),
        DJ_SLOW_PARTY(
            label = "DJ SLOW PARTY",
            description = "Tempo enerjik, snare roll cepat, riser membahana, drop klimaks penuh",
            defaultBpm = 90.0f,
            minBpm = 84.0f,
            maxBpm = 100.0f,
            drumDensityMultiplier = 1.30f,
            bassBoostMultiplier = 1.20f,
            chordPreset = ChordSynthPreset.PAD,
            bassPatternType = BassPatternType.SYNCOPATED,
            fxDensity = 1.2f,
            masteringPreset = MasteringPreset.LOUD_CLEAN,
            legacyPreset = AutoDjPreset.DJ_PARTY
        );

        val recommendedBpmRange: ClosedFloatingPointRange<Float>
            get() = minBpm..maxBpm
    }

    /**
     * Preferensi Energi Remix:
     * CALM (0.0-0.3), LOW (0.3-0.5), MEDIUM (0.5-0.7), HIGH (0.7-0.85), DROP (0.85-1.0).
     */
    enum class EnergyPreference(
        val label: String,
        val minLevel: Float,
        val maxLevel: Float,
        val targetEnergy: Float,
        val description: String
    ) {
        CALM("Calm (Tenang)", 0.0f, 0.30f, 0.25f, "Nuansa akustik/pad rileks tanpa dentuman berlebih"),
        LOW("Low (Santai)", 0.30f, 0.50f, 0.45f, "Ketukan halus, instrumen dominan di latar belakang"),
        MEDIUM("Medium (Seimbang)", 0.50f, 0.70f, 0.60f, "Keseimbangan ideal vokal dan ketukan DJ Slow"),
        HIGH("High (Bertenaga)", 0.70f, 0.85f, 0.78f, "Kick tegas, bass padat, instrumen aktif bersemangat"),
        DROP("Drop (Maksimal)", 0.85f, 1.0f, 0.95f, "Dentuman penuh klimaks, energi maksimal seluruh elemen")
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
        INSTRUMENTAL("Fokus Harmoni & Melodi", "Akord, synth, dan instrumen pengiring lebih menonjol", -0.15f, 0.05f, 0.20f, 0.20f)
    }

    // --- Sub-plans ---
    data class DrumPlan(
        val densityMultiplier: Float,
        val useSnareRollsInBuild: Boolean = true,
        val kickSidechainEnabled: Boolean = true,
        val kickVelocity: Float = 1.0f,
        val snareVelocity: Float = 0.90f,
        val hihatStyle: String = "1/8th Swing DJ Slow"
    )

    data class BassPlan(
        val patternType: BassPatternType,
        val subBoostGain: Float,
        val sidechainDuckAmount: Float,
        val slideProbability: Float,
        val bassVolume: Float
    )

    data class ChordPlan(
        val preset: ChordSynthPreset,
        val volume: Float,
        val voicingWidth: Float,
        val progression: List<Chord>
    )

    data class MelodyPlan(
        val presence: Float,
        val volume: Float,
        val seed: Long,
        val hookScale: String,
        val octaves: Int = 1
    )

    data class PadPlan(
        val warmth: Float,
        val volume: Float,
        val stereoSpread: Float = 0.85f
    )

    data class FxPlan(
        val densityMultiplier: Float,
        val useRisersInBuild: Boolean = true,
        val useImpactOnDrop: Boolean = true,
        val useVocalChopInPreDrop: Boolean = true,
        val volume: Float = 0.75f
    )

    data class VocalPlan(
        val pitchShiftSemitones: Int = 0,
        val timeStretchRatio: Float = 1.0f,
        val duckingDepth: Float,
        val highPassFreqHz: Float = 95.0f,
        val presenceBoostDb: Float = 2.5f,
        val echoSend: Float = 0.20f,
        val reverbSend: Float = 0.25f
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
     * Rencana Remix Utuh yang dihasilkan oleh RemixBrain.
     */
    data class RemixPlan(
        val targetBpm: Float,
        val targetKey: MusicKey,
        val style: RemixStyle,
        val energyPreference: EnergyPreference,
        val focusPreference: FocusPreference,
        val seed: Long,
        val totalBars: Int,
        val sections: List<SongSection>,
        val drumPlan: DrumPlan,
        val bassPlan: BassPlan,
        val chordPlan: ChordPlan,
        val melodyPlan: MelodyPlan,
        val padPlan: PadPlan,
        val fxPlan: FxPlan,
        val vocalPlan: VocalPlan,
        val mixPlan: MixPlan,
        val masterPlan: MasterPlan,
        val planSummary: List<String>
    ) {
        val seedHex: String get() = seed.toString(16).uppercase()

        fun formatReport(): String {
            return "Rencana Remix [Style: ${style.label}, BPM: $targetBpm, Key: ${targetKey.displayName}, Seed: #$seedHex]\n" +
                    "• Seksi: ${sections.size} seksi (${totalBars} bar)\n" +
                    "• Drum: ${drumPlan.hihatStyle}, Bass: ${bassPlan.patternType.displayName}\n" +
                    "• Vokal: Ducking ${(vocalPlan.duckingDepth * 100).toInt()}%, Presens +${vocalPlan.presenceBoostDb} dB\n" +
                    "• Master Target: ${masterPlan.targetLufs} LUFS (Ceiling ${masterPlan.ceilingDb} dB)"
        }
    }

    /**
     * Membuat RemixPlan yang menentukan seluruh eksekusi remix.
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
        val random = Random(seed)

        // 1. Tentukan BPM Target (pilih override atau sesuaikan BPM sumber ke rentang style)
        val selectedBpm = targetBpmOverride ?: run {
            val sourceBpm = analysis.bpm
            if (sourceBpm in style.minBpm..style.maxBpm) {
                sourceBpm
            } else {
                style.defaultBpm
            }
        }
        val safeBpm = selectedBpm.coerceIn(style.minBpm, style.maxBpm)

        // 2. Tentukan Key Target (mengikuti analisis asli vokal agar alami tanpa distorsi pitch berlebihan)
        val targetKey = analysis.key

        // 3. Modifikasi Energi berdasarkan preferensi user
        val energyTarget = energyPreference.targetEnergy
        val energyScale = (energyTarget / 0.60f).coerceIn(0.6f, 1.4f)

        // 4. Perencanaan Drum
        val drumDensity = (style.drumDensityMultiplier * energyScale).coerceIn(0.5f, 1.5f)
        val drumPlan = DrumPlan(
            densityMultiplier = drumDensity,
            useSnareRollsInBuild = true,
            kickSidechainEnabled = true,
            kickVelocity = (1.0f * energyScale).coerceIn(0.8f, 1.0f),
            snareVelocity = (0.90f * energyScale).coerceIn(0.7f, 1.0f),
            hihatStyle = when (style) {
                RemixStyle.DJ_SLOW_BASS -> "1/8th Swing DJ Slow Heavy"
                RemixStyle.DJ_SLOW_PARTY -> "1/16th Rolling Dance"
                RemixStyle.DJ_SLOW_ROMANTIS -> "1/4th Relaxed Soft"
                else -> "1/8th DJ Slow Standard"
            }
        )

        // 5. Perencanaan Bass
        val bassBoost = style.bassBoostMultiplier * (if (focusPreference == FocusPreference.BEAT_DRIVEN) 1.15f else 1.0f)
        val bassPlan = BassPlan(
            patternType = style.bassPatternType,
            subBoostGain = bassBoost,
            sidechainDuckAmount = (0.50f + (style.bassBoostMultiplier * 0.15f)).coerceIn(0.4f, 0.85f),
            slideProbability = if (style == RemixStyle.DJ_SLOW_BASS || style == RemixStyle.DJ_SLOW_DARK) 0.35f else 0.10f,
            bassVolume = (0.85f * bassBoost * (0.8f + energyTarget * 0.3f)).coerceAtMost(1.0f)
        )

        // 6. Perencanaan Akor
        val chordPlan = ChordPlan(
            preset = style.chordPreset,
            volume = (0.70f + focusPreference.synthVolumeOffset).coerceIn(0.3f, 0.95f),
            voicingWidth = if (style == RemixStyle.DJ_SLOW_ROMANTIS || style == RemixStyle.DJ_SLOW_DEEP) 0.90f else 0.70f,
            progression = analysis.chords
        )

        // 7. Perencanaan Melodi Hook
        val melodyPlan = MelodyPlan(
            presence = if (style == RemixStyle.DJ_SLOW_ROMANTIS || style == RemixStyle.DJ_SLOW_PARTY) 1.0f else 0.75f,
            volume = (0.65f + focusPreference.synthVolumeOffset).coerceIn(0.3f, 0.90f),
            seed = seed + 101L,
            hookScale = targetKey.displayName,
            octaves = if (style == RemixStyle.DJ_SLOW_PARTY) 2 else 1
        )

        // 8. Perencanaan Pad Atmosfir
        val padPlan = PadPlan(
            warmth = if (style == RemixStyle.DJ_SLOW_ROMANTIS || style == RemixStyle.DJ_SLOW_DEEP) 1.2f else 0.9f,
            volume = if (style == RemixStyle.DJ_SLOW_DEEP || style == RemixStyle.DJ_SLOW_ROMANTIS) 0.80f else 0.65f,
            stereoSpread = 0.85f
        )

        // 9. Perencanaan FX Transisi
        val fxPlan = FxPlan(
            densityMultiplier = style.fxDensity * (0.8f + energyTarget * 0.4f),
            useRisersInBuild = true,
            useImpactOnDrop = true,
            useVocalChopInPreDrop = style != RemixStyle.DJ_SLOW_ROMANTIS,
            volume = 0.75f
        )

        // 10. Perencanaan Vokal & FX
        val vocalPlan = VocalPlan(
            pitchShiftSemitones = 0,
            timeStretchRatio = if (analysis.bpm > 0) safeBpm / analysis.bpm else 1.0f,
            duckingDepth = focusPreference.duckingDepth,
            highPassFreqHz = if (style == RemixStyle.DJ_SLOW_BASS) 110.0f else 95.0f,
            presenceBoostDb = if (focusPreference == FocusPreference.VOCAL_DOMINANT) 3.5f else 2.0f,
            echoSend = if (style == RemixStyle.DJ_SLOW_DEEP) 0.30f else 0.18f,
            reverbSend = if (style == RemixStyle.DJ_SLOW_ROMANTIS || style == RemixStyle.DJ_SLOW_DEEP) 0.35f else 0.22f
        )

        // 11. Perencanaan Mix
        val mixPlan = MixPlan(
            vocalVolume = (1.0f + focusPreference.vocalVolumeOffset).coerceIn(0.4f, 1.2f),
            beatVolume = (0.80f + focusPreference.beatVolumeOffset).coerceIn(0.0f, 1.2f),
            drumVolume = (0.90f + focusPreference.beatVolumeOffset).coerceIn(0.3f, 1.2f),
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
            targetLufs = style.masteringPreset.targetLufs,
            ceilingDb = -0.5f,
            saturationAmount = if (style == RemixStyle.DJ_SLOW_DARK || style == RemixStyle.DJ_SLOW_BASS) 0.20f else 0.10f
        )

        // Ringkasan rencana
        val summaryList = listOf(
            "Gaya Remix: ${style.label} @ ${safeBpm.toInt()} BPM (${targetKey.displayName})",
            "Preferensi: Energi ${energyPreference.label} • Fokus ${focusPreference.label}",
            "Struktur: ${musicalMap.sections.size} Bagian Aransemen, ${musicalMap.totalBars} Bar",
            "Drum & Bass: ${drumPlan.hihatStyle} • ${bassPlan.patternType.displayName} (Boost x${String.format("%.2f", bassBoost)})",
            "Instrumen: Akor ${style.chordPreset.displayName} • Pad Atmosfir Luas • Melodi Hook",
            "Vokal: Presens +${vocalPlan.presenceBoostDb} dB, Sidechain Ducking ${(vocalPlan.duckingDepth * 100).toInt()}%",
            "Master: Preset ${style.masteringPreset.label} (${masterPlan.targetLufs} LUFS)"
        )

        return RemixPlan(
            targetBpm = safeBpm,
            targetKey = targetKey,
            style = style,
            energyPreference = energyPreference,
            focusPreference = focusPreference,
            seed = seed,
            totalBars = musicalMap.totalBars,
            sections = musicalMap.sections,
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
