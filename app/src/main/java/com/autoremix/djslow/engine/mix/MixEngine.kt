package com.autoremix.djslow.engine.mix

import com.autoremix.djslow.engine.pcm.AudioPcmData
import kotlin.math.abs

/**
 * Mix Engine untuk menggabungkan trek:
 * Vokal, Beat, Drum Synth, Bass Synth, Akor Synth, Melodi Synth, Pad Synth, dan FX Transisi.
 *
 * Fitur:
 * - Kontrol gain, mute, solo independen
 * - Auto Mix cerdas (Vokal dibuat dominan di depan, instrumen diselaraskan)
 * - Master Gain & Peak Limiting dengan headroom aman (0.95f / -0.5 dB untuk mencegah clipping)
 */
object MixEngine {

    const val SAFE_HEADROOM_PEAK = 0.95f // -0.5 dB Headroom untuk proteksi clipping

    data class MixParams(
        val vocalSettings: MixTrackSettings = MixTrackSettings(volume = 1.0f),
        val beatSettings: MixTrackSettings = MixTrackSettings(volume = 0.80f),
        val drumSettings: MixTrackSettings = MixTrackSettings(volume = 0.85f),
        val bassSettings: MixTrackSettings = MixTrackSettings(volume = 0.85f),
        val chordSettings: MixTrackSettings = MixTrackSettings(volume = 0.70f),
        val melodySettings: MixTrackSettings = MixTrackSettings(volume = 0.80f),
        val padSettings: MixTrackSettings = MixTrackSettings(volume = 0.75f),
        val masterGain: Float = 0.90f,
        val isAutoMixEnabled: Boolean = true
    )

    /**
     * Melakukan mixing antara seluruh layer audio PCM ke stereo Float32.
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

        onProgress?.invoke(0.05f, "Menghitung parameter mixing multi-track...")

        val isAnySolo = params.vocalSettings.isSolo ||
                params.beatSettings.isSolo ||
                params.drumSettings.isSolo ||
                params.bassSettings.isSolo ||
                params.chordSettings.isSolo ||
                params.melodySettings.isSolo ||
                params.padSettings.isSolo

        var vocalGain = params.vocalSettings.computeEffectiveGain(isAnySolo)
        var beatGain = params.beatSettings.computeEffectiveGain(isAnySolo)
        var drumGain = params.drumSettings.computeEffectiveGain(isAnySolo)
        var bassGain = params.bassSettings.computeEffectiveGain(isAnySolo)
        var chordGain = params.chordSettings.computeEffectiveGain(isAnySolo)
        var melodyGain = params.melodySettings.computeEffectiveGain(isAnySolo)
        var padGain = params.padSettings.computeEffectiveGain(isAnySolo)
        val transitionGain = if (isAnySolo) 0.0f else 0.80f

        // Auto Mix cerdas: vokal tetap menonjol dan tidak tertutup tumpukan layer
        if (params.isAutoMixEnabled && vocalGain > 0.0f) {
            val totalAccompaniment = beatGain + drumGain + bassGain + chordGain + melodyGain + padGain
            if (totalAccompaniment > 0.0f) {
                val maxAccompaniment = vocalGain * 1.6f
                if (totalAccompaniment > maxAccompaniment) {
                    val scale = maxAccompaniment / totalAccompaniment
                    beatGain *= scale
                    drumGain *= scale
                    bassGain *= scale
                    chordGain *= scale
                    melodyGain *= scale
                    padGain *= scale
                }
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
        val mixedSamples = FloatArray((totalFrames * channels).toInt())
        val vocalSamples = vocalPcm?.samples
        val beatSamples = beatPcm?.samples
        val chordSamples = chordPcm?.samples
        val bassSamples = bassPcm?.samples
        val drumSamples = drumPcm?.samples
        val melodySamples = melodyPcm?.samples
        val padSamples = padPcm?.samples
        val transSamples = transitionPcm?.samples

        val masterGain = params.masterGain.coerceIn(0.0f, 1.5f)

        onProgress?.invoke(0.20f, "Menggabungkan sinyal audio seluruh trek instrumen...")

        var maxPeakBeforeLimit = 0.0f
        val chunkSize = 44100
        var framesProcessed = 0

        for (f in 0 until totalFrames) {
            val iL = (f * 2).toInt()
            val iR = (f * 2 + 1).toInt()

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

            val mixedL = (vL * vocalGain +
                    bL * beatGain +
                    drL * drumGain +
                    bassL * bassGain +
                    cL * chordGain +
                    mL * melodyGain +
                    pL * padGain +
                    tL * transitionGain) * masterGain

            val mixedR = (vR * vocalGain +
                    bR * beatGain +
                    drR * drumGain +
                    bassR * bassGain +
                    cR * chordGain +
                    mR * melodyGain +
                    pR * padGain +
                    tR * transitionGain) * masterGain

            mixedSamples[iL] = mixedL
            mixedSamples[iR] = mixedR

            val absL = abs(mixedL)
            val absR = abs(mixedR)
            if (absL > maxPeakBeforeLimit) maxPeakBeforeLimit = absL
            if (absR > maxPeakBeforeLimit) maxPeakBeforeLimit = absR

            framesProcessed++
            if (framesProcessed % chunkSize == 0) {
                val progress = 0.20f + 0.55f * (framesProcessed.toFloat() / totalFrames.toFloat())
                onProgress?.invoke(progress, "Proses mixing multi-track PCM Float32...")
            }
        }

        onProgress?.invoke(0.80f, "Pemeriksaan peak & proteksi headroom limiting (-0.5 dB)...")

        if (maxPeakBeforeLimit > SAFE_HEADROOM_PEAK) {
            val attenuation = SAFE_HEADROOM_PEAK / maxPeakBeforeLimit
            for (i in mixedSamples.indices) {
                mixedSamples[i] = (mixedSamples[i] * attenuation).coerceIn(-SAFE_HEADROOM_PEAK, SAFE_HEADROOM_PEAK)
            }
        } else {
            for (i in mixedSamples.indices) {
                if (mixedSamples[i] > 1.0f) mixedSamples[i] = 1.0f
                else if (mixedSamples[i] < -1.0f) mixedSamples[i] = -1.0f
            }
        }

        onProgress?.invoke(1.0f, "Mixing selesai. Headroom aman & bebas clipping.")

        val resultPcm = AudioPcmData(
            samples = mixedSamples,
            sampleRate = 44100,
            channels = channels
        )

        return Result.success(resultPcm)
    }
}
