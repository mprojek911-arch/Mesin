package com.autoremix.djslow.engine.synth

import com.autoremix.djslow.engine.core.ArrangementEngine
import com.autoremix.djslow.engine.music.Chord
import com.autoremix.djslow.engine.pcm.AudioPcmData
import com.autoremix.djslow.engine.structure.SongSectionType
import com.autoremix.djslow.engine.timeline.MasterTimeline
import com.autoremix.djslow.engine.timeline.TimelineEvent
import kotlin.math.PI
import kotlin.math.sin

/**
 * Preset Instrumen Akor.
 */
enum class ChordSynthPreset(
    val label: String,
    val primaryWaveform: Waveform,
    val secondaryWaveform: Waveform,
    val secondaryDetuneCents: Float,
    val adsr: AdsrParams,
    val octave: Int = 4
) {
    PIANO(
        label = "Piano",
        primaryWaveform = Waveform.TRIANGLE,
        secondaryWaveform = Waveform.SINE,
        secondaryDetuneCents = 2.0f,
        adsr = AdsrParams(attackSeconds = 0.015f, decaySeconds = 0.40f, sustainLevel = 0.35f, releaseSeconds = 0.25f),
        octave = 4
    ),
    SOFT_PIANO(
        label = "Soft Piano",
        primaryWaveform = Waveform.SINE,
        secondaryWaveform = Waveform.TRIANGLE,
        secondaryDetuneCents = 1.0f,
        adsr = AdsrParams(attackSeconds = 0.04f, decaySeconds = 0.50f, sustainLevel = 0.40f, releaseSeconds = 0.35f),
        octave = 4
    ),
    PAD(
        label = "Warm Pad",
        primaryWaveform = Waveform.SAW,
        secondaryWaveform = Waveform.SINE,
        secondaryDetuneCents = 4.0f,
        adsr = AdsrParams(attackSeconds = 0.25f, decaySeconds = 0.60f, sustainLevel = 0.75f, releaseSeconds = 0.45f),
        octave = 4
    ),
    SOFT_SYNTH(
        label = "Soft Synth",
        primaryWaveform = Waveform.SQUARE,
        secondaryWaveform = Waveform.TRIANGLE,
        secondaryDetuneCents = 3.0f,
        adsr = AdsrParams(attackSeconds = 0.03f, decaySeconds = 0.30f, sustainLevel = 0.55f, releaseSeconds = 0.20f),
        octave = 4
    );

    val displayName: String get() = label
}

/**
 * Chord Synthesizer Engine:
 * Menghasilkan audio PCM Float32 stereo nyata untuk progresi akor
 * berdasarkan Master Timeline dan Preset Instrumen sintetis.
 */
object ChordSynthEngine {

    /**
     * Merender keseluruhan progresi akor pada Master Timeline ke buffer PCM Float32 Stereo.
     */
    fun renderProgressionPcm(
        timeline: MasterTimeline,
        preset: ChordSynthPreset = ChordSynthPreset.SOFT_PIANO,
        volume: Float = 0.75f,
        arrangement: ArrangementEngine.FullArrangement? = null,
        onProgress: ((Float, String) -> Unit)? = null
    ): AudioPcmData {
        val totalFrames = timeline.totalFrames.toInt()
        val sampleRate = timeline.sampleRate
        val channels = 2
        val samples = FloatArray(totalFrames * channels)

        if (totalFrames <= 0 || timeline.chordEvents.isEmpty()) {
            return AudioPcmData(samples, sampleRate, channels)
        }

        onProgress?.invoke(0.1f, "Mempersiapkan synthesizer akor...")

        val totalEvents = timeline.chordEvents.size
        timeline.chordEvents.forEachIndexed { eventIndex, event ->
            val eventStartFrame = event.startSample.toInt()
            val eventEndFrame = minOf(event.endSample.toInt(), totalFrames)
            val eventFrames = eventEndFrame - eventStartFrame

            val sec = arrangement?.getSectionForBar(event.barIndex)
            val secVolume = when (sec?.sectionType) {
                SongSectionType.DROP, SongSectionType.MAIN_DROP, SongSectionType.PEAK, SongSectionType.FINAL_DROP -> volume * 1.0f
                SongSectionType.BREAK, SongSectionType.BREAKDOWN -> volume * 0.65f // Soft chord (BAGIAN D)
                SongSectionType.BUILD_UP, SongSectionType.BUILD_UP_2, SongSectionType.FINAL_BUILD -> volume * 0.80f
                SongSectionType.PRE_DROP -> volume * 0.60f
                SongSectionType.INTRO, SongSectionType.OUTRO -> volume * 0.60f
                else -> volume
            }

            if (eventFrames > 0 && eventStartFrame < totalFrames) {
                renderSingleChordIntoBuffer(
                    chord = event.chord,
                    startFrame = eventStartFrame,
                    numFrames = eventFrames,
                    targetSamples = samples,
                    channels = channels,
                    sampleRate = sampleRate,
                    preset = preset,
                    volume = secVolume
                )
            }

            if (eventIndex % 4 == 0) {
                val prog = 0.1f + 0.85f * (eventIndex.toFloat() / totalEvents)
                onProgress?.invoke(prog, "Merender akor ${event.chord.name} (Bar ${event.barIndex + 1})...")
            }
        }

        onProgress?.invoke(1.0f, "Sintesis audio akor selesai.")
        return AudioPcmData(samples, sampleRate, channels)
    }

