package com.autoremix.djslow.logchat

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class LogLevel(val label: String, val severity: Int) {
    DEBUG("DEBUG", 0),
    INFO("INFO", 1),
    WARNING("WARNING", 2),
    ERROR("ERROR", 3),
    CRITICAL("CRITICAL", 4)
}

enum class LogModule(val label: String) {
    AUDIO("AUDIO"),
    BPM("BPM"),
    KEY("KEY"),
    REMIX("REMIX"),
    ARRANGEMENT("ARRANGEMENT"),
    MIX("MIX"),
    MASTER("MASTER"),
    EXPORT("EXPORT"),
    PLAYBACK("PLAYBACK"),
    SYSTEM("SYSTEM")
}

data class LogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val module: LogModule,
    val message: String,
    val detail: String? = null,
    val stackTrace: String? = null,
    val pipelineStage: String? = null,
    val associatedFile: String? = null
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))

    val formattedDateTime: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
}
