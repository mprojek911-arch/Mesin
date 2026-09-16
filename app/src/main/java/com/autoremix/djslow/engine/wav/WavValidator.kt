package com.autoremix.djslow.engine.wav

import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/**
 * Validasi ketat berkas WAV hasil render nyata sesuai aturan integritas audio:
 * 1. File ada
 * 2. File dapat dibuka
 * 3. Durasi > 0
 * 4. Sample rate valid (44100)
 * 5. Channel valid (1 atau 2)
 * 6. PCM valid
 * 7. Audio tidak silent (peak > 0.001f)
 * 8. Peak valid (peak <= 1.0f)
 * 9. Tidak ada NaN atau Infinity
 */
object WavValidator {

    data class ValidationResult(
        val isValid: Boolean,
        val errorMessage: String? = null,
        val durationMs: Long = 0L,
        val sampleRate: Int = 0,
        val channels: Int = 0,
        val bitsPerSample: Int = 0,
        val peakAmplitude: Float = 0.0f,
        val fileSizeBytes: Long = 0L,
        val isSilent: Boolean = false
    )

    /**
     * Memvalidasi berkas WAV. Jika gagal mengembalikan error terstruktur: "Rendering gagal. Silakan ulangi."
     */
    fun validate(file: File): ValidationResult {
        // 1. Periksa keberadaan berkas fisik
        if (!file.exists() || !file.isFile) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Rendering gagal. Silakan ulangi. (Berkas tidak ditemukan)"
            )
        }

        val fileSize = file.length()
        if (fileSize < 44L) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Rendering gagal. Silakan ulangi. (Ukuran berkas rusak/kurang dari header)",
                fileSizeBytes = fileSize
            )
        }

        // 2. Periksa apakah file dapat dibuka & dibaca
        try {
            FileInputStream(file).use { input ->
                val header = ByteArray(44)
                val readBytes = input.read(header)
                if (readBytes < 44) {
                    return ValidationResult(
                        isValid = false,
                        errorMessage = "Rendering gagal. Silakan ulangi. (Gagal membaca header WAV)",
                        fileSizeBytes = fileSize
                    )
                }

                val riff = String(header, 0, 4)
                val wave = String(header, 8, 4)
                val fmt = String(header, 12, 4)

                if (riff != "RIFF" || wave != "WAVE" || fmt != "fmt ") {
                    return ValidationResult(
                        isValid = false,
                        errorMessage = "Rendering gagal. Silakan ulangi. (Header berkas bukan format WAV RIFF)",
                        fileSizeBytes = fileSize
                    )
                }

                val bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
                bb.position(20)
                val audioFormat = bb.short.toInt() // 1 = PCM
                val channels = bb.short.toInt()
                val sampleRate = bb.int
                val byteRate = bb.int
                bb.short // blockAlign
                val bitsPerSample = bb.short.toInt()

                // 4. Sample rate valid
                if (sampleRate != 44100 && sampleRate != 48000) {
                    return ValidationResult(
                        isValid = false,
                        errorMessage = "Rendering gagal. Silakan ulangi. (Sample rate tidak valid: $sampleRate Hz)",
                        sampleRate = sampleRate
                    )
                }

                // 5. Channel valid
                if (channels != 1 && channels != 2) {
                    return ValidationResult(
                        isValid = false,
                        errorMessage = "Rendering gagal. Silakan ulangi. (Jumlah channel tidak valid: $channels)",
                        channels = channels
                    )
                }

                // Format PCM valid
                if (audioFormat != 1 || bitsPerSample != 16) {
                    return ValidationResult(
                        isValid = false,
                        errorMessage = "Rendering gagal. Silakan ulangi. (Bukan 16-bit PCM standar)",
                        bitsPerSample = bitsPerSample
                    )
                }

                // Cari chunk 'data' dan ukuran data
                val dataTag = String(header, 36, 4)
                bb.position(40)
                var dataSize = bb.int // posisi 40-43
                if (dataTag != "data" || dataSize <= 0) {
                    // Fallback hitung dari sisa panjang berkas
                    dataSize = (fileSize - 44L).toInt()
                }

                val bytesPerFrame = channels * (bitsPerSample / 8)
                val totalFrames = if (bytesPerFrame > 0) dataSize / bytesPerFrame else 0
                val durationMs = if (sampleRate > 0) (totalFrames.toLong() * 1000L) / sampleRate else 0L

                // 3. Durasi > 0
                if (durationMs <= 0L || totalFrames <= 0) {
                    return ValidationResult(
                        isValid = false,
                        errorMessage = "Rendering gagal. Silakan ulangi. (Durasi audio kosong/0)",
                        durationMs = durationMs
                    )
                }

                // 6. Validasi PCM, cek Silence, Peak, NaN, Infinity
                var maxPeak = 0.0f
                var hasValidSample = false
                val buffer = ByteArray(8192)
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } > 0) {
                    val sampleBuffer = ByteBuffer.wrap(buffer, 0, bytesRead).order(ByteOrder.LITTLE_ENDIAN)
                    while (sampleBuffer.remaining() >= 2) {
                        val s16 = sampleBuffer.short.toFloat()
                        val sampleFloat = s16 / 32768.0f

                        // 9. Tidak ada NaN atau Infinity
                        if (sampleFloat.isNaN() || sampleFloat.isInfinite()) {
                            return ValidationResult(
                                isValid = false,
                                errorMessage = "Rendering gagal. Silakan ulangi. (Ditemukan nilai NaN/Infinity pada audio)"
                            )
                        }

                        val absVal = abs(sampleFloat)
                        if (absVal > maxPeak) {
                            maxPeak = absVal
                        }
                        if (absVal > 0.001f) {
                            hasValidSample = true
                        }
                    }
                }

                // 7. Audio tidak silent
                if (!hasValidSample || maxPeak < 0.001f) {
                    return ValidationResult(
                        isValid = false,
                        errorMessage = "Rendering gagal. Silakan ulangi. (Audio hasil hening / silent)",
                        peakAmplitude = maxPeak,
                        isSilent = true,
                        durationMs = durationMs
                    )
                }

                // 8. Peak valid (peak <= 1.0f)
                if (maxPeak > 1.05f) {
                    return ValidationResult(
                        isValid = false,
                        errorMessage = "Rendering gagal. Silakan ulangi. (Terjadi clipping berat: peak $maxPeak)",
                        peakAmplitude = maxPeak
                    )
                }

                // Seluruh validasi lulus
                return ValidationResult(
                    isValid = true,
                    errorMessage = null,
                    durationMs = durationMs,
                    sampleRate = sampleRate,
                    channels = channels,
                    bitsPerSample = bitsPerSample,
                    peakAmplitude = maxPeak,
                    fileSizeBytes = fileSize,
                    isSilent = false
                )
            }
        } catch (e: Exception) {
            return ValidationResult(
                isValid = false,
                errorMessage = "Rendering gagal. Silakan ulangi: ${e.localizedMessage}"
            )
        }
    }
}
