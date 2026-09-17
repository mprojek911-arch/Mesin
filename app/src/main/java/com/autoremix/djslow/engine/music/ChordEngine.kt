package com.autoremix.djslow.engine.music

import com.autoremix.djslow.engine.analysis.KeyDetector
import com.autoremix.djslow.engine.pcm.AudioPcmData
import com.autoremix.djslow.engine.timeline.MasterTimeline
import com.autoremix.djslow.engine.timeline.TimelineEvent
import kotlin.math.sqrt

/**
 * Chord Engine untuk menganalisis dan menghasilkan Progresi Akor musikal
 * yang selaras dengan Master Timeline, Beat Grid, dan Tangga Nada (Key Constraint).
 */
object ChordEngine {

    data class ProgressionResult(
        val key: MusicKey,
        val chordEvents: List<TimelineEvent.ChordEvent>,
        val chordProgressionSummary: List<Chord>,
        val confidence: Float,
        val isEstimated: Boolean,
        val description: String
    ) {
        val displayProgression: String
            get() = chordProgressionSummary.take(8).joinToString(" - ") { it.name }
    }

    /**
     * Menghasilkan atau menganalisis progresi akor untuk keseluruhan Master Timeline.
     */
    fun buildChordProgression(
        timeline: MasterTimeline,
        key: MusicKey,
        vocalPcm: AudioPcmData? = null
    ): ProgressionResult {
        val totalBars = maxOf(1, timeline.totalBars)
        val defaultProgression = getDefaultProgressionForKey(key)

        // Jika ada vokal PCM, coba deteksi chroma per-bar
        val barChords = ArrayList<Chord>(totalBars)
        var totalConfidence = 0.0f

        val hasVocal = vocalPcm != null && vocalPcm.totalFrames > 44100 && !vocalPcm.isSilent()

        for (bar in 0 until totalBars) {
            val fallbackChord = defaultProgression[bar % defaultProgression.size]

            if (hasVocal) {
                val startBeat = (bar * timeline.beatsPerBar).toFloat()
                val endBeat = ((bar + 1) * timeline.beatsPerBar).toFloat()
                val startSample = timeline.beatToSample(startBeat).toInt()
                val endSample = timeline.beatToSample(endBeat).toInt()

                val detectedChord = detectChordForBar(vocalPcm!!, startSample, endSample, key)
                if (detectedChord != null) {
                    barChords.add(detectedChord)
                    totalConfidence += 0.65f
                } else {
                    barChords.add(fallbackChord)
                    totalConfidence += 0.45f
                }
            } else {
                barChords.add(fallbackChord)
                totalConfidence += 0.50f
            }
        }

        val avgConfidence = (totalConfidence / totalBars).coerceIn(0.20f, 0.90f)
        val isEstimated = !hasVocal || avgConfidence < 0.55f

        // Buat TimelineEvent.ChordEvent per bar
        val chordEvents = ArrayList<TimelineEvent.ChordEvent>(totalBars)
        for (bar in 0 until totalBars) {
            val chord = barChords[bar]
            val startBeat = (bar * timeline.beatsPerBar).toFloat()
            val endBeat = ((bar + 1) * timeline.beatsPerBar).toFloat()
            val startSample = timeline.beatToSample(startBeat)
            val endSample = timeline.beatToSample(endBeat)

            chordEvents.add(
                TimelineEvent.ChordEvent(
                    barIndex = bar,
                    beatIndexInBar = 0.0f,
                    durationBeats = timeline.beatsPerBar.toFloat(),
                    startSample = startSample,
                    endSample = endSample,
                    chord = chord
                )
            )
        }

        // Ambil representasi 4-bar progression pattern
        val summary = if (barChords.size >= 4) {
            barChords.take(4)
        } else {
            defaultProgression
        }

        return ProgressionResult(
            key = key,
            chordEvents = chordEvents,
            chordProgressionSummary = summary,
            confidence = avgConfidence,
            isEstimated = isEstimated,
            description = if (isEstimated) "Perkiraan Progresi Akor Diatonik" else "Akor Terdeteksi Selaras Vokal"
        )
    }

