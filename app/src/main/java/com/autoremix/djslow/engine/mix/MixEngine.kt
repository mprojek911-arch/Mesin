package com.autoremix.djslow.engine.mix

import com.autoremix.djslow.engine.pcm.AudioBufferPool
import com.autoremix.djslow.engine.pcm.AudioPcmData
import kotlin.math.abs

/**
 * Mix Engine Multi-Bus & Auto Gain Staging (Tahap 5).
 *
 * Mengelompokkan trek ke dalam 6 MIX BUS resmi:
 * 1. VOCAL BUS  : Vokal utama
 * 2. BEAT BUS   : Trek beat sumber
 * 3. DRUM BUS   : Drum synth aransemen
 * 4. BASS BUS   : Sub-bass sidechained
 * 5. MUSIC BUS  : Akor, Melodi, Pad, FX Transisi (dengan ducking vokal otomatis)
 * 6. MASTER BUS : Bus summing utama dengan proteksi headroom aman sebelum mastering
 *
 * Fitur:
 * - Kontrol independen Volume, Mute, Solo untuk setiap trek dan bus
 * - Auto Gain Staging: Prioritas hierarki VOCAL -> KICK/DRUM -> BASS -> MUSIC BUS
 * - Headroom pre-master terkalibrasi (-4.5 dBFS) agar tahap Auto Mastering memiliki ruang dinamika optimal
 */
object MixEngine {

    const val PRE_MASTER_HEADROOM_PEAK = 0.85f // -1.4 dBFS Headroom sebelum mastering

    data class MixParams(
        val vocalSettings: MixTrackSettings = MixTrackSettings(volume = 1.0f),
        val beatSettings: MixTrackSettings = MixTrackSettings(volume = 0.80f),
        val drumSettings: MixTrackSettings = MixTrackSettings(volume = 0.85f),
        val bassSettings: MixTrackSettings = MixTrackSettings(volume = 0.85f),
        val chordSettings: MixTrackSettings = MixTrackSettings(volume = 0.70f),
        val melodySettings: MixTrackSettings = MixTrackSettings(volume = 0.80f),
        val padSettings: MixTrackSettings = MixTrackSettings(volume = 0.75f),
        val fxSettings: MixTrackSettings = MixTrackSettings(volume = 0.80f),
        val vocalBusSettings: BusSettings = BusSettings(BusType.VOCAL_BUS, volume = 1.0f),
        val beatBusSettings: BusSettings = BusSettings(BusType.BEAT_BUS, volume = 1.0f),
        val drumBusSettings: BusSettings = BusSettings(BusType.DRUM_BUS, volume = 1.0f),
        val bassBusSettings: BusSettings = BusSettings(BusType.BASS_BUS, volume = 1.0f),
        val musicBusSettings: BusSettings = BusSettings(BusType.MUSIC_BUS, volume = 1.0f),
        val masterGain: Float = 0.90f,
        val isAutoMixEnabled: Boolean = true
    )

