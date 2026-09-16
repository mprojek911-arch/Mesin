package com.autoremix.djslow.logchat

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

object LogChatManager {

    private const val TAG = "LogChat"
    private const val MAX_LOGS = 500
    private const val LOG_FILE_NAME = "logchat_history.json"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appContext: Context? = null
    private var isInitialized = false

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val _pipelineStatus = MutableStateFlow<Map<PipelineStage, PipelineNodeStatus>>(
        PipelineStage.entries.associateWith { stage ->
            PipelineNodeStatus(stage = stage, status = StepStatus.PENDING)
        }
    )
    val pipelineStatus: StateFlow<Map<PipelineStage, PipelineNodeStatus>> = _pipelineStatus.asStateFlow()

    private val _audioDiagnostic = MutableStateFlow<AudioDiagnosticInfo?>(null)
    val audioDiagnostic: StateFlow<AudioDiagnosticInfo?> = _audioDiagnostic.asStateFlow()

    private val _lastCrashReport = MutableStateFlow<String?>(null)
    val lastCrashReport: StateFlow<String?> = _lastCrashReport.asStateFlow()

    fun init(context: Context) {
        if (isInitialized) return
        val appCtx = context.applicationContext
        appContext = appCtx
        isInitialized = true

        // Install Crash Handler
        CrashHandler.install(appCtx)

        // Load persisted logs and crash report asynchronously
        scope.launch {
            loadPersistedLogs(appCtx)
            checkLastCrash(appCtx)
        }
    }

    fun log(
        level: LogLevel,
        module: LogModule,
        message: String,
        detail: String? = null,
        stackTrace: String? = null,
        pipelineStage: String? = null,
        associatedFile: String? = null
    ) {
        val entry = LogEntry(
            level = level,
            module = module,
            message = message,
            detail = detail,
            stackTrace = stackTrace,
            pipelineStage = pipelineStage,
            associatedFile = associatedFile
        )

        // Android logcat mirroring
        when (level) {
            LogLevel.DEBUG -> Log.d(TAG, "[${module.name}] $message")
            LogLevel.INFO -> Log.i(TAG, "[${module.name}] $message")
            LogLevel.WARNING -> Log.w(TAG, "[${module.name}] $message - $detail")
            LogLevel.ERROR -> Log.e(TAG, "[${module.name}] $message - $detail\n$stackTrace")
            LogLevel.CRITICAL -> Log.e(TAG, "CRITICAL [${module.name}] $message - $detail\n$stackTrace")
        }

        _logs.update { currentList ->
            val updated = currentList + entry
            if (updated.size > MAX_LOGS) {
                updated.takeLast(MAX_LOGS)
            } else {
                updated
            }
        }

        // Persist to disk in background
        appContext?.let { ctx ->
            scope.launch {
                saveLogsToDisk(ctx)
            }
        }
    }

    fun info(module: LogModule, message: String, detail: String? = null, stage: String? = null, file: String? = null) {
        log(LogLevel.INFO, module, message, detail, null, stage, file)
    }

    fun warn(module: LogModule, message: String, detail: String? = null, stage: String? = null, file: String? = null) {
        log(LogLevel.WARNING, module, message, detail, null, stage, file)
    }

    fun error(
        module: LogModule,
        message: String,
        throwable: Throwable? = null,
        detail: String? = null,
        stage: String? = null,
        file: String? = null
    ) {
        val stackTrace = throwable?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            sw.toString()
        }
        val fullDetail = buildString {
            detail?.let { append(it) }
            throwable?.message?.let {
                if (isNotEmpty()) append("\n")
                append("Exception: ${throwable.javaClass.simpleName}: $it")
            }
        }.ifEmpty { null }

