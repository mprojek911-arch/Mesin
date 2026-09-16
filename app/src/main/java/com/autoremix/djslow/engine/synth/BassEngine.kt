package com.autoremix.djslow.engine.synth

import com.autoremix.djslow.engine.music.Chord
import com.autoremix.djslow.engine.music.MusicKey
import com.autoremix.djslow.engine.music.PitchClass
import com.autoremix.djslow.engine.pcm.AudioPcmData
import com.autoremix.djslow.engine.timeline.MasterTimeline
import com.autoremix.djslow.engine.timeline.TimelineEvent
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.tanh

/**
 * Tipe Pola Bass yang didukung.
 */
enum class BassPatternType(val label: String, val description: String) {
    DJ_SLOW_BASS("DJ Slow Bass", "Ketukan sub-bass khas DJ Slow: punch di ketukan 1, bounce sinkopasi di 2.5 dan 4"),
    ROOT("Root", "Nada dasar stabil pada ketukan kuat"),
    ROOT_FIFTH("Root + Fifth", "Variasi nada dasar dan nada kelima bergantian"),
    OCTAVE("Octave Jump", "Lompatan oktaf rendah ke tinggi memberi dinamika ritmik"),
    PASSING_NOTE("Passing Note", "Nada transisi tangga nada yang menyambung mulus ke akor berikutnya"),
    SYNCOPATED("Syncopated Funk", "Pola ketukan gantung di antara hit beat");

    val displayName: String get() = label
}

/**
 * Bass Engine:
 * Menghasilkan pola bass musikal mengikuti Key, Tangga Nada, Akor, BPM, dan Beat Grid.
 * Menghasilkan audio PCM Float32 stereo nyata dengan karakter bass bulat dan punchy.
 */
object BassEngine {

