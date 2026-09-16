package com.autoremix.djslow.logchat

import android.content.Context
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class CrashHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    private val isHandlingCrash = AtomicBoolean(false)

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        // Mencegah loop rekursif jika terjadi kegagalan saat logging crash
        if (!isHandlingCrash.compareAndSet(false, true)) {
            defaultHandler?.uncaughtException(thread, throwable)
            return
        }

        try {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            throwable.printStackTrace(pw)
            val stackTraceString = sw.toString()
            val timestamp = System.currentTimeMillis()
            val formattedDate = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))

            val crashReport = """
                TIME: $formattedDate ($timestamp)
                THREAD: ${thread.name} (id: ${thread.id})
                EXCEPTION: ${throwable.javaClass.name}
                MESSAGE: ${throwable.message ?: "No message"}
                STACK TRACE:
                $stackTraceString
            """.trimIndent()

            Log.e("CrashHandler", "CRASH DETECTED:\n$crashReport")

            // Simpan dump darurat ke disk secara synchronous
            val logDir = File(context.filesDir, "logchat")
            if (!logDir.exists()) logDir.mkdirs()
            val crashFile = File(logDir, "last_crash.txt")
            crashFile.writeText(crashReport)

            // Catat ke LogChatManager dan paksa flush ke disk
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
        } finally {
            // Meneruskan ke default handler Android agar crash diproses standar oleh sistem OS
            defaultHandler?.uncaughtException(thread, throwable)
        }
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
