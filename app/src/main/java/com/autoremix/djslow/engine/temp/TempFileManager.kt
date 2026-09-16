package com.autoremix.djslow.engine.temp

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Pengelola berkas sementara (Temporary File Management) untuk pipeline render studio.
 * Memastikan media penyimpanan perangkat tidak penuh dan berkas setengah jadi langsung dihapus jika dibatalkan atau gagal.
 *
 * Alur Pipeline:
 * INPUT -> TEMP -> PROCESS -> RENDER -> VALIDATE -> FINAL OUTPUT -> CLEAN TEMP
 */
object TempFileManager {

    private const val TEMP_DIR_NAME = "remix_temp"

    /**
     * Mendapatkan atau membuat direktori berkas sementara.
     */
    fun getTempDir(context: Context): File {
        val dir = File(context.cacheDir, TEMP_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Membuat berkas sementara baru dengan prefix tertentu.
     */
    fun createTempFile(context: Context, prefix: String, extension: String = ".tmp"): File {
        val dir = getTempDir(context)
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault()).format(Date())
        return File(dir, "${prefix}_$timestamp$extension")
    }

    /**
     * Menghapus seluruh berkas sementara di dalam direktori cache aplikasi.
     * Tidak akan menghapus berkas hasil render final yang tersimpan di direktori permanen.
     */
    fun cleanAllTempFiles(context: Context): Int {
        var deletedCount = 0
        try {
            val dir = getTempDir(context)
            val files = dir.listFiles()
            if (files != null) {
                for (file in files) {
                    if (file.isFile) {
                        if (file.delete()) {
                            deletedCount++
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return deletedCount
    }

    /**
     * Menghapus satu berkas secara aman jika ada.
     */
    fun deleteSafe(file: File?): Boolean {
        return try {
            if (file != null && file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
