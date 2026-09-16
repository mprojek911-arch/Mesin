package com.autoremix.djslow.engine.mix

import com.autoremix.djslow.engine.pcm.AudioPcmData
import kotlin.math.abs

/**
 * Mix Engine untuk menggabungkan trek Vokal, Beat, Akor Synth, dan Bass Synth ke Master Bus.
 * Fitur:
 * - Volume, Mute, Solo untuk setiap trek (Vokal, Beat, Chord, Bass)
 * - Auto Mix cerdas: Vokal dominan, elemen musik (Beat, Chord, Bass) disesuaikan seimbang
 * - Master Gain & Peak Limiting dengan headroom aman (0.95f / -0.5 dB untuk mencegah clipping)
 */
object MixEngine {

    const val SAFE_HEADROOM_PEAK = 0.95f // -0.5 dB Headroom untuk proteksi clipping

    data class MixParams(
        val vocalSettings: MixTrackSettings = MixTrackSettings(volume = 1.0f),
        val beatSettings: MixTrackSettings = MixTrackSettings(volume = 0.8f),
        val chordSettings: MixTrackSettings = MixTrackSettings(volume = 0.70f),
        val bassSettings: MixTrackSettings = MixTrackSettings(volume = 0.85f),
        val masterGain: Float = 0.90f,
        val isAutoMixEnabled: Boolean = true
    )

    /**
     * Melakukan mixing antara buffer Vokal, Beat, Chord Synth, dan Bass Synth PCM.
     */
    fun mix(
        vocalPcm: AudioPcmData?,
        beatPcm: AudioPcmData?,
        chordPcm: AudioPcmData? = null,
        bassPcm: AudioPcmData? = null,
        params: MixParams = MixParams(),
        onProgress: ((Float, String) -> Unit)? = null
    ): Result<AudioPcmData> {
        if (vocalPcm == null && beatPcm == null && chordPcm == null && bassPcm == null) {
            return Result.failure(IllegalArgumentException("Tidak ada trek audio untuk di-mix."))
        }

        onProgress?.invoke(0.05f, "Menghitung parameter mixing 4-track...")

        // Evaluasi status Solo global
        val isAnySolo = params.vocalSettings.isSolo ||
                params.beatSettings.isSolo ||
                params.chordSettings.isSolo ||
                params.bassSettings.isSolo

        var vocalGain = params.vocalSettings.computeEffectiveGain(isAnySolo)
        var beatGain = params.beatSettings.computeEffectiveGain(isAnySolo)
        var chordGain = params.chordSettings.computeEffectiveGain(isAnySolo)
        var bassGain = params.bassSettings.computeEffectiveGain(isAnySolo)

        // Terapkan Auto Mix jika diaktifkan: Vokal dibuat dominan, musik pengiring diseimbangkan
        if (params.isAutoMixEnabled) {
            val totalAccompanimentGain = beatGain + chordGain + bassGain
            if (vocalGain > 0.0f && totalAccompanimentGain > 0.0f) {
                // Skala musik pengiring agar vokal tetap jernih dan vokal berdiri di depan
                val maxAccompaniment = vocalGain * 1.25f
                if (totalAccompanimentGain > maxAccompaniment) {
                    val scale = maxAccompaniment / totalAccompanimentGain
                    beatGain *= scale
                    chordGain *= scale
                    bassGain *= scale
                }
            }
        }

        val vocalFrames = vocalPcm?.totalFrames ?: 0
        val beatFrames = beatPcm?.totalFrames ?: 0
        val chordFrames = chordPcm?.totalFrames ?: 0
        val bassFrames = bassPcm?.totalFrames ?: 0
        val totalFrames = maxOf(vocalFrames, beatFrames, chordFrames, bassFrames)

        if (totalFrames <= 0) {
            return Result.failure(IllegalStateException("Durasi audio tidak valid untuk di-mix."))
        }

        val channels = 2
        val mixedSamples = FloatArray(totalFrames * channels)
        val vocalSamples = vocalPcm?.samples
        val beatSamples = beatPcm?.samples
        val chordSamples = chordPcm?.samples
        val bassSamples = bassPcm?.samples

        val masterGain = params.masterGain.coerceIn(0.0f, 1.5f)

        onProgress?.invoke(0.20f, "Menggabungkan sinyal audio Vokal, Beat, Chord & Bass...")

        var maxPeakBeforeLimit = 0.0f
        val chunkSize = 44100 // Lapor progres berkala tiap ~1 detik audio
        var framesProcessed = 0

        for (f in 0 until totalFrames) {
            val iL = f * 2
            val iR = f * 2 + 1

            val vL = if (vocalSamples != null && iL < vocalSamples.size) vocalSamples[iL] else 0.0f
            val vR = if (vocalSamples != null && iR < vocalSamples.size) vocalSamples[iR] else 0.0f

            val bL = if (beatSamples != null && iL < beatSamples.size) beatSamples[iL] else 0.0f
            val bR = if (beatSamples != null && iR < beatSamples.size) beatSamples[iR] else 0.0f

            val cL = if (chordSamples != null && iL < chordSamples.size) chordSamples[iL] else 0.0f
            val cR = if (chordSamples != null && iR < chordSamples.size) chordSamples[iR] else 0.0f

            val bassL = if (bassSamples != null && iL < bassSamples.size) bassSamples[iL] else 0.0f
            val bassR = if (bassSamples != null && iR < bassSamples.size) bassSamples[iR] else 0.0f

            val mixedL = (vL * vocalGain + bL * beatGain + cL * chordGain + bassL * bassGain) * masterGain
            val mixedR = (vR * vocalGain + bR * beatGain + cR * chordGain + bassR * bassGain) * masterGain

            mixedSamples[iL] = mixedL
            mixedSamples[iR] = mixedR

            val absL = abs(mixedL)
            val absR = abs(mixedR)
            if (absL > maxPeakBeforeLimit) maxPeakBeforeLimit = absL
            if (absR > maxPeakBeforeLimit) maxPeakBeforeLimit = absR

            framesProcessed++
            if (framesProcessed % chunkSize == 0) {
                val progress = 0.20f + 0.55f * (framesProcessed.toFloat() / totalFrames.toFloat())
                onProgress?.invoke(progress, "Proses mixing PCM Float32...")
            }
        }

        onProgress?.invoke(0.80f, "Pemeriksaan peak & proteksi headroom limiting...")

        // Headroom / Peak Normalization: Jika level terlalu tinggi, turunkan gain agar tidak terjadi clipping
        if (maxPeakBeforeLimit > SAFE_HEADROOM_PEAK) {
            val attenuation = SAFE_HEADROOM_PEAK / maxPeakBeforeLimit
            for (i in mixedSamples.indices) {
                mixedSamples[i] = (mixedSamples[i] * attenuation).coerceIn(-1.0f, 1.0f)
            }
        } else {
            // Safety clamp
            for (i in mixedSamples.indices) {
                if (mixedSamples[i] > 1.0f) mixedSamples[i] = 1.0f
                else if (mixedSamples[i] < -1.0f) mixedSamples[i] = -1.0f
            }
        }

        onProgress?.invoke(1.0f, "Mixing selesai. Headroom aman.")

        val resultPcm = AudioPcmData(
            samples = mixedSamples,
            sampleRate = 44100,
            channels = channels
        )

        return Result.success(resultPcm)
    }
}
