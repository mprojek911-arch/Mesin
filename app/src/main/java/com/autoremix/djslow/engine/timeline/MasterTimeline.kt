package com.autoremix.djslow.engine.timeline

import com.autoremix.djslow.engine.arrangement.TransitionFxEvent
import com.autoremix.djslow.engine.drum.DrumEvent
import com.autoremix.djslow.engine.melody.MelodyEvent
import com.autoremix.djslow.engine.music.Chord
import com.autoremix.djslow.engine.music.MusicKey
import com.autoremix.djslow.engine.structure.EnergyCurve
import com.autoremix.djslow.engine.structure.SongSection

/**
 * Event pada Master Timeline.
 */
sealed class TimelineEvent {
    abstract val barIndex: Int
    abstract val beatIndexInBar: Float
    abstract val durationBeats: Float
    abstract val startSample: Long
    abstract val endSample: Long

    val startTimeMs: Long
        get() = (startSample * 1000L) / 44100L

    val durationMs: Long
        get() = ((endSample - startSample) * 1000L) / 44100L

    data class ChordEvent(
        override val barIndex: Int,
        override val beatIndexInBar: Float,
        override val durationBeats: Float,
        override val startSample: Long,
        override val endSample: Long,
        val chord: Chord
    ) : TimelineEvent()

    data class BassEvent(
        override val barIndex: Int,
        override val beatIndexInBar: Float,
        override val durationBeats: Float,
        override val startSample: Long,
        override val endSample: Long,
        val midiNote: Int,
        val frequencyHz: Float,
        val velocity: Float = 0.85f,
        val isSlide: Boolean = false
    ) : TimelineEvent()
}

/**
 * Titik grid ketukan (Beat Grid) dalam timeline.
 */
data class BeatGridPoint(
    val totalBeatIndex: Int,
    val barIndex: Int,
    val beatInBar: Int, // 0..beatsPerBar - 1
    val sampleOffset: Long,
    val timeMs: Long
)

/**
 * Master Timeline untuk menyelaraskan waktu seluruh event musik:
 * MASTER CLOCK (44.1 kHz) -> BPM -> BEAT GRID -> BAR -> CHORD & BASS EVENTS.
 */
data class MasterTimeline(
    override val bpm: Float = 80.0f,
    override val beatsPerBar: Int = 4,
    override val sampleRate: Int = 44100,
    val totalDurationMs: Long = 180000L, // Default 3 menit
    val beatGrid: List<BeatGridPoint> = emptyList(),
    val chordEvents: List<TimelineEvent.ChordEvent> = emptyList(),
    val bassEvents: List<TimelineEvent.BassEvent> = emptyList(),
    val sections: List<SongSection> = emptyList(),
    val energyCurve: EnergyCurve? = null,
    val drumEvents: List<DrumEvent> = emptyList(),
    val melodyEvents: List<MelodyEvent> = emptyList(),
    val transitionEvents: List<TransitionFxEvent> = emptyList()
) : IMusicalClock {
    val totalFrames: Long = (totalDurationMs * sampleRate) / 1000L
    override val samplesPerBeat: Double = (sampleRate * 60.0) / bpm.coerceIn(40.0f, 240.0f)
    override val msPerBeat: Double = (60.0 * 1000.0) / bpm.coerceIn(40.0f, 240.0f)
    val totalBeats: Int get() = beatGrid.size
    val totalBars: Int = if (beatGrid.isEmpty()) 0 else (beatGrid.size + beatsPerBar - 1) / beatsPerBar

    /**
     * Mengkonversi posisi ketukan total menjadi sample offset di Master Clock.
     */
    override fun beatToSample(beat: Float): Long {
        return (beat * samplesPerBeat).toLong().coerceIn(0L, totalFrames)
    }

    /**
     * Mengkonversi posisi ketukan total menjadi milidetik.
     */
    override fun beatToMs(beat: Float): Long {
        return (beat * msPerBeat).toLong()
    }

    override fun sampleToBeat(sample: Long): Float {
        return (sample.coerceIn(0L, totalFrames) / samplesPerBeat).toFloat()
    }

    override fun msToBeat(ms: Long): Float {
        return (ms / msPerBeat).toFloat()
    }

    override fun sampleToMs(sample: Long): Long {
        return ((sample * 1000L) / sampleRate)
    }

    override fun msToSample(ms: Long): Long {
        return ((ms * sampleRate) / 1000L).coerceIn(0L, totalFrames)
    }

    override fun getBarForBeat(beat: Float): Int = (beat / beatsPerBar).toInt()

    override fun getBeatInBar(beat: Float): Float = beat % beatsPerBar

    override fun getDownbeatSampleForBar(barIndex: Int): Long = beatToSample((barIndex * beatsPerBar).toFloat())

    override fun getDownbeatMsForBar(barIndex: Int): Long = beatToMs((barIndex * beatsPerBar).toFloat())

    /**
     * Mendapatkan akor aktif pada sample clock tertentu.
     */
    fun getChordAtSample(sample: Long): Chord? {
        return chordEvents.firstOrNull { sample in it.startSample until it.endSample }?.chord
    }

    companion object {
        /**
         * Membangun Master Timeline dan Beat Grid berdasarkan BPM dan durasi audio.
         */
        fun build(
            bpm: Float,
            totalDurationMs: Long,
            beatsPerBar: Int = 4,
            sampleRate: Int = 44100
        ): MasterTimeline {
            val safeBpm = bpm.coerceIn(60.0f, 140.0f)
            val duration = maxOf(totalDurationMs, 4000L)
            val totalFrames = (duration * sampleRate) / 1000L
            val samplesPerBeat = (sampleRate * 60.0) / safeBpm

            val totalBeats = ((duration / 1000.0) * (safeBpm / 60.0)).toInt() + 1
            val gridPoints = ArrayList<BeatGridPoint>(totalBeats)

            for (b in 0 until totalBeats) {
                val sampleOffset = (b * samplesPerBeat).toLong()
                if (sampleOffset >= totalFrames) break
                val timeMs = ((sampleOffset * 1000.0) / sampleRate).toLong()
                val barIndex = b / beatsPerBar
                val beatInBar = b % beatsPerBar
                gridPoints.add(
                    BeatGridPoint(
                        totalBeatIndex = b,
                        barIndex = barIndex,
                        beatInBar = beatInBar,
                        sampleOffset = sampleOffset,
                        timeMs = timeMs
                    )
                )
            }

            return MasterTimeline(
                bpm = safeBpm,
                beatsPerBar = beatsPerBar,
                sampleRate = sampleRate,
                totalDurationMs = duration,
                beatGrid = gridPoints
            )
        }
    }
}