    /**
     * Membangun daftar BassEvent untuk seluruh Master Timeline berdasarkan pola terpilih.
     */
    fun generateBassEvents(
        timeline: MasterTimeline,
        key: MusicKey,
        patternType: BassPatternType = BassPatternType.DJ_SLOW_BASS,
        baseOctave: Int = 2 // Oktaf bass C2 (MIDI 36 ~ 65.4 Hz)
    ): List<TimelineEvent.BassEvent> {
        val totalBars = maxOf(1, timeline.totalBars)
        val events = ArrayList<TimelineEvent.BassEvent>()

        for (bar in 0 until totalBars) {
            // Ambil akor bar ini dari timeline
            val chordEvent = timeline.chordEvents.firstOrNull { it.barIndex == bar }
            val currentChord = chordEvent?.chord ?: Chord(key.tonic, com.autoremix.djslow.engine.music.ChordType.MINOR)

            // Akor bar berikutnya (untuk passing note)
            val nextChordEvent = timeline.chordEvents.firstOrNull { it.barIndex == bar + 1 }
            val nextChord = nextChordEvent?.chord ?: currentChord

            val rootMidi = 12 * (baseOctave + 1) + currentChord.root.semitone
            val fifthMidi = rootMidi + 7
            val octaveMidi = rootMidi + 12
            val nextRootMidi = 12 * (baseOctave + 1) + nextChord.root.semitone
            val passingMidi = if (nextRootMidi > rootMidi) rootMidi + 2 else rootMidi - 1

            when (patternType) {
                BassPatternType.DJ_SLOW_BASS -> {
                    // Ciri khas DJ Slow Indonesia:
                    // Beat 0: Punch root kuat (durasi 1.25 beat)
                    // Beat 2.5: Sinkopasi bouncy (durasi 0.75 beat)
                    // Beat 3.5: Passing note atau fifth sebelum pergantian bar (durasi 0.5 beat)
                    events.add(createBassEvent(timeline, bar, 0.0f, 1.25f, rootMidi, velocity = 0.95f))
                    events.add(createBassEvent(timeline, bar, 2.5f, 0.75f, rootMidi, velocity = 0.85f))
                    val bouncePitch = if (bar % 2 == 1) fifthMidi else passingMidi
                    events.add(createBassEvent(timeline, bar, 3.5f, 0.5f, bouncePitch, velocity = 0.80f))
                }

                BassPatternType.ROOT -> {
                    // Beat 0 dan Beat 2
                    events.add(createBassEvent(timeline, bar, 0.0f, 1.8f, rootMidi, velocity = 0.90f))
                    events.add(createBassEvent(timeline, bar, 2.0f, 1.8f, rootMidi, velocity = 0.85f))
                }

                BassPatternType.ROOT_FIFTH -> {
                    // Beat 0: Root, Beat 2: Fifth
                    events.add(createBassEvent(timeline, bar, 0.0f, 1.8f, rootMidi, velocity = 0.92f))
                    events.add(createBassEvent(timeline, bar, 2.0f, 1.8f, fifthMidi, velocity = 0.85f))
                }

                BassPatternType.OCTAVE -> {
                    // Beat 0: Low Root, Beat 1.5: High Octave, Beat 2.0: Low Root, Beat 3.0: High Octave
                    events.add(createBassEvent(timeline, bar, 0.0f, 1.2f, rootMidi, velocity = 0.92f))
                    events.add(createBassEvent(timeline, bar, 1.5f, 0.4f, octaveMidi, velocity = 0.78f))
                    events.add(createBassEvent(timeline, bar, 2.0f, 0.8f, rootMidi, velocity = 0.85f))
                    events.add(createBassEvent(timeline, bar, 3.0f, 0.8f, octaveMidi, velocity = 0.82f))
                }

                BassPatternType.PASSING_NOTE -> {
                    // Beat 0: Root, Beat 2: Fifth, Beat 3.5: Passing note ke bar berikutnya
                    events.add(createBassEvent(timeline, bar, 0.0f, 1.8f, rootMidi, velocity = 0.90f))
                    events.add(createBassEvent(timeline, bar, 2.0f, 1.3f, fifthMidi, velocity = 0.82f))
                    events.add(createBassEvent(timeline, bar, 3.5f, 0.5f, passingMidi, velocity = 0.80f))
                }

                BassPatternType.SYNCOPATED -> {
                    // Beat 0: Root, Beat 1.5: Root, Beat 2.75: Fifth
                    events.add(createBassEvent(timeline, bar, 0.0f, 1.2f, rootMidi, velocity = 0.90f))
                    events.add(createBassEvent(timeline, bar, 1.5f, 0.8f, rootMidi, velocity = 0.82f))
                    events.add(createBassEvent(timeline, bar, 2.75f, 1.0f, fifthMidi, velocity = 0.85f))
                }
            }
        }

        return events
    }

    private fun createBassEvent(
        timeline: MasterTimeline,
        bar: Int,
        beatInBar: Float,
        durationBeats: Float,
        midiNote: Int,
        velocity: Float
    ): TimelineEvent.BassEvent {
        val absoluteBeat = bar * timeline.beatsPerBar + beatInBar
        val startSample = timeline.beatToSample(absoluteBeat)
        val endSample = timeline.beatToSample(absoluteBeat + durationBeats)
        val freq = Chord.midiToFrequency(midiNote)

        return TimelineEvent.BassEvent(
            barIndex = bar,
            beatIndexInBar = beatInBar,
            durationBeats = durationBeats,
            startSample = startSample,
            endSample = endSample,
            midiNote = midiNote,
            frequencyHz = freq,
            velocity = velocity
        )
    }

