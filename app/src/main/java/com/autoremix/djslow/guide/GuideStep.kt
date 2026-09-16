package com.autoremix.djslow.guide

/**
 * 13 Langkah Panduan Interaktif Remix Musik Auto Remix DJ Slow.
 * Mengikuti spesifikasi alur remix step-by-step tanpa mock data.
 */
enum class GuideStep(
    val stepNumber: Int,
    val title: String,
    val shortTitle: String,
    val icon: String,
    val targetFeature: String,
    val description: String,
    val actionButtonText: String
) {
    STEP_01_PILIH_AUDIO(
        stepNumber = 1,
        title = "PILIH AUDIO",
        shortTitle = "Pilih Audio",
        icon = "🎵",
        targetFeature = "PILIH AUDIO",
        description = "Pertama, pilih lagu yang ingin Anda remix dari perangkat Anda. Anda dapat memilih vokal dan musik beat pengiring.",
        actionButtonText = "PILIH AUDIO SEKARANG"
    ),
    STEP_02_ANALISIS_MUSIK(
        stepNumber = 2,
        title = "ANALISIS MUSIK",
        shortTitle = "Analisis Musik",
        icon = "🔎",
        targetFeature = "ANALISIS MUSIK",
        description = "Aplikasi akan membaca karakter lagu untuk membantu menentukan BPM, tempo, beat, nada (key), dan bagian musik secara akurat.",
        actionButtonText = "MULAI ANALISIS MUSIK"
    ),
    STEP_03_PISAHKAN_VOCAL_BEAT(
        stepNumber = 3,
        title = "PISAHKAN VOCAL & BEAT",
        shortTitle = "Vocal & Beat",
        icon = "🎤",
        targetFeature = "VOCAL & BEAT SUMBER",
        description = "Gunakan pemisahan audio jika ingin mengatur vokal dan musik secara terpisah. Vokal digunakan untuk suara penyanyi, sedangkan Beat/Instrumental digunakan untuk musik pengiring.",
        actionButtonText = "ATUR SUMBER AUDIO"
    ),
    STEP_04_ATUR_BPM_PITCH(
        stepNumber = 4,
        title = "ATUR BPM & PITCH",
        shortTitle = "BPM & Pitch",
        icon = "🥁",
        targetFeature = "TEMPO & NADA",
        description = "BPM mengatur kecepatan musik (DJ Slow idealnya di kisaran 75-85 BPM). Pitch mengatur tinggi rendah nada vokal agar harmonis.",
        actionButtonText = "SESUAIKAN BPM / PITCH"
    ),
    STEP_05_PILIH_GAYA_REMIX(
        stepNumber = 5,
        title = "PILIH GAYA REMIX",
        shortTitle = "Gaya Remix",
        icon = "🎧",
        targetFeature = "PRESET GAYA DJ SLOW",
        description = "Pilih karakter remix nyata yang didukung engine, seperti DJ Slow, Slow Bass, Chill, Breakbeat, atau Custom aransemen.",
        actionButtonText = "PILIH GAYA REMIX"
    ),
    STEP_06_ATUR_STRUKTUR_REMIX(
        stepNumber = 6,
        title = "ATUR STRUKTUR REMIX",
        shortTitle = "Struktur Remix",
        icon = "🎼",
        targetFeature = "STRUKTUR LAGU",
        description = "Struktur menentukan alur musik: INTRO (pembuka) ➔ BUILD UP (energi naik) ➔ DROP (bagian utama) ➔ BREAK (istirahat ruang) ➔ OUTRO (penutup).",
        actionButtonText = "LIHAT STRUKTUR"
    ),
    STEP_07_ATUR_MUSIK_LAYERS(
        stepNumber = 7,
        title = "ATUR MUSIK / LAYERS",
        shortTitle = "Musik & Layers",
        icon = "🎹",
        targetFeature = "MULTI-TRACK MIXER",
        description = "Atur volume layer instrumen nyata yang aktif (Drum, Bass, Chord, Melodi, Pad). Gunakan fader volume untuk mengatur seberapa kuat layer terdengar.",
        actionButtonText = "BUKA MIXER LAYERS"
    ),
    STEP_08_ATUR_VOCAL(
        stepNumber = 8,
        title = "ATUR VOCAL",
        shortTitle = "Atur Vocal",
        icon = "🎤",
        targetFeature = "VOCAL PROCESSING & DUCKING",
        description = "Atur volume vokal, filter EQ, dan Auto Ducking agar vokal penyanyi menyatu jernih di atas hentakan kick & bass remix.",
        actionButtonText = "ATUR AUTO MIX / DUCKING"
    ),
    STEP_09_ATUR_FX_TRANSITION(
        stepNumber = 9,
        title = "FX & TRANSITION",
        shortTitle = "FX & Transisi",
        icon = "🔥",
        targetFeature = "EFEK & TRANSISI",
        description = "FX digunakan untuk membuat perpindahan antarbagian musik terasa lebih hidup dengan transisi riser, impact, dan filter sweep.",
        actionButtonText = "LIHAT FX TRANSISI"
    ),
    STEP_10_PREVIEW_MONITOR(
        stepNumber = 10,
        title = "PREVIEW & MONITOR",
        shortTitle = "Preview Audio",
        icon = "▶️",
        targetFeature = "AUDIO PLAYER ASLI",
        description = "Dengarkan preview audio langsung melalui engine pemutar audio nyata dengan kontrol Play, Jeda, dan Stop.",
        actionButtonText = "PUTAR PREVIEW AUDIO"
    ),
    STEP_11_RENDER_REMIX(
        stepNumber = 11,
        title = "RENDER REMIX",
        shortTitle = "Render WAV",
        icon = "⚙️",
        targetFeature = "RENDER ENGINE WAV",
        description = "Render akan memproses dan menggabungkan seluruh komponen remix menjadi satu file audio WAV hasil remix kualitas studio.",
        actionButtonText = "MULAI RENDER REMIX"
    ),
    STEP_12_PUTAR_HASIL_REMIX(
        stepNumber = 12,
        title = "PUTAR HASIL REMIX",
        shortTitle = "Putar Hasil",
        icon = "🎧",
        targetFeature = "HASIL REMIX PLAYER",
        description = "Putar file audio hasil render asli. Hasil remix final diputar sebagai satu audio mix lengkap dengan durasi dan progress playback sebenarnya.",
        actionButtonText = "PUTAR HASIL REMIX"
    ),
    STEP_13_SIMPAN_EXPORT(
        stepNumber = 13,
        title = "SIMPAN / EXPORT",
        shortTitle = "Simpan File",
        icon = "💾",
        targetFeature = "SIMPAN KE MEDIASTORE",
        description = "Jika Anda sudah puas dengan hasil remix, simpan atau ekspor file audio ke folder Musik perangkat Anda agar dapat didengarkan kapan saja.",
        actionButtonText = "SIMPAN HASIL REMIX"
    );

    val explanation: String get() = description
    val actionLabel: String get() = actionButtonText

    companion object {
        fun fromStepNumber(number: Int): GuideStep {
            return entries.firstOrNull { it.stepNumber == number } ?: STEP_01_PILIH_AUDIO
        }
    }
}
