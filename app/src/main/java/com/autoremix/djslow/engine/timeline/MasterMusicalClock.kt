package com.autoremix.djslow.engine.timeline

/**
 * SATU MASTER MUSICAL CLOCK (FASE 2 - Bagian C).
 *
 * Semua subsistem (beat, bar, downbeat, BPM, chord, vocal, arrangement,
 * drum, bass, melody, pad, dan FX) wajib merujuk pada basis waktu terpadu ini.
 * Dilarang membuat clock BPM terpisah untuk masing-masing engine.
 */
interface IMusicalClock {
    val bpm: Float
    val sampleRate: Int
    val beatsPerBar: Int
    val samplesPerBeat: Double
    val msPerBeat: Double

    fun beatToSample(beat: Float): Long
    fun sampleToBeat(sample: Long): Float
    fun beatToMs(beat: Float): Long
    fun msToBeat(ms: Long): Float
    fun sampleToMs(sample: Long): Long
    fun msToSample(ms: Long): Long
    fun getBarForBeat(beat: Float): Int
    fun getBeatInBar(beat: Float): Float
    fun getDownbeatSampleForBar(barIndex: Int): Long
    fun getDownbeatMsForBar(barIndex: Int): Long
}

/**
 * Implementasi Master Musical Clock sample-akurat pada 44.1 kHz.
 */
class MasterMusicalClock(
    override val bpm: Float,
    override val sampleRate: Int = 44100,
    override val beatsPerBar: Int = 4
) : IMusicalClock {

    private val safeBpm = bpm.coerceIn(40.0f, 240.0f)

    override val samplesPerBeat: Double = (sampleRate * 60.0) / safeBpm
    override val msPerBeat: Double = (60.0 * 1000.0) / safeBpm

    override fun beatToSample(beat: Float): Long = (beat * samplesPerBeat).toLong()

    override fun sampleToBeat(sample: Long): Float = (sample / samplesPerBeat).toFloat()

    override fun beatToMs(beat: Float): Long = (beat * msPerBeat).toLong()

    override fun msToBeat(ms: Long): Float = (ms / msPerBeat).toFloat()

    override fun sampleToMs(sample: Long): Long = ((sample * 1000L) / sampleRate)

    override fun msToSample(ms: Long): Long = ((ms * sampleRate) / 1000L)

    override fun getBarForBeat(beat: Float): Int = (beat / beatsPerBar).toInt()

    override fun getBeatInBar(beat: Float): Float = beat % beatsPerBar

    override fun getDownbeatSampleForBar(barIndex: Int): Long = beatToSample((barIndex * beatsPerBar).toFloat())

    override fun getDownbeatMsForBar(barIndex: Int): Long = beatToMs((barIndex * beatsPerBar).toFloat())
}
