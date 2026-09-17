package com.autoremix.djslow.engine.pcm

import com.autoremix.djslow.logchat.LogChatManager
import com.autoremix.djslow.logchat.LogModule

/**
 * AUDIO MEMORY GUARD
 * Menjaga batas alokasi memori heap, mendeteksi tekanan memori tinggi,
 * dan menangani potensi OutOfMemoryError secara elegan tanpa crash.
 */
object AudioMemoryGuard {

    const val CRITICAL_HEAP_MB = 32L

    /**
     * Memeriksa apakah kapasitas heap memadai untuk alokasi sebesar [requiredBytesEstimate].
     */
    fun hasSufficientMemory(requiredBytesEstimate: Long = 0L): Boolean {
        val snap = AudioMemoryManager.getMemorySnapshot()
        val availableBytes = snap.availableHeapMb * 1024 * 1024
        return (snap.availableHeapMb >= CRITICAL_HEAP_MB) && (requiredBytesEstimate < (availableBytes * 3 / 4))
    }

    /**
     * Menjalankan operasi audio di dalam perlindungan memori.
     * Jika terjadi OutOfMemoryError, heap dibersihkan dan Result.failure dikembalikan tanpa crash.
     */
    inline fun <T> runGuarded(
        stage: String,
        block: () -> Result<T>
    ): Result<T> {
        val snap = AudioMemoryManager.getMemorySnapshot()
        if (snap.availableHeapMb < CRITICAL_HEAP_MB) {
            System.gc()
        }

        return try {
            block()
        } catch (oom: OutOfMemoryError) {
            AudioBufferPool.clear()
            System.gc()
            val msg = "Tekanan memori tinggi (OOM) pada tahap '$stage'. Sistem telah membebaskan buffer."
            LogChatManager.error(
                module = LogModule.SYSTEM,
                message = msg,
                throwable = Exception(oom),
                stage = stage
            )
            Result.failure(IllegalStateException("Memori perangkat tidak cukup untuk melanjutkan tahap $stage. Silakan ulangi dengan durasi lebih pendek atau tutup aplikasi latar."))
        } catch (t: Throwable) {
            LogChatManager.error(
                module = LogModule.SYSTEM,
                message = "Kegagalan pada tahap '$stage': ${t.localizedMessage}",
                throwable = Exception(t),
                stage = stage
            )
            Result.failure(if (t is Exception) t else IllegalStateException("Gagal memproses audio ($stage): ${t.localizedMessage ?: t.message}"))
        }
    }
}