    /**
     * Templat Progresi Akor Diatonik Populer / DJ Slow berdasarkan Tangga Nada.
     */
    fun getDefaultProgressionForKey(key: MusicKey): List<Chord> {
        val tonic = key.tonic.semitone
        return when (key.mode) {
            MusicMode.MINOR -> {
                // i - VI - III - VII (misal Am - F - C - G)
                listOf(
                    Chord(PitchClass.fromSemitone(tonic), ChordType.MINOR),
                    Chord(PitchClass.fromSemitone(tonic + 8), ChordType.MAJOR),
                    Chord(PitchClass.fromSemitone(tonic + 3), ChordType.MAJOR),
                    Chord(PitchClass.fromSemitone(tonic + 10), ChordType.MAJOR)
                )
            }
            MusicMode.MAJOR -> {
                // I - V - vi - IV (misal C - G - Am - F)
                listOf(
                    Chord(PitchClass.fromSemitone(tonic), ChordType.MAJOR),
                    Chord(PitchClass.fromSemitone(tonic + 7), ChordType.MAJOR),
                    Chord(PitchClass.fromSemitone(tonic + 9), ChordType.MINOR),
                    Chord(PitchClass.fromSemitone(tonic + 5), ChordType.MAJOR)
                )
            }
        }
    }

    /**
     * Menganalisis akor terbaik untuk rentang sampel suatu birama (bar) menggunakan template matching.
     */
    private fun detectChordForBar(
        pcm: AudioPcmData,
        startSample: Int,
        endSample: Int,
        key: MusicKey
    ): Chord? {
        val length = endSample - startSample
        if (length < 2048) return null

        val safeStartSample = startSample.coerceIn(0, pcm.samples.size - 1)
        val safeEndSample = endSample.coerceIn(safeStartSample, pcm.samples.size)
        val sampleCount = safeEndSample - safeStartSample
        if (sampleCount < 2048) return null

        val startFrame = safeStartSample / pcm.channels
        val frameCount = sampleCount / pcm.channels

        val chroma = KeyDetector.computeChromagram(
            samples = pcm.samples,
            channels = pcm.channels,
            sampleRate = pcm.sampleRate,
            startFrameOffset = startFrame,
            frameCount = frameCount
        )
        if (chroma.all { it <= 1e-5f }) return null

        // Kandidat akor: diutamakan akor diatonik dalam tangga nada
        val scaleNotes = key.getScalePitchClasses()
        val candidates = mutableListOf<Chord>()

        // Tambahkan akor dari skala tangga nada
        for (note in scaleNotes) {
            candidates.add(Chord(note, ChordType.MAJOR))
            candidates.add(Chord(note, ChordType.MINOR))
            candidates.add(Chord(note, ChordType.SEVENTH))
            candidates.add(Chord(note, ChordType.MINOR_SEVENTH))
            candidates.add(Chord(note, ChordType.SUS4))
        }

        var bestChord: Chord? = null
        var maxScore = -1.0

        for (candidate in candidates) {
            val template = createChordTemplate(candidate)
            val score = dotProduct(chroma, template)
            if (score > maxScore) {
                maxScore = score
                bestChord = candidate
            }
        }

        return if (maxScore > 0.35) bestChord else null
    }

    private fun createChordTemplate(chord: Chord): FloatArray {
        val template = FloatArray(12)
        val pitchClasses = chord.getPitchClasses()
        for (pc in pitchClasses) {
            template[pc.semitone] = 1.0f
        }
        // Bobot ekstra pada nada dasar (root)
        template[chord.root.semitone] = 1.5f
        return template
    }

    private fun dotProduct(a: FloatArray, b: FloatArray): Double {
        var dot = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in 0 until 12) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 1e-9) dot / denom else 0.0
    }
}
