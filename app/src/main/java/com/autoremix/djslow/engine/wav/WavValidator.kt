package com.autoremix.djslow.engine.wav

import com.autoremix.djslow.engine.dsp.LoudnessMeter
import com.autoremix.djslow.engine.pcm.AudioPcmData
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.log10

/**
 * Validasi ketat berkas WAV hasil render nyata sesuai aturan integritas audio Tahap 5:
 * 1. File ada & dapat dibuka
 * 2. Durasi > 0 ms
 * 3. Sample rate valid (44100 / 48000 Hz)
 * 4. Channel valid (Stereo 2 / Mono 1)
 * 5. Format PCM 16-bit RIFF valid
 * 6. Audio tidak silent (peak > 0.001f)
 * 7. Peak valid (peak <= 1.0f, batas toleransi <= 1.01f)
 * 8. Tidak ada nilai NaN atau Infinity
 * 9. Pengukuran Loudness: LUFS Integrated, True Peak (dBTP), RMS (dBFS), Peak (dBFS)
 * 10. Jika terjadi clipping/kerusakan, RENDER = FAIL ("Rendering gagal. Silakan ulangi.")
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
        val peakDbfs: Float = -96.0f,
        val rmsDbfs: Float = -96.0f,
        val lufsIntegrated: Float = -70.0f,
        val truePeakDbtp: Float = -96.0f,
        val fileSizeBytes: Long = 0L,
        val isSilent: Boolean = false,
        val isClipping: Boolean = false
    ) {
        val formattedLufs: String
            get() = String.format("%.1f LUFS", lufsIntegrated)

        val formattedTruePeak: String
            get() = String.format("%.2f dBTP", truePeakDbtp)

        val formattedPeak: String
            get() = String.format("%.2f dBFS (%.2f)", peakDbfs, peakAmplitude)

        val formattedRms: String
            get() = String.format("%.1f dBFS", rmsDbfs)
    }

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
                bb.int // byteRate
                bb.short // blockAlign
                val bitsPerSample = bb.short.toInt()

                // 3. Sample rate valid
                if (sampleRate != 44100 && sampleRate != 48000) {
                    return ValidationResult(
                        isValid = false,
                        errorMessage = "Rendering gagal. Silakan ulangi. (Sample rate tidak valid: $sampleRate Hz)",
                        sampleRate = sampleRate
                    )
                }

                // 4. Channel valid
                if (channels != 1 && channels != 2) {
                    return ValidationResult(
                        isValid = false,
                        errorMessage = "Rendering gagal. Silakan ulangi. (Jumlah channel tidak valid: $channels)",
                        channels = channels
                    )
                }

                // 5. Format PCM valid
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
                var dataSize = bb.int
                if (dataTag != "data" || dataSize <= 0) {
                    dataSize = (fileSize - 44L).toInt()
                }

                val bytesPerFrame = channels * (bitsPerSample / 8)
                val totalFrames = if (bytesPerFrame > 0) dataSize / bytesPerFrame else 0
                val durationMs = if (sampleRate > 0) (totalFrames.toLong() * 1000L) / sampleRate else 0L

                // 6. Durasi > 0
                if (durationMs <= 0L || totalFrames <= 0) {
                    return ValidationResult(
                        isValid = false,
                        errorMessage = "Rendering gagal. Silakan ulangi. (Durasi audio kosong/0)",
                        durationMs = durationMs
                    )
                }

                // 7. Ekstraksi seluruh sampel PCM Float untuk analisis Loudness & Anti-Clipping
                val sampleCount = totalFrames * channels
                val samples = FloatArray(sampleCount)
                val buffer = ByteArray(8192)
                var bytesRead: Int
                var sampleIndex = 0

                while (input.read(buffer).also { bytesRead = it } > 0 && sampleIndex < sampleCount) {
                    val sampleBuffer = ByteBuffer.wrap(buffer, 0, bytesRead).order(ByteOrder.LITTLE_ENDIAN)
                    while (sampleBuffer.remaining() >= 2 && sampleIndex < sampleCount) {
                        val s16 = sampleBuffer.short.toFloat()
                        val s = s16 / 32768.0f

                        // 8. Cek NaN / Infinity
                        if (s.isNaN() || s.isInfinite()) {
                            return ValidationResult(
                                isValid = false,
                                errorMessage = "Rendering gagal. Silakan ulangi. (Ditemukan nilai NaN/Infinity pada audio)"
                            )
                        }

                        samples[sampleIndex++] = s
                    }
                }

                // 9. Analisis Loudness Nyata (LUFS, True Peak, RMS, Peak)
                val pcmData = AudioPcmData(samples, sampleRate, channels)
                val report = LoudnessMeter.analyze(pcmData)

                // 10. Cek Silence
                if (report.peakLinear < 0.001f) {
                    return ValidationResult(
                        isValid = false,
                        errorMessage = "Rendering gagal. Silakan ulangi. (Audio hasil hening / silent)",
                        peakAmplitude = report.peakLinear,
                        isSilent = true,
                        durationMs = durationMs
                    )
                }

                // 11. Cek Anti-Clipping Ketat (Peak > 1.0f)
                if (report.peakLinear > 1.005f || report.isClipping) {
                    return ValidationResult(
                        isValid = false,
                        errorMessage = "Rendering gagal. Silakan ulangi. (Terdeteksi clipping audio: Peak ${String.format("%.2f", report.peakLinear)} / ${report.formattedTruePeak})",
                        peakAmplitude = report.peakLinear,
                        peakDbfs = report.peakDbfs,
                        rmsDbfs = report.rmsDbfs,
                        lufsIntegrated = report.lufsIntegrated,
                        truePeakDbtp = report.truePeakDbtp,
                        isClipping = true
                    )
                }

                // Lolos Seluruh Validasi Kualitas Audio
                return ValidationResult(
                    isValid = true,
                    errorMessage = null,
                    durationMs = durationMs,
                    sampleRate = sampleRate,
                    channels = channels,
                    bitsPerSample = bitsPerSample,
                    peakAmplitude = report.peakLinear,
                    peakDbfs = report.peakDbfs,
                    rmsDbfs = report.rmsDbfs,
                    lufsIntegrated = report.lufsIntegrated,
                    truePeakDbtp = report.truePeakDbtp,
                    fileSizeBytes = fileSize,
                    isSilent = false,
                    isClipping = false
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
