package com.autoremix.djslow.engine.timeline

import com.autoremix.djslow.engine.music.Chord
import com.autoremix.djslow.engine.music.MusicKey

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
    val bpm: Float = 80.0f,
    val beatsPerBar: Int = 4,
    val sampleRate: Int = 44100,
    val totalDurationMs: Long = 180000L, // Default 3 menit
    val beatGrid: List<BeatGridPoint> = emptyList(),
    val chordEvents: List<TimelineEvent.ChordEvent> = emptyList(),
    val bassEvents: List<TimelineEvent.BassEvent> = emptyList()
) {
    val totalFrames: Long = (totalDurationMs * sampleRate) / 1000L
    val samplesPerBeat: Double = (sampleRate * 60.0) / bpm
    val msPerBeat: Double = (60.0 * 1000.0) / bpm
    val totalBeats: Int get() = beatGrid.size
    val totalBars: Int = if (beatGrid.isEmpty()) 0 else (beatGrid.size + beatsPerBar - 1) / beatsPerBar

    /**
     * Mengkonversi posisi ketukan total menjadi sample offset di Master Clock.
     */
    fun beatToSample(beat: Float): Long {
        return (beat * samplesPerBeat).toLong().coerceIn(0L, totalFrames)
    }

    /**
     * Mengkonversi posisi ketukan total menjadi milidetik.
     */
    fun beatToMs(beat: Float): Long {
        return (beat * msPerBeat).toLong()
    }

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
