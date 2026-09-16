package com.autoremix.djslow.logchat

data class AudioDiagnosticInfo(
    val fileName: String,
    val format: String,
    val durationMs: Long,
    val sampleRate: Int,
    val channels: Int,
    val bpm: Float? = null,
    val key: String? = null,
    val fileSizeBytes: Long = 0L,
    val lastSuccessfulStep: String = "Import"
)
