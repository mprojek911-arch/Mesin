package com.autoremix.djslow.guide

/**
 * Topik bantuan kontekstual untuk tombol ⓘ di setiap bagian UI fitur asli.
 */
enum class ContextualHelpTopic(
    val title: String,
    val icon: String,
    val explanation: String,
    val relatedStep: GuideStep
) {
    AUDIO_SELECTION(
        title = "PILIH AUDIO SUMBER",
        icon = "🎵",
        explanation = "Pilih file lagu (MP3, WAV, M4A, AAC) dari perangkat Anda. Anda dapat memilih vokal saja, beat saja, atau keduanya untuk digabungkan menjadi lagu remix baru.",
        relatedStep = GuideStep.STEP_01_PILIH_AUDIO
    ),
    ANALYSIS(
        title = "ANALISIS MUSIK NYATA",
        icon = "🔎",
        explanation = "Aplikasi akan memindai bentuk gelombang audio untuk menghitung BPM secara matematis, mengidentifikasi nada dasar, serta mendeteksi ketukan transien untuk sinkronisasi tempo.",
        relatedStep = GuideStep.STEP_02_ANALISIS_MUSIK
    ),
    VOCAL_SEPARATION(
        title = "PEMISAHAN VOKAL & BEAT",
        icon = "🎤",
        explanation = "Vokal digunakan untuk suara penyanyi asli. Beat/Instrumental digunakan untuk pola drum ritmis. Memisahkan keduanya memungkinkan remixer mengatur volume, ducking, dan efek secara independen.",
        relatedStep = GuideStep.STEP_03_PISAHKAN_VOCAL_BEAT
    ),
    BPM_AND_TEMPO(
        title = "BPM & TEMPO REMIX",
        icon = "🎚️",
        explanation = "BPM (Beats Per Minute) menentukan kecepatan lagu. BPM lebih tinggi membuat musik lebih cepat dan bertenaga. Untuk gaya DJ Slow khas Indonesia, tempo ideal berkisar di 75 - 85 BPM.",
        relatedStep = GuideStep.STEP_04_ATUR_BPM_PITCH
    ),
    REMIX_STYLE(
        title = "GAYA REMIX & PRESET",
        icon = "🎧",
        explanation = "Preset gaya menentukan ketukan drum, variasi bassline, dan pola akord yang digunakan: DJ Slow (santai & berbobot), Slow Bass (sub-bass kuat), atau Breakbeat ritmis.",
        relatedStep = GuideStep.STEP_05_PILIH_GAYA_REMIX
    ),
    ARRANGEMENT_STRUCTURE(
        title = "STRUKTUR REMIX",
        icon = "🎼",
        explanation = "Lagu remix dibagi menjadi beberapa seksi: Intro (pembuka santai), Build-Up (tegangan naik), Drop (bagian puncak dentuman drum & bass), Break (jeda tenang), dan Outro (penutup).",
        relatedStep = GuideStep.STEP_06_ATUR_STRUKTUR_REMIX
    ),
    LAYERS_MIXER(
        title = "MULTI-TRACK & BUS MIXER",
        icon = "🎹",
        explanation = "Atur keseimbangan suara antara Drum, Bass, Akord, Melodi, Pad, dan Vokal. Pastikan tidak ada instrumen yang saling bertabrakan atau terlalu mendominasi.",
        relatedStep = GuideStep.STEP_07_ATUR_MUSIK_LAYERS
    ),
    VOCAL_CONTROLS(
        title = "VOKAL & AUTO DUCKING",
        icon = "🎤",
        explanation = "Fitur Auto Ducking secara otomatis menurunkan sedikit volume musik saat vokal penyanyi masuk, sehingga vokal selalu terdengar jelas dan tidak tenggelam dalam dentuman bass.",
        relatedStep = GuideStep.STEP_08_ATUR_VOCAL
    ),
    FX_TRANSITIONS(
        title = "FX & TRANSISI",
        icon = "🔥",
        explanation = "Efek transisi seperti Riser (suara mendesing naik menjelang drop) dan Impact (dentuman keras saat drop mulai) membuat aransemen remix terasa dinamis dan tidak membosankan.",
        relatedStep = GuideStep.STEP_09_ATUR_FX_TRANSITION
    ),
    PREVIEW_PLAYER(
        title = "PREVIEW PEMUTAR AUDIO ASLI",
        icon = "▶️",
        explanation = "Dengarkan trek audio sumber dengan sinkronisasi tempo nyata. Gunakan kontrol Play, Jeda, dan Stop untuk memverifikasi keselarasan vokal dan musik.",
        relatedStep = GuideStep.STEP_10_PREVIEW_MONITOR
    ),
    RENDER_MASTER(
        title = "RENDER REMIX STUDIO",
        icon = "⚙️",
        explanation = "Proses render memadukan synthesizers, drum PCM, trek audio, compressor, limiter, dan stereo processor menjadi satu file audio WAV stereo 16-bit berstandar industri.",
        relatedStep = GuideStep.STEP_11_RENDER_REMIX
    ),
    RENDERED_PLAYBACK(
        title = "PUTAR HASIL REMIX NYATA",
        icon = "🎧",
        explanation = "File audio mix utuh yang sudah dirender dapat diputar secara langsung menggunakan pemutar media nyata. Menampilkan durasi, posisi waktu, dan nama file fisik.",
        relatedStep = GuideStep.STEP_12_PUTAR_HASIL_REMIX
    ),
    EXPORT_MEDIASTORE(
        title = "SIMPAN / EKSPOR FILE",
        icon = "💾",
        explanation = "Hasil remix akan diekspor ke folder Musik (Audio MediaStore) perangkat Android Anda sehingga dapat diputar di pemutar musik apa pun atau dibagikan.",
        relatedStep = GuideStep.STEP_13_SIMPAN_EXPORT
    );

    val shortSummary: String get() = explanation
    val detailedExplanation: String get() = explanation
    val targetStep: GuideStep get() = relatedStep
}
