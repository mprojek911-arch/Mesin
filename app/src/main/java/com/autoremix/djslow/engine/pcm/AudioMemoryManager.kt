package com.autoremix.djslow.engine.pcm

import com.autoremix.djslow.logchat.LogChatManager
import com.autoremix.djslow.logchat.LogModule

/**
 * AUDIO MEMORY MANAGER
 * Mengelola kebijakan memori, batas alokasi buffer audio, pemantauan heap runtime,
 * serta penentuan strategi streaming / chunk processing untuk mencegah OutOfMemoryError (OOM).
 */
object AudioMemoryManager {

    const val CHUNK_DURATION_DEFAULT_MS = 500
    const val CHUNK_DURATION_LOW_RAM_MS = 250
    const val CHUNK_DURATION_HIGH_RAM_MS = 1000

    // Batas durasi (ms) di mana audio wajib dialihkan ke mode streaming chunk
    // Lagu di atas 30 detik selalu menggunakan streaming / chunk processing
    const val STREAMING_THRESHOLD_MS = 30_000L

    // Ambang batas sisa memori minimum (MB) sebelum memicu pembersihan darurat
    private const val MIN_FREE_HEAP_MB = 36L

    data class MemorySnapshot(
        val maxMemoryMb: Long,
        val totalMemoryMb: Long,
        val freeMemoryMb: Long,
        val availableHeapMb: Long
    )

    fun getMemorySnapshot(): MemorySnapshot {
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val totalMemory = runtime.totalMemory() / (1024 * 1024)
        val freeMemory = runtime.freeMemory() / (1024 * 1024)
        val availableHeap = maxMemory - (totalMemory - freeMemory)
        return MemorySnapshot(
            maxMemoryMb = maxMemory,
            totalMemoryMb = totalMemory,
            freeMemoryMb = freeMemory,
            availableHeapMb = availableHeap
        )
    }

    /**
     * Mencatat status pemakaian memori ke LogChat dan sistem logcat.
     */
    fun logMemoryUsage(
        contextTag: String,
        blockFrames: Int = 0,
        activeBuffers: Int = 0,
        estimatedPcmMb: Double = 0.0
    ) {
        val snap = getMemorySnapshot()
        val heapUsedMb = snap.totalMemoryMb - snap.freeMemoryMb
        val extraInfo = buildString {
            if (blockFrames > 0) append(" BlockFrames=$blockFrames")
            if (activeBuffers > 0) append(" ActiveBuffers=$activeBuffers")
            if (estimatedPcmMb > 0.0) append(String.format(" EstPcm=%.1fMB", estimatedPcmMb))
        }
        LogChatManager.info(
            module = LogModule.SYSTEM,
            message = "[INFO] [MEMORY] $contextTag: HeapUsed=${heapUsedMb}MB / Max=${snap.maxMemoryMb}MB (Avail=${snap.availableHeapMb}MB, Free=${snap.freeMemoryMb}MB)$extraInfo"
        )
    }

    /**
     * Mencatat telemetri memori per blok sesuai format standar FASE 19:
     * [MEMORY] stage=... blockStartFrame=... blockFrames=... heapUsedMB=... heapMaxMB=...
     */
    fun logBlockMemory(
        stage: String,
        blockStartFrame: Long,
        blockFrames: Int
    ) {
        val snap = getMemorySnapshot()
        val heapUsedMb = snap.totalMemoryMb - snap.freeMemoryMb
        LogChatManager.info(
            module = LogModule.SYSTEM,
            message = "[MEMORY] stage=$stage blockStartFrame=$blockStartFrame blockFrames=$blockFrames heapUsedMB=$heapUsedMb heapMaxMB=${snap.maxMemoryMb}"
        )
    }

    /**
     * Memeriksa apakah durasi audio dan jumlah layer instrumen memerlukan streaming.
     */
    fun shouldUseStreaming(durationMs: Long, sampleRate: Int = 44100, channels: Int = 2): Boolean {
        if (durationMs > STREAMING_THRESHOLD_MS) return true

        // Estimasi byte jika seluruh 6 layer instrumen dialokasikan
        // 6 layers * duration * sampleRate * channels * 4 bytes per float
        val totalFloatsPerTrack = (durationMs * sampleRate / 1000) * channels
        val estimatedTotalBytes = totalFloatsPerTrack * 4 * 6
        val snap = getMemorySnapshot()

        // Jika alokasi melebihi 25% heap tersedia, gunakan streaming
        val safeThresholdBytes = (snap.availableHeapMb * 1024 * 1024) / 4
        return estimatedTotalBytes > safeThresholdBytes
    }

    /**
     * Menghitung ukuran frame untuk satu chunk pemrosesan audio berdasarkan kondisi heap saat ini.
     */
    fun getRecommendedChunkFrames(sampleRate: Int = 44100): Int {
        val snap = getMemorySnapshot()
        val chunkDurationMs = when {
            snap.availableHeapMb < 48 -> CHUNK_DURATION_LOW_RAM_MS
            snap.availableHeapMb > 160 -> CHUNK_DURATION_HIGH_RAM_MS
            else -> CHUNK_DURATION_DEFAULT_MS
        }
        return (sampleRate * chunkDurationMs / 1000).coerceAtLeast(1024)
    }

    /**
     * Melakukan pembersihan buffer pool jika memori kritis di bawah batas aman.
     * Tanpa memanggil System.gc() manual agar performa audio thread tetap deterministik.
     */
    fun trimMemoryIfNeeded(tag: String = "Engine") {
        val snap = getMemorySnapshot()
        if (snap.availableHeapMb < MIN_FREE_HEAP_MB) {
            LogChatManager.warn(
                module = LogModule.SYSTEM,
                message = "[WARNING] [MEMORY] $tag: Memori kritis (${snap.availableHeapMb}MB tersedia). Membersihkan AudioBufferPool."
            )
            AudioBufferPool.clear()
        }
    }
}
