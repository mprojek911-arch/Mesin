package com.autoremix.djslow.logchat

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class CrashHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        throwable.printStackTrace(pw)
        val stackTraceString = sw.toString()

        val crashReport = """
            THREAD: ${thread.name} (id: ${thread.id})
            TIME: ${System.currentTimeMillis()}
            EXCEPTION: ${throwable.javaClass.name}
            MESSAGE: ${throwable.message ?: "No message"}
            STACK TRACE:
            $stackTraceString
        """.trimIndent()

        Log.e("CrashHandler", "CRASH DETECTED:\n$crashReport")

        try {
            // Simpan dump darurat ke disk secara synchronous
            val logDir = File(context.filesDir, "logchat")
            if (!logDir.exists()) logDir.mkdirs()
            val crashFile = File(logDir, "last_crash.txt")
            crashFile.writeText(crashReport)

            // Catat ke LogChatManager
            LogChatManager.log(
                level = LogLevel.CRITICAL,
                module = LogModule.SYSTEM,
                message = "CRASH: Uncaught exception di ${thread.name} - ${throwable.message ?: throwable.javaClass.simpleName}",
                detail = "Exception: ${throwable.javaClass.name}\nMessage: ${throwable.message}",
                stackTrace = stackTraceString,
                pipelineStage = "SYSTEM"
            )
            LogChatManager.flushSync()
        } catch (e: Exception) {
            Log.e("CrashHandler", "Gagal menyimpan crash report: ${e.message}")
        }

        defaultHandler?.uncaughtException(thread, throwable)
    }

    companion object {
        fun install(context: Context) {
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (previousHandler !is CrashHandler) {
                Thread.setDefaultUncaughtExceptionHandler(CrashHandler(context.applicationContext, previousHandler))
            }
        }
    }
}
