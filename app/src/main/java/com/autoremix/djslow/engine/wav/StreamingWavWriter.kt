package com.autoremix.djslow.engine.wav

import com.autoremix.djslow.engine.pcm.AudioPcmData
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * STREAMING WAV WRITER
 * Menulis streaming audio PCM Float32 stereo langsung ke berkas RIFF WAV 16-bit 44.1 kHz
 * secara chunk-by-chunk tanpa perlu mengalokasikan array FloatArray raksasa di memori RAM.
 *
 * Header diperbarui otomatis saat stream ditutup (close).
 */
class StreamingWavWriter(
    val targetFile: File,
    val sampleRate: Int = 44100,
    val channels: Int = 2
) : Closeable {

    private val bitsPerSample = 16
    private val bytesPerSample = bitsPerSample / 8
    private val blockAlign = channels * bytesPerSample
    private val byteRate = sampleRate * blockAlign

    private val raf: RandomAccessFile
    private var totalBytesWritten: Long = 0L
    private var isClosed = false

    // Reusable byte buffer for chunk encoding (e.g. 8192 frames = 32KB buffer)
    private val writeBuffer = ByteArray(8192 * channels * bytesPerSample)
    private val byteBuffer = ByteBuffer.wrap(writeBuffer).order(ByteOrder.LITTLE_ENDIAN)

    init {
        targetFile.parentFile?.mkdirs()
        if (targetFile.exists()) {
            targetFile.delete()
        }
        raf = RandomAccessFile(targetFile, "rw")
        writeHeaderPlaceholder()
    }

    private fun writeHeaderPlaceholder() {
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        // Chunk ID "RIFF"
        header.put('R'.code.toByte())
        header.put('I'.code.toByte())
        header.put('F'.code.toByte())
        header.put('F'.code.toByte())
        header.putInt(36) // Temporary placeholder
        // Format "WAVE"
        header.put('W'.code.toByte())
        header.put('A'.code.toByte())
        header.put('V'.code.toByte())
        header.put('E'.code.toByte())

        // Subchunk 1 "fmt "
        header.put('f'.code.toByte())
        header.put('m'.code.toByte())
        header.put('t'.code.toByte())
        header.put(' '.code.toByte())
        header.putInt(16) // Subchunk1Size = 16
        header.putShort(1) // PCM format
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bitsPerSample.toShort())

        // Subchunk 2 "data"
        header.put('d'.code.toByte())
        header.put('a'.code.toByte())
        header.put('t'.code.toByte())
        header.put('a'.code.toByte())
        header.putInt(0) // Temporary placeholder

        raf.write(header.array())
    }

    /**
     * Menulis satu chunk sampel Float32 interleaved [samples] ke berkas WAV.
     */
    fun writeChunk(samples: FloatArray, offset: Int = 0, count: Int = samples.size) {
        if (isClosed) throw IllegalStateException("StreamingWavWriter sudah ditutup.")
        if (count <= 0) return

        var sampleIdx = offset
        val endIdx = offset + count

        while (sampleIdx < endIdx) {
            byteBuffer.clear()
            val samplesToProcess = minOf((endIdx - sampleIdx), writeBuffer.size / bytesPerSample)
            for (i in 0 until samplesToProcess) {
                val s = samples[sampleIdx++].coerceIn(-1.0f, 1.0f)
                val s16 = (s * 32767.0f).toInt().toShort()
                byteBuffer.putShort(s16)
            }
            val bytesToWrite = samplesToProcess * bytesPerSample
            raf.write(writeBuffer, 0, bytesToWrite)
            totalBytesWritten += bytesToWrite
        }
    }

    fun writeChunk(pcm: AudioPcmData) {
        writeChunk(pcm.samples, 0, pcm.samples.size)
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        try {
            // Perbarui RIFF header dengan ukuran data riil
            val dataSize = totalBytesWritten
            val chunkSize = 36L + dataSize

            // Tulis chunkSize di offset 4
            raf.seek(4)
            val chunkSizeBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(chunkSize.toInt()).array()
            raf.write(chunkSizeBytes)

            // Tulis dataSize di offset 40
            raf.seek(40)
            val dataSizeBytes = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(dataSize.toInt()).array()
            raf.write(dataSizeBytes)
        } finally {
            raf.close()
        }
    }
}
