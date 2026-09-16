package com.autoremix.djslow.engine.wav

import com.autoremix.djslow.engine.pcm.AudioPcmData
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * WAV Renderer nyata yang menulis buffer PCM Float32 ke dalam berkas standar RIFF WAV 16-bit PCM 44.1 kHz stereo.
 */
object WavRenderer {

    /**
     * Merender [AudioPcmData] ke berkas RIFF WAV pada [targetFile].
     */
    fun render(
        pcmData: AudioPcmData,
        targetFile: File,
        onProgress: ((Float, String) -> Unit)? = null
    ): Result<File> {
        val totalSamples = pcmData.samples.size
        if (totalSamples == 0) {
            return Result.failure(IllegalArgumentException("Buffer audio kosong, tidak dapat merender WAV."))
        }

        // Pastikan direktori induk tersedia
        targetFile.parentFile?.mkdirs()
        if (targetFile.exists()) {
            targetFile.delete()
        }

        onProgress?.invoke(0.05f, "Menyiapkan berkas WAV...")

        val sampleRate = pcmData.sampleRate
        val channels = pcmData.channels
        val bitsPerSample = 16
        val bytesPerSample = bitsPerSample / 8
        val blockAlign = channels * bytesPerSample
        val byteRate = sampleRate * blockAlign
        val dataSize = totalSamples * bytesPerSample
        val chunkSize = 36 + dataSize

        try {
            BufferedOutputStream(FileOutputStream(targetFile), 65536).use { out ->
                // 1. Tulis 44-byte RIFF Header
                val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)

                // Chunk ID "RIFF"
                header.put('R'.code.toByte())
                header.put('I'.code.toByte())
                header.put('F'.code.toByte())
                header.put('F'.code.toByte())

                header.putInt(chunkSize)

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

                header.putInt(16) // Subchunk1Size = 16 untuk PCM
                header.putShort(1) // AudioFormat = 1 (PCM)
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

                header.putInt(dataSize)

                out.write(header.array())

                onProgress?.invoke(0.15f, "Menulis aliran PCM 16-bit ke WAV...")

                // 2. Tulis sampel audio dalam blok buffer untuk kecepatan dan efisiensi memori
                val bufferFrames = 4096
                val bufferSamples = bufferFrames * channels
                val byteChunk = ByteArray(bufferSamples * bytesPerSample)
                val byteBuffer = ByteBuffer.wrap(byteChunk).order(ByteOrder.LITTLE_ENDIAN)

                var sampleIndex = 0
                val totalFrames = pcmData.totalFrames

                while (sampleIndex < totalSamples) {
                    byteBuffer.clear()
                    val toProcess = minOf(bufferSamples, totalSamples - sampleIndex)

                    for (i in 0 until toProcess) {
                        val sampleFloat = pcmData.samples[sampleIndex + i]
                        // Konversi Float [-1.0, 1.0] ke Int16 Little-Endian
                        val clamped = sampleFloat.coerceIn(-1.0f, 1.0f)
                        val s16 = (clamped * 32767.0f).toInt().toShort()
                        byteBuffer.putShort(s16)
                    }

                    out.write(byteChunk, 0, toProcess * bytesPerSample)
                    sampleIndex += toProcess

                    val currentFrame = sampleIndex / channels
                    val progress = 0.15f + 0.80f * (currentFrame.toFloat() / totalFrames.toFloat())
                    onProgress?.invoke(progress, "Rendering WAV ($currentFrame / $totalFrames frame)...")
                }

                out.flush()
            }

            onProgress?.invoke(1.0f, "Render berkas WAV selesai.")
            return Result.success(targetFile)

        } catch (e: Exception) {
            if (targetFile.exists()) {
                targetFile.delete()
            }
            return Result.failure(IllegalStateException("Rendering gagal. Silakan ulangi: ${e.localizedMessage}"))
        }
    }
}
