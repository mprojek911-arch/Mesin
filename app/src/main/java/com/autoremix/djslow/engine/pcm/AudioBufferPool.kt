package com.autoremix.djslow.engine.pcm

/**
 * REUSABLE AUDIO BUFFER POOL
 * Pool buffer FloatArray reusable untuk pemrosesan audio berbasis blok / chunk.
 * Mencegah ribuan alokasi FloatArray baru per detik dan melindungi heap Android dari OutOfMemoryError.
 */
object AudioBufferPool {

    private const val MAX_POOL_BUFFERS = 16
    private val pool = ArrayDeque<FloatArray>()
    private val lock = Any()

    /**
     * Mengambil buffer FloatArray dengan ukuran minimal [minSize] (samples).
     * Buffer otomatis dikosongkan (diisi 0.0f) sebelum digunakan.
     */
    fun acquire(minSize: Int): FloatArray {
        synchronized(lock) {
            val iterator = pool.iterator()
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                if (candidate.size >= minSize) {
                    iterator.remove()
                    candidate.fill(0.0f)
                    return candidate
                }
            }
        }
        return FloatArray(minSize)
    }

    /**
     * Mengembalikan buffer FloatArray ke pool untuk digunakan kembali oleh proses berikutnya.
     */
    fun release(buffer: FloatArray?) {
        if (buffer == null) return
        synchronized(lock) {
            if (pool.size < MAX_POOL_BUFFERS) {
                pool.add(buffer)
            }
        }
    }

    /**
     * Mengosongkan seluruh buffer yang disimpan dalam pool untuk membebaskan heap RAM.
     */
    fun clear() {
        synchronized(lock) {
            pool.clear()
        }
    }

    /**
     * Jumlah buffer yang saat ini menganggur dalam pool.
     */
    fun getPoolSize(): Int {
        synchronized(lock) {
            return pool.size
        }
    }
}