    /**
     * Merender daftar event Bass ke buffer PCM Float32 Stereo 44.1 kHz.
     */
    fun renderBassPcm(
        timeline: MasterTimeline,
        bassEvents: List<TimelineEvent.BassEvent>,
        volume: Float = 0.85f,
        onProgress: ((Float, String) -> Unit)? = null
    ): AudioPcmData {
        val totalFrames = timeline.totalFrames.toInt()
        val sampleRate = timeline.sampleRate
        val channels = 2
        val samples = FloatArray(totalFrames * channels)

        if (totalFrames <= 0 || bassEvents.isEmpty()) {
            return AudioPcmData(samples, sampleRate, channels)
        }

        onProgress?.invoke(0.1f, "Mempersiapkan synthesizer bass...")

        val totalEvents = bassEvents.size
        bassEvents.forEachIndexed { index, event ->
            val startFrame = event.startSample.toInt()
            val endFrame = minOf(event.endSample.toInt(), totalFrames)
            val numFrames = endFrame - startFrame

            if (numFrames > 0 && startFrame < totalFrames) {
                renderSingleBassHit(
                    event = event,
                    startFrame = startFrame,
                    numFrames = numFrames,
                    targetSamples = samples,
                    channels = channels,
                    sampleRate = sampleRate,
                    masterVolume = volume
                )
            }

            if (index % 8 == 0) {
                val prog = 0.1f + 0.85f * (index.toFloat() / totalEvents)
                onProgress?.invoke(prog, "Merender bass track (${index + 1}/$totalEvents)...")
            }
        }

        onProgress?.invoke(1.0f, "Sintesis bass selesai.")
        return AudioPcmData(samples, sampleRate, channels)
    }

    /**
     * Sintesis satu pukulan nada bass:
     * - Sub-bass sinusoidal dalam
     * - Sedikit pitch-envelope drop di transient untuk 'kick/punch' instan
     * - Saturasi harmonik hangat (soft saturation tanh) agar terdengar jelas di speaker HP
     */
    private fun renderSingleBassHit(
        event: TimelineEvent.BassEvent,
        startFrame: Int,
        numFrames: Int,
        targetSamples: FloatArray,
        channels: Int,
        sampleRate: Int,
        masterVolume: Float
    ) {
        val baseFreq = event.frequencyHz
        val hitVolume = event.velocity * masterVolume

        // ADSR bass: Serangan cepat 8ms, decay 180ms, sustain 65%, release 100ms
        val adsr = AdsrEnvelope(
            params = AdsrParams(
                attackSeconds = 0.008f,
                decaySeconds = 0.18f,
                sustainLevel = 0.65f,
                releaseSeconds = 0.10f
            ),
            totalFrames = numFrames.toLong(),
            sampleRate = sampleRate
        )

        var phaseSub = 0.0
        var phaseHarmonic = 0.0
        val pitchEnvelopeFrames = (0.045f * sampleRate).toInt() // 45ms pitch drop untuk punch kick bass

        for (f in 0 until numFrames) {
            val frameIndex = startFrame + f
            val sampleIdxL = frameIndex * channels
            val sampleIdxR = frameIndex * channels + 1

            if (sampleIdxR >= targetSamples.size) break

            // Pitch drop singkat di awal nada untuk transient punch khas DJ Slow
            val pitchMultiplier = if (f < pitchEnvelopeFrames) {
                1.0f + 0.8f * (1.0f - (f.toFloat() / pitchEnvelopeFrames))
            } else {
                1.0f
            }

            val currentFreq = baseFreq * pitchMultiplier
            val phaseIncSub = (2.0 * PI * currentFreq) / sampleRate
            val phaseIncHarmonic = (2.0 * PI * currentFreq * 2.0) / sampleRate

            val subSample = sin(phaseSub).toFloat()
            // Harmonik kedua untuk kehangatan frekuensi menengah
            val harmSample = (sin(phaseHarmonic) * 0.25).toFloat()

            phaseSub += phaseIncSub
            if (phaseSub >= 2.0 * PI) phaseSub -= 2.0 * PI

            phaseHarmonic += phaseIncHarmonic
            if (phaseHarmonic >= 2.0 * PI) phaseHarmonic -= 2.0 * PI

            val rawSample = subSample + harmSample
            // Saturasi analog hangat
            val saturated = tanh((rawSample * 1.35).toDouble()).toFloat()

            val envelope = adsr.getGain(f.toLong())
            val sampleOut = saturated * hitVolume * envelope

            // Bass diletakkan di tengah (mono center)
            targetSamples[sampleIdxL] += sampleOut
            targetSamples[sampleIdxR] += sampleOut
        }
    }
}
