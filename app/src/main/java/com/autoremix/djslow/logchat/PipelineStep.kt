package com.autoremix.djslow.logchat

enum class PipelineStage(val label: String, val order: Int) {
    INPUT("INPUT", 1),
    DECODE("DECODE", 2),
    ANALYSIS("ANALISIS", 3),
    MUSICAL_MAP("MUSICAL MAP", 4),
    REMIX("REMIX", 5),
    ARRANGEMENT("ARRANGEMENT", 6),
    GENERATOR("GENERATOR", 7),
    VOCAL_FX("VOCAL FX", 8),
    MIX("MIX", 9),
    MASTER("MASTER", 10),
    EXPORT("EXPORT", 11),
    PLAYBACK("PLAYBACK", 12)
}

enum class StepStatus(val label: String, val symbol: String) {
    PENDING("Belum Dijalankan", "⚪"),
    SUCCESS("Berhasil", "🟢"),
    WARNING("Peringatan", "🟡"),
    FAILED("Gagal", "🔴")
}

data class PipelineNodeStatus(
    val stage: PipelineStage,
    val status: StepStatus = StepStatus.PENDING,
    val detail: String? = null,
    val lastUpdated: Long = System.currentTimeMillis()
)
