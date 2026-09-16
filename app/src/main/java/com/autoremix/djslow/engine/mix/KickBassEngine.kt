package com.autoremix.djslow.engine.mix

import com.autoremix.djslow.engine.drum.DrumEvent
import com.autoremix.djslow.engine.drum.DrumSoundType
import com.autoremix.djslow.engine.pcm.AudioPcmData
import kotlin.math.exp
import kotlin.math.max

/**
 * Kick Bass Engine:
 * Menyelaraskan interaksi antara Kick dan Sub-Bass DJ Slow:
 * - Sidechain Compression Ducking: Ketika kick memukul, gain bass diturunkan seketika (ducking)
 *   lalu kembali pulih (release curve eksponensial).
 * - Menghindari phase cancellation dan distorsi pada rentang frekuensi sub 30 - 120 Hz.
 * - Menghasilkan pukulan kick yang bersih (punchy) berdampingan dengan sub-bass yang bulat.
 */
object KickBassEngine {

    /**
     * Menerapkan ducking sidechain pada buffer PCM bass berdasarkan daftar event kick.
     */
    fun applySidechainDucking(
        bassPcm: AudioPcmData,
        drumEvents: List<DrumEvent>,
        sampleRate: Int,
        duckingDepth: Float = 0.65f, // Bass turun hingga 35% saat kick memukul
        releaseTimeMs: Float = 140f   // Waktu pemulihan bass setelah kick
    ): AudioPcmData {
        val samples = bassPcm.samples
        val channels = bassPcm.channels
        val totalFrames = bassPcm.totalFrames

        val kickEvents = drumEvents.filter { it.soundType == DrumSoundType.KICK }
        if (kickEvents.isEmpty()) return bassPcm

        val releaseSamples = ((releaseTimeMs / 1000f) * sampleRate).toInt().coerceAtLeast(1)
        val output = samples.copyOf()

        for (kick in kickEvents) {
            val startSample = kick.sampleOffset
            val startIdx = (startSample * channels).toInt()
            if (startIdx >= output.size) continue

            val vel = kick.velocity.coerceIn(0.2f, 1.0f)
            val effectiveDepth = duckingDepth * vel

            for (i in 0 until releaseSamples) {
                val outIdx = startIdx + i * channels
                if (outIdx + 1 >= output.size) break

                val t = i.toDouble() / releaseSamples
                // Kurva pemulihan (1 - exp(-t * 4))
                val recovery = 1.0 - exp(-t * 4.0)
                val gain = (1.0f - effectiveDepth * (1.0f - recovery.toFloat())).coerceIn(0.1f, 1.0f)

                output[outIdx] *= gain
                output[outIdx + 1] *= gain
            }
        }

        return AudioPcmData(
            samples = output,
            sampleRate = sampleRate,
            channels = channels
        )
    }
}