        log(LogLevel.ERROR, module, message, fullDetail, stackTrace, stage, file)
    }

    fun critical(
        module: LogModule,
        message: String,
        throwable: Throwable? = null,
        detail: String? = null,
        stage: String? = null
    ) {
        val stackTrace = throwable?.let {
            val sw = StringWriter()
            it.printStackTrace(PrintWriter(sw))
            sw.toString()
        }
        log(LogLevel.CRITICAL, module, message, detail, stackTrace, stage)
    }

    fun debug(module: LogModule, message: String) {
        log(LogLevel.DEBUG, module, message)
    }

    fun updatePipeline(stage: PipelineStage, status: StepStatus, detail: String? = null) {
        _pipelineStatus.update { currentMap ->
            val updated = currentMap.toMutableMap()
            updated[stage] = PipelineNodeStatus(stage = stage, status = status, detail = detail)
            updated
        }

        // Log automatically
        when (status) {
            StepStatus.SUCCESS -> info(
                module = mapStageToModule(stage),
                message = "${stage.label}: BERHASIL",
                detail = detail,
                stage = stage.label
            )
            StepStatus.WARNING -> warn(
                module = mapStageToModule(stage),
                message = "${stage.label}: PERINGATAN",
                detail = detail,
                stage = stage.label
            )
            StepStatus.FAILED -> error(
                module = mapStageToModule(stage),
                message = "${stage.label}: GAGAL",
                detail = detail,
                stage = stage.label
            )
            StepStatus.PENDING -> {}
        }
    }

    fun updateAudioDiagnostic(info: AudioDiagnosticInfo) {
        _audioDiagnostic.value = info
    }

    fun updateLastSuccessfulStep(step: String) {
        _audioDiagnostic.update { current ->
            current?.copy(lastSuccessfulStep = step)
        }
    }

    fun resetPipeline() {
        _pipelineStatus.value = PipelineStage.entries.associateWith { stage ->
            PipelineNodeStatus(stage = stage, status = StepStatus.PENDING)
        }
    }

    fun clearLogs() {
        _logs.value = emptyList()
        appContext?.let { ctx ->
            scope.launch {
                try {
                    val file = File(ctx.filesDir, "logchat/$LOG_FILE_NAME")
                    if (file.exists()) file.delete()
                    val crashFile = File(ctx.filesDir, "logchat/last_crash.txt")
                    if (crashFile.exists()) crashFile.delete()
                    _lastCrashReport.value = null
                } catch (e: Exception) {
                    Log.e(TAG, "Gagal menghapus log storage: ${e.message}")
                }
            }
        }
        info(LogModule.SYSTEM, "Seluruh catatan LogChat berhasil dibersihkan oleh pengguna.")
    }

    fun exportLogsAsText(): String {
        val currentLogs = _logs.value
        val pipeline = _pipelineStatus.value
        val audio = _audioDiagnostic.value

        return buildString {
            appendLine("=== AUTO REMIX DJ SLOW — LAPORAN DIAGNOSTIK LOGCHAT ===")
            appendLine("Tanggal Ekspor: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}")
            appendLine("Total Catatan: ${currentLogs.size}")
            appendLine()

            appendLine("--- STATUS PIPELINE AUDIO ---")
            PipelineStage.entries.forEach { stage ->
                val node = pipeline[stage]
                appendLine("${node?.status?.symbol ?: "⚪"} ${stage.label}: ${node?.status?.label ?: "Belum Dijalankan"}${if (!node?.detail.isNullOrBlank()) " (${node?.detail})" else ""}")
            }
            appendLine()

            if (audio != null) {
                appendLine("--- INFORMASI AUDIO DIAGNOSTIK ---")
                appendLine("File: ${audio.fileName}")
                appendLine("Format: ${audio.format}")
                appendLine("Durasi: ${audio.durationMs / 1000}s (${audio.durationMs} ms)")
                appendLine("Sample Rate: ${audio.sampleRate} Hz")
                appendLine("Channels: ${audio.channels}")
                appendLine("BPM: ${audio.bpm?.let { "%.1f".format(it) } ?: "Belum teranalisis"}")
                appendLine("Key: ${audio.key ?: "Belum teranalisis"}")
                appendLine("Ukuran: ${audio.fileSizeBytes / 1024} KB")
                appendLine("Langkah Terakhir Berhasil: ${audio.lastSuccessfulStep}")
                appendLine()
            }

            appendLine("--- DAFTAR LOG SISTEM & AUDIO ---")
            if (currentLogs.isEmpty()) {
                appendLine("(Tidak ada catatan log)")
            } else {
                currentLogs.forEach { entry ->
                    appendLine("[${entry.formattedDateTime}] [${entry.level.name}] [${entry.module.name}] ${entry.message}")
                    if (!entry.detail.isNullOrBlank()) {
                        appendLine("  Detail: ${entry.detail}")
                    }
                    if (!entry.associatedFile.isNullOrBlank()) {
                        appendLine("  File Terkait: ${entry.associatedFile}")
                    }
                    if (!entry.pipelineStage.isNullOrBlank()) {
                        appendLine("  Tahap Pipeline: ${entry.pipelineStage}")
                    }
                    if (!entry.stackTrace.isNullOrBlank()) {
                        appendLine("  Stack Trace:\n${entry.stackTrace.trim().prependIndent("    ")}")
                    }
                    appendLine("----------------------------------------")
                }
            }
        }
    }

    /**
     * Memicu pengujian error terkontrol secara aman untuk memverifikasi kemampuan pencatatan error di LogChat.
     * Mencoba membaca file tiruan yang sengaja corrupt/unsupported.
     */
    fun triggerControlledTestError(context: Context) {
        info(LogModule.AUDIO, "Memulai Uji Diagnostik Error Terkontrol...")
        updatePipeline(PipelineStage.DECODE, StepStatus.WARNING, "Memulai uji pembacaan audio korup terkontrol")
        
        try {
            val dummyInvalidUri = Uri.parse("file:///invalid/path/corrupt_audio_test.xyz")
            val decodeResult = com.autoremix.djslow.engine.AudioDecoder.decode(context, dummyInvalidUri)
            if (decodeResult.isFailure) {
                val exception = decodeResult.exceptionOrNull() ?: IllegalArgumentException("Unsupported audio format: xyz")
                error(
                    module = LogModule.AUDIO,
                    message = "Gagal membaca file audio: format berkas tidak didukung.",
                    throwable = exception,
                    detail = "Unsupported audio format or file not found (uji terkontrol berhasil menangkap error)",
                    stage = "DECODE",
                    file = "corrupt_audio_test.xyz"
                )
                updatePipeline(PipelineStage.DECODE, StepStatus.FAILED, "Unsupported audio format (uji terkontrol)")
            }
        } catch (ex: Exception) {
            error(
                module = LogModule.AUDIO,
                message = "Gagal membaca file audio (Uji terkontrol)",
                throwable = ex,
                detail = ex.message ?: "Format audio tidak valid",
                stage = "DECODE",
                file = "corrupt_audio_test.xyz"
            )
            updatePipeline(PipelineStage.DECODE, StepStatus.FAILED, "Unsupported audio format: ${ex.message}")
        }
    }

    fun flushSync() {
        val ctx = appContext ?: return
        try {
            val logDir = File(ctx.filesDir, "logchat")
            if (!logDir.exists()) logDir.mkdirs()
            val file = File(logDir, LOG_FILE_NAME)
            val jsonArray = JSONArray()
            _logs.value.takeLast(MAX_LOGS).forEach { entry ->
                val obj = JSONObject().apply {
                    put("id", entry.id)
                    put("timestamp", entry.timestamp)
                    put("level", entry.level.name)
                    put("module", entry.module.name)
                    put("message", entry.message)
                    put("detail", entry.detail ?: "")
                    put("stackTrace", entry.stackTrace ?: "")
                    put("pipelineStage", entry.pipelineStage ?: "")
                    put("associatedFile", entry.associatedFile ?: "")
                }
                jsonArray.put(obj)
            }
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            Log.e(TAG, "flushSync gagal: ${e.message}")
        }
    }

    private fun saveLogsToDisk(context: Context) {
        try {
            val logDir = File(context.filesDir, "logchat")
            if (!logDir.exists()) logDir.mkdirs()
            val file = File(logDir, LOG_FILE_NAME)
            val jsonArray = JSONArray()
            _logs.value.takeLast(MAX_LOGS).forEach { entry ->
                val obj = JSONObject().apply {
                    put("id", entry.id)
                    put("timestamp", entry.timestamp)
                    put("level", entry.level.name)
                    put("module", entry.module.name)
                    put("message", entry.message)
                    put("detail", entry.detail ?: "")
                    put("stackTrace", entry.stackTrace ?: "")
                    put("pipelineStage", entry.pipelineStage ?: "")
                    put("associatedFile", entry.associatedFile ?: "")
                }
                jsonArray.put(obj)
            }
            file.writeText(jsonArray.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menyimpan log ke disk: ${e.message}")
        }
    }

    private fun loadPersistedLogs(context: Context) {
        try {
            val file = File(context.filesDir, "logchat/$LOG_FILE_NAME")
            if (!file.exists()) return

            val jsonString = file.readText()
            if (jsonString.isBlank()) return

            val jsonArray = JSONArray(jsonString)
            val loaded = ArrayList<LogEntry>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val levelStr = obj.optString("level", "INFO")
                val moduleStr = obj.optString("module", "SYSTEM")
                val level = try { LogLevel.valueOf(levelStr) } catch (e: Exception) { LogLevel.INFO }
                val module = try { LogModule.valueOf(moduleStr) } catch (e: Exception) { LogModule.SYSTEM }

                loaded.add(
                    LogEntry(
                        id = obj.optString("id"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        level = level,
                        module = module,
                        message = obj.optString("message", ""),
                        detail = obj.optString("detail").ifBlank { null },
                        stackTrace = obj.optString("stackTrace").ifBlank { null },
                        pipelineStage = obj.optString("pipelineStage").ifBlank { null },
                        associatedFile = obj.optString("associatedFile").ifBlank { null }
                    )
                )
            }
            if (loaded.isNotEmpty()) {
                _logs.update { existing ->
                    val combined = loaded + existing
                    if (combined.size > MAX_LOGS) combined.takeLast(MAX_LOGS) else combined
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memuat log yang tersimpan: ${e.message}")
        }
    }

    private fun checkLastCrash(context: Context) {
        try {
            val crashFile = File(context.filesDir, "logchat/last_crash.txt")
            if (crashFile.exists()) {
                val report = crashFile.readText()
                if (report.isNotBlank()) {
                    _lastCrashReport.value = report
                    log(
                        level = LogLevel.CRITICAL,
                        module = LogModule.SYSTEM,
                        message = "CATATAN CRASH SEBELUMNYA DITEMUKAN",
                        detail = report,
                        pipelineStage = "SYSTEM"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memeriksa crash log: ${e.message}")
        }
    }

    private fun mapStageToModule(stage: PipelineStage): LogModule {
        return when (stage) {
            PipelineStage.INPUT, PipelineStage.DECODE -> LogModule.AUDIO
            PipelineStage.ANALYSIS -> LogModule.BPM
            PipelineStage.MUSICAL_MAP -> LogModule.KEY
            PipelineStage.REMIX -> LogModule.REMIX
            PipelineStage.ARRANGEMENT, PipelineStage.GENERATOR, PipelineStage.VOCAL_FX -> LogModule.ARRANGEMENT
            PipelineStage.MIX -> LogModule.MIX
            PipelineStage.MASTER -> LogModule.MASTER
            PipelineStage.EXPORT -> LogModule.EXPORT
            PipelineStage.PLAYBACK -> LogModule.PLAYBACK
        }
    }
}