    /**
     * Melakukan mixing antara seluruh layer audio PCM ke stereo Float32 melalui arsitektur Mix Bus.
     */
    fun mix(
        vocalPcm: AudioPcmData?,
        beatPcm: AudioPcmData?,
        chordPcm: AudioPcmData? = null,
        bassPcm: AudioPcmData? = null,
        drumPcm: AudioPcmData? = null,
        melodyPcm: AudioPcmData? = null,
        padPcm: AudioPcmData? = null,
        transitionPcm: AudioPcmData? = null,
        params: MixParams = MixParams(),
        onProgress: ((Float, String) -> Unit)? = null
    ): Result<AudioPcmData> {
        val hasAny = vocalPcm != null || beatPcm != null || chordPcm != null ||
                bassPcm != null || drumPcm != null || melodyPcm != null ||
                padPcm != null || transitionPcm != null

        if (!hasAny) {
            return Result.failure(IllegalArgumentException("Tidak ada trek audio untuk di-mix."))
        }

        onProgress?.invoke(0.05f, "Menghitung parameter mixing multi-bus & gain staging...")

        val isAnySolo = params.vocalSettings.isSolo ||
                params.beatSettings.isSolo ||
                params.drumSettings.isSolo ||
                params.bassSettings.isSolo ||
                params.chordSettings.isSolo ||
                params.melodySettings.isSolo ||
                params.padSettings.isSolo ||
                params.fxSettings.isSolo

        // 1. Gain per trek
        var vocalGain = params.vocalSettings.computeEffectiveGain(isAnySolo) * params.vocalBusSettings.effectiveGain
        var beatGain = params.beatSettings.computeEffectiveGain(isAnySolo) * params.beatBusSettings.effectiveGain
        var drumGain = params.drumSettings.computeEffectiveGain(isAnySolo) * params.drumBusSettings.effectiveGain
        var bassGain = params.bassSettings.computeEffectiveGain(isAnySolo) * params.bassBusSettings.effectiveGain

        // Music Bus mengontrol Akor, Melodi, Pad, dan FX
        val musicBusMultiplier = params.musicBusSettings.effectiveGain
        var chordGain = params.chordSettings.computeEffectiveGain(isAnySolo) * musicBusMultiplier
        var melodyGain = params.melodySettings.computeEffectiveGain(isAnySolo) * musicBusMultiplier
        var padGain = params.padSettings.computeEffectiveGain(isAnySolo) * musicBusMultiplier
        var fxGain = params.fxSettings.computeEffectiveGain(isAnySolo) * musicBusMultiplier

        // 2. AUTO GAIN STAGING: Hierarki Vokal > Kick/Drum > Bass > Music
        if (params.isAutoMixEnabled) {
            if (vocalGain > 0.0f) {
                // Skala instrumen pengiring agar vokal memiliki ruang yang bersih
                val totalMusic = chordGain + melodyGain + padGain + fxGain
                if (totalMusic > 0.0f) {
                    val maxAllowedMusic = vocalGain * 1.5f
                    if (totalMusic > maxAllowedMusic) {
                        val musicScale = maxAllowedMusic / totalMusic
                        chordGain *= musicScale
                        melodyGain *= musicScale
                        padGain *= musicScale
                        fxGain *= musicScale
                    }
                }
            }

            // Normalisasi gain staging awal untuk mencegah penumpukan clipping sebelum mastering
            val totalTrackEnergy = vocalGain + beatGain + drumGain + bassGain + chordGain + melodyGain + padGain + fxGain
            if (totalTrackEnergy > 3.2f) {
                val stagingScale = 3.2f / totalTrackEnergy
                vocalGain *= stagingScale
                beatGain *= stagingScale
                drumGain *= stagingScale
                bassGain *= stagingScale
                chordGain *= stagingScale
                melodyGain *= stagingScale
                padGain *= stagingScale
                fxGain *= stagingScale
            }
        }

        val totalFrames = maxOf(
            vocalPcm?.totalFrames ?: 0,
            beatPcm?.totalFrames ?: 0,
            chordPcm?.totalFrames ?: 0,
            bassPcm?.totalFrames ?: 0,
            drumPcm?.totalFrames ?: 0,
            melodyPcm?.totalFrames ?: 0,
            padPcm?.totalFrames ?: 0,
            transitionPcm?.totalFrames ?: 0
        )

        if (totalFrames <= 0) {
            return Result.failure(IllegalStateException("Durasi audio tidak valid untuk di-mix."))
        }

        val channels = 2
        val mixedSamples = FloatArray(totalFrames * channels)
        val vocalSamples = vocalPcm?.samples
        val beatSamples = beatPcm?.samples
        val chordSamples = chordPcm?.samples
        val bassSamples = bassPcm?.samples
        val drumSamples = drumPcm?.samples
        val melodySamples = melodyPcm?.samples
        val padSamples = padPcm?.samples
        val transSamples = transitionPcm?.samples

        val masterGain = params.masterGain.coerceIn(0.0f, 1.5f)

        onProgress?.invoke(0.20f, "Menggabungkan sinyal audio melalui Mix Bus...")

        var maxPeakBeforeLimit = 0.0f
        val blockFrames = 16384
        val tempBlockSize = blockFrames * channels
        val chunkBuffer = AudioBufferPool.acquire(tempBlockSize)
        var current = 0

        try {
            while (current < totalFrames) {
                val count = minOf(blockFrames, totalFrames - current)
                val chunkEnd = current + count

                for (f in 0 until count) {
                    val frameIdx = current + f
                    val iL = frameIdx * 2
                    val iR = frameIdx * 2 + 1

                    val vL = if (vocalSamples != null && iL < vocalSamples.size) vocalSamples[iL] else 0.0f
                    val vR = if (vocalSamples != null && iR < vocalSamples.size) vocalSamples[iR] else 0.0f

                    val bL = if (beatSamples != null && iL < beatSamples.size) beatSamples[iL] else 0.0f
                    val bR = if (beatSamples != null && iR < beatSamples.size) beatSamples[iR] else 0.0f

                    val drL = if (drumSamples != null && iL < drumSamples.size) drumSamples[iL] else 0.0f
                    val drR = if (drumSamples != null && iR < drumSamples.size) drumSamples[iR] else 0.0f

                    val bassL = if (bassSamples != null && iL < bassSamples.size) bassSamples[iL] else 0.0f
                    val bassR = if (bassSamples != null && iR < bassSamples.size) bassSamples[iR] else 0.0f

                    val cL = if (chordSamples != null && iL < chordSamples.size) chordSamples[iL] else 0.0f
                    val cR = if (chordSamples != null && iR < chordSamples.size) chordSamples[iR] else 0.0f

                    val mL = if (melodySamples != null && iL < melodySamples.size) melodySamples[iL] else 0.0f
                    val mR = if (melodySamples != null && iR < melodySamples.size) melodySamples[iR] else 0.0f

                    val pL = if (padSamples != null && iL < padSamples.size) padSamples[iL] else 0.0f
                    val pR = if (padSamples != null && iR < padSamples.size) padSamples[iR] else 0.0f

                    val tL = if (transSamples != null && iL < transSamples.size) transSamples[iL] else 0.0f
                    val tR = if (transSamples != null && iR < transSamples.size) transSamples[iR] else 0.0f

                    // 1. Bus Summing:
                    // VOCAL BUS
                    val busVocalL = vL * vocalGain
                    val busVocalR = vR * vocalGain

                    // BEAT BUS
                    val busBeatL = bL * beatGain
                    val busBeatR = bR * beatGain

                    // DRUM BUS
                    val busDrumL = drL * drumGain
                    val busDrumR = drR * drumGain

                    // BASS BUS
                    val busBassL = bassL * bassGain
                    val busBassR = bassR * bassGain

                    // MUSIC BUS (Chord + Melody + Pad + FX)
                    val busMusicL = cL * chordGain + mL * melodyGain + pL * padGain + tL * fxGain
                    val busMusicR = cR * chordGain + mR * melodyGain + pR * padGain + tR * fxGain

                    // MASTER BUS SUM
                    val mixedL = (busVocalL + busBeatL + busDrumL + busBassL + busMusicL) * masterGain
                    val mixedR = (busVocalR + busBeatR + busDrumR + busBassR + busMusicR) * masterGain

                    val chunkIdxL = f * 2
                    val chunkIdxR = f * 2 + 1
                    chunkBuffer[chunkIdxL] = mixedL
                    chunkBuffer[chunkIdxR] = mixedR

                    val absL = abs(mixedL)
                    val absR = abs(mixedR)
                    if (absL > maxPeakBeforeLimit) maxPeakBeforeLimit = absL
                    if (absR > maxPeakBeforeLimit) maxPeakBeforeLimit = absR
                }

                val destOffset = current * channels
                System.arraycopy(chunkBuffer, 0, mixedSamples, destOffset, count * channels)

                current += count

                val progress = 0.20f + 0.55f * (current.toFloat() / totalFrames.toFloat())
                if (current % (blockFrames * 4) == 0 || current >= totalFrames) {
                    onProgress?.invoke(progress, "Proses mixing multi-bus chunk (${(progress * 100).toInt()}%)...")
                }
            }
        } finally {
            AudioBufferPool.release(chunkBuffer)
        }

        onProgress?.invoke(0.80f, "Pemeriksaan headroom pre-master (-1.4 dBFS)...")

        // Pre-master headroom protection: pastikan tidak ada clipping sebelum masuk ke AutoMasteringEngine
        if (maxPeakBeforeLimit > PRE_MASTER_HEADROOM_PEAK) {
            val attenuation = PRE_MASTER_HEADROOM_PEAK / maxPeakBeforeLimit
            for (i in mixedSamples.indices) {
                mixedSamples[i] = (mixedSamples[i] * attenuation).coerceIn(-PRE_MASTER_HEADROOM_PEAK, PRE_MASTER_HEADROOM_PEAK)
            }
        } else {
            for (i in mixedSamples.indices) {
                mixedSamples[i] = mixedSamples[i].coerceIn(-1.0f, 1.0f)
            }
        }

        onProgress?.invoke(1.0f, "Mixing Mix Bus selesai.")

        val resultPcm = AudioPcmData(
            samples = mixedSamples,
            sampleRate = 44100,
            channels = channels
        )

        return Result.success(resultPcm)
    }
}
