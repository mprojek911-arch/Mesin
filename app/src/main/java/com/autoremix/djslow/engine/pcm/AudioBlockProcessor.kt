package com.autoremix.djslow.engine.pcm

/**
 * AUDIO BLOCK PROCESSOR
 * Utilitas pemrosesan audio per blok (chunked processing) untuk durasi panjang (30s - 300s+).
 * Membagi aliran waktu menjadi blok-blok kecil (default 16384 frames = ~371ms @ 44.1 kHz).
 *
 * Mencegah full-track FloatArray allocation sekaligus menjaga kontinuitas state DSP antar-blok.
 */
object AudioBlockProcessor {

    const val DEFAULT_BLOCK_FRAMES = 16384 // 16384 frames * 2 channels = 32768 floats = 131 KB per buffer
    const val MIN_BLOCK_FRAMES = 4096
    const val MAX_BLOCK_FRAMES = 32768

    /**
     * Menghitung ukuran blok optimal berdasarkan kondisi heap perangkat saat ini.
     */
    fun determineOptimalBlockFrames(sampleRate: Int = 44100): Int {
        val snap = AudioMemoryManager.getMemorySnapshot()
        return when {
            snap.availableHeapMb < 48 -> 4096
            snap.availableHeapMb < 80 -> 8192
            snap.availableHeapMb > 180 -> 32768
            else -> DEFAULT_BLOCK_FRAMES
        }
    }

    /**
     * Memproses rentang frame [totalFrames] dalam pecahan blok sebesar [blockFrames].
     */
    inline fun iterateBlocks(
        totalFrames: Long,
        blockFrames: Int = DEFAULT_BLOCK_FRAMES,
        crossinline onBlock: (startFrame: Long, frameCount: Int, isLast: Boolean) -> Unit
    ) {
        if (totalFrames <= 0L) return
        var currentFrame = 0L
        while (currentFrame < totalFrames) {
            val count = minOf(blockFrames.toLong(), totalFrames - currentFrame).toInt()
            val isLast = (currentFrame + count) >= totalFrames
            onBlock(currentFrame, count, isLast)
            currentFrame += count
        }
    }
}
