package com.autoremix.djslow.engine.music

import kotlin.math.pow

/**
 * Notasi Pitch Class 12 nada kromatik (C = 0 .. B = 11).
 */
enum class PitchClass(val semitone: Int, val noteName: String) {
    C(0, "C"),
    C_SHARP(1, "C#"),
    D(2, "D"),
    D_SHARP(3, "D#"),
    E(4, "E"),
    F(5, "F"),
    F_SHARP(6, "F#"),
    G(7, "G"),
    G_SHARP(8, "G#"),
    A(9, "A"),
    A_SHARP(10, "A#"),
    B(11, "B");

    val displayName: String
        get() = noteName

    companion object {
        fun fromSemitone(semitone: Int): PitchClass {
            val normalized = ((semitone % 12) + 12) % 12
            return entries.first { it.semitone == normalized }
        }

        fun fromName(name: String): PitchClass? {
            val clean = name.trim().uppercase()
            return entries.firstOrNull {
                it.noteName.uppercase() == clean ||
                        (clean == "DB" && it == C_SHARP) ||
                        (clean == "EB" && it == D_SHARP) ||
                        (clean == "GB" && it == F_SHARP) ||
                        (clean == "AB" && it == G_SHARP) ||
                        (clean == "BB" && it == A_SHARP)
            }
        }
    }
}

/**
 * Mode nada: Major (Mayor) atau Minor (Minor).
 */
enum class MusicMode(val label: String) {
    MAJOR("Mayor"),
    MINOR("Minor")
}

/**
 * Representasi Tangga Nada / Key musik.
 */
data class MusicKey(
    val tonic: PitchClass,
    val mode: MusicMode,
    val confidence: Float = 0.8f
) {
    val displayName: String
        get() = when (mode) {
            MusicMode.MAJOR -> "${tonic.noteName} Mayor"
            MusicMode.MINOR -> "${tonic.noteName}m (${tonic.noteName} Minor)"
        }

    val shortName: String
        get() = when (mode) {
            MusicMode.MAJOR -> tonic.noteName
            MusicMode.MINOR -> "${tonic.noteName}m"
        }

    /**
     * Menghasilkan skala 7 nada diatonik dari tangga nada ini.
     */
    fun getScalePitchClasses(): List<PitchClass> {
        val intervals = when (mode) {
            MusicMode.MAJOR -> intArrayOf(0, 2, 4, 5, 7, 9, 11)
            MusicMode.MINOR -> intArrayOf(0, 2, 3, 5, 7, 8, 10) // Natural minor
        }
        return intervals.map { PitchClass.fromSemitone(tonic.semitone + it) }
    }
}

/**
 * Tipe akor yang didukung dalam Tahap 3.
 * Prioritas: Major dan Minor, serta variasi 7, m7, sus2, sus4, dim.
 */
enum class ChordType(val symbol: String, val intervals: IntArray) {
    MAJOR("", intArrayOf(0, 4, 7)),
    MINOR("m", intArrayOf(0, 3, 7)),
    SEVENTH("7", intArrayOf(0, 4, 7, 10)),
    MINOR_SEVENTH("m7", intArrayOf(0, 3, 7, 10)),
    SUS2("sus2", intArrayOf(0, 2, 7)),
    SUS4("sus4", intArrayOf(0, 5, 7)),
    DIMINISHED("dim", intArrayOf(0, 3, 6));

    override fun toString(): String = symbol
}

/**
 * Definisi Akor lengkap.
 */
data class Chord(
    val root: PitchClass,
    val type: ChordType
) {
    val name: String
        get() = "${root.noteName}${type.symbol}"

    val displayName: String
        get() = name

    /**
     * Nada-nada penyusun akor dalam pitch class.
     */
    fun getPitchClasses(): List<PitchClass> {
        return type.intervals.map { PitchClass.fromSemitone(root.semitone + it) }
    }

    /**
     * Menghasilkan frekuensi dasar nada-nada penyusun untuk oktaf tertentu (default oktaf 4).
     */
    fun getFrequenciesHz(octave: Int = 4): List<Float> {
        // C4 = MIDI note 60, Frekuensi A4 (MIDI 69) = 440.0 Hz
        val rootMidi = 12 * (octave + 1) + root.semitone
        return type.intervals.map { interval ->
            val midiNote = rootMidi + interval
            midiToFrequency(midiNote)
        }
    }

    companion object {
        fun midiToFrequency(midiNote: Int): Float {
            return (440.0 * 2.0.pow((midiNote - 69.0) / 12.0)).toFloat()
        }
    }
}