    /**
     * Merender satu akor tunggal ke buffer audio baru (misalnya untuk pratinjau instan).
     */
    fun renderSingleChordPreview(
        chord: Chord,
        durationSeconds: Float = 2.0f,
        preset: ChordSynthPreset = ChordSynthPreset.SOFT_PIANO,
        volume: Float = 0.8f,
        sampleRate: Int = 44100
    ): AudioPcmData {
        val totalFrames = (durationSeconds * sampleRate).toInt()
        val channels = 2
        val samples = FloatArray(totalFrames * channels)

        renderSingleChordIntoBuffer(
            chord = chord,
            startFrame = 0,
            numFrames = totalFrames,
            targetSamples = samples,
            channels = channels,
            sampleRate = sampleRate,
            preset = preset,
            volume = volume
        )

        return AudioPcmData(samples, sampleRate, channels)
    }

    /**
     * Mensintesis suara akor polifonik langsung ke buffer array sampel target.
     */
    private fun renderSingleChordIntoBuffer(
        chord: Chord,
        startFrame: Int,
        numFrames: Int,
        targetSamples: FloatArray,
        channels: Int,
        sampleRate: Int,
        preset: ChordSynthPreset,
        volume: Float
    ) {
        val frequencies = chord.getFrequenciesHz(preset.octave)
        if (frequencies.isEmpty()) return

        val adsr = AdsrEnvelope(preset.adsr, numFrames.toLong(), sampleRate)
        val voiceWeight = (volume / (frequencies.size * 1.15f)).coerceIn(0.05f, 1.0f)

        // Buat osilator untuk setiap nada dalam akor
        val oscillators = frequencies.map { freq ->
            Pair(
                SynthOscillator(preset.primaryWaveform, freq, sampleRate),
                // Osilator kedua dengan sedikit detune untuk rasa stereo lebar dan kehangatan
                SynthOscillator(
                    preset.secondaryWaveform,
                    freq * (1.0f + (preset.secondaryDetuneCents / 1200.0f)),
                    sampleRate
                )
            )
        }

        for (f in 0 until numFrames) {
            val frameIndex = startFrame + f
            val sampleIdxL = frameIndex * channels
            val sampleIdxR = frameIndex * channels + 1

            if (sampleIdxR >= targetSamples.size) break

            val envelopeGain = adsr.getGain(f.toLong())
            var mixedL = 0.0f
            var mixedR = 0.0f

            for ((osc1, osc2) in oscillators) {
                val s1 = osc1.nextSample()
                val s2 = osc2.nextSample()

                // Panning stereo ringan (s1 dominan kiri, s2 dominan kanan)
                mixedL += (s1 * 0.65f + s2 * 0.35f) * voiceWeight * envelopeGain
                mixedR += (s1 * 0.35f + s2 * 0.65f) * voiceWeight * envelopeGain
            }

            targetSamples[sampleIdxL] += mixedL
            targetSamples[sampleIdxR] += mixedR
        }
    }
}
