package com.autoremix.djslow.engine.core

import com.autoremix.djslow.engine.drum.DrumEvent
import com.autoremix.djslow.engine.mix.BassProcessor
import com.autoremix.djslow.engine.mix.DrumProcessor
import com.autoremix.djslow.engine.mix.KickBassEngine
import com.autoremix.djslow.engine.mix.MixEngine
import com.autoremix.djslow.engine.mix.MixTrackSettings
import com.autoremix.djslow.engine.mix.VocalDucker
import com.autoremix.djslow.engine.mix.VocalProcessor
import com.autoremix.djslow.engine.pcm.AudioPcmData
import com.autoremix.djslow.engine.structure.SongSectionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 8. INTELLIGENT MIX ENGINE
 * Mengintegrasikan seluruh prosesor:
 * VocalProcessor, DrumProcessor, BassProcessor, KickBassEngine, VocalDucker, StereoEngine, dan MixEngine.
 * Melakukan Auto Mix cerdas berbasis susunan aransemen, kurva energi, kehadiran vokal, dan energi bass/drum.
 */
object IntelligentMixEngine {

    data class TrackControls(
        val volume: Float = 1.0f,
        val isMuted: Boolean = false,
        val isSolo: Boolean = false,
        val pan: Float = 0.0f, // -1.0 (L) .. 1.0 (R) - Berlaku saat render
        val isDuckingActive: Boolean = true
    )

    data class IntelligentMixConfig(
        val vocalControls: TrackControls = TrackControls(volume = 1.0f),
        val beatControls: TrackControls = TrackControls(volume = 0.80f),
        val drumControls: TrackControls = TrackControls(volume = 0.90f),
        val bassControls: TrackControls = TrackControls(volume = 0.85f),
        val chordControls: TrackControls = TrackControls(volume = 0.70f),
        val melodyControls: TrackControls = TrackControls(volume = 0.80f),
        val padControls: TrackControls = TrackControls(volume = 0.75f),
        val fxControls: TrackControls = TrackControls(volume = 0.80f),
        val masterGain: Float = 1.0f,
        val isAutoMixEnabled: Boolean = true
    )

    /**
     * Menjalankan intelligent mixing seluruh track audio.
     */
    suspend fun performIntelligentMix(
        vocalPcm: AudioPcmData?,
        beatPcm: AudioPcmData?,
        drumPcm: AudioPcmData?,
        bassPcm: AudioPcmData?,
        chordPcm: AudioPcmData?,
        melodyPcm: AudioPcmData?,
        padPcm: AudioPcmData?,
        fxPcm: AudioPcmData?,
        drumEvents: List<DrumEvent>,
        remixPlan: RemixBrain.RemixPlan,
        arrangement: ArrangementEngine.FullArrangement,
        config: IntelligentMixConfig,
        onProgress: ((Float, String) -> Unit)? = null
    ): Result<AudioPcmData> = withContext(Dispatchers.Default) {
        try {
            onProgress?.invoke(0.10f, "Memproses pembersihan & kompresi Track Vokal...")
            val processedVocal = if (vocalPcm != null && !vocalPcm.isSilent()) {
                VocalProcessor.process(vocalPcm)
            } else {
                null
            }

            onProgress?.invoke(0.25f, "Memproses EQ punch & transient Track Drum...")
            val processedDrum = if (drumPcm != null && !drumPcm.isSilent()) {
                DrumProcessor.process(drumPcm)
            } else {
                null
            }

            onProgress?.invoke(0.40f, "Memproses saturasi sub-bass & notch separation...")
            var processedBass = if (bassPcm != null && !bassPcm.isSilent()) {
                BassProcessor.process(bassPcm)
            } else {
                null
            }

            // Sidechain Kick-to-Bass Ducking
            if (processedBass != null && drumEvents.isNotEmpty() && remixPlan.drumPlan.kickSidechainEnabled) {
                onProgress?.invoke(0.55f, "Menerapkan Kick-to-Bass sidechain ducking otomatis...")
                processedBass = KickBassEngine.applySidechainDucking(
                    bassPcm = processedBass,
                    drumEvents = drumEvents,
                    sampleRate = processedBass.sampleRate
                )
            }

            // Auto Mix Dynamic Scaling berdasarkan aransemen & energi
            val dynamicVocalVol = config.vocalControls.volume
            var dynamicBeatVol = config.beatControls.volume
            var dynamicDrumVol = config.drumControls.volume
            var dynamicBassVol = config.bassControls.volume
            var dynamicChordVol = config.chordControls.volume
            var dynamicMelodyVol = config.melodyControls.volume
            var dynamicPadVol = config.padControls.volume
            var dynamicFxVol = config.fxControls.volume

            if (config.isAutoMixEnabled) {
                // Skala instrumen otomatis agar vokal selalu jernih dan bebas masking frekuensi
                val vocalPresent = processedVocal != null && !processedVocal.isSilent()
                if (vocalPresent) {
                    dynamicChordVol *= 0.85f
                    dynamicPadVol *= 0.85f
                    dynamicMelodyVol *= 0.88f
                }
            }

            val mixParams = MixEngine.MixParams(
                vocalSettings = MixTrackSettings(
                    volume = dynamicVocalVol,
                    isMuted = config.vocalControls.isMuted,
                    isSolo = config.vocalControls.isSolo
                ),
                beatSettings = MixTrackSettings(
                    volume = dynamicBeatVol,
                    isMuted = config.beatControls.isMuted,
                    isSolo = config.beatControls.isSolo
                ),
                drumSettings = MixTrackSettings(
                    volume = dynamicDrumVol,
                    isMuted = config.drumControls.isMuted,
                    isSolo = config.drumControls.isSolo
                ),
                bassSettings = MixTrackSettings(
                    volume = dynamicBassVol,
                    isMuted = config.bassControls.isMuted,
                    isSolo = config.bassControls.isSolo
                ),
                chordSettings = MixTrackSettings(
                    volume = dynamicChordVol,
                    isMuted = config.chordControls.isMuted,
                    isSolo = config.chordControls.isSolo
                ),
                melodySettings = MixTrackSettings(
                    volume = dynamicMelodyVol,
                    isMuted = config.melodyControls.isMuted,
                    isSolo = config.melodyControls.isSolo
                ),
                padSettings = MixTrackSettings(
                    volume = dynamicPadVol,
                    isMuted = config.padControls.isMuted,
                    isSolo = config.padControls.isSolo
                ),
                fxSettings = MixTrackSettings(
                    volume = dynamicFxVol,
                    isMuted = config.fxControls.isMuted,
                    isSolo = config.fxControls.isSolo
                ),
                masterGain = config.masterGain,
                isAutoMixEnabled = config.isAutoMixEnabled
            )

            onProgress?.invoke(0.75f, "Melakukan summing multi-bus ke stereo pre-master...")
            val mixResult = MixEngine.mix(
                vocalPcm = processedVocal,
                beatPcm = beatPcm,
                chordPcm = chordPcm,
                bassPcm = processedBass,
                drumPcm = processedDrum,
                melodyPcm = melodyPcm,
                padPcm = padPcm,
                transitionPcm = fxPcm,
                params = mixParams
            )

            mixResult
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Intelligent Mix gagal: ${e.localizedMessage ?: e.message}"))
        }
    }
}
