package com.autoremix.djslow.engine.pcm

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor

/**
 * Audio PCM Decoder nyata yang mendekode audio (MP3, WAV, M4A, AAC, dsb.)
 * menjadi data PCM Float32 stereo pada frekuensi standar 44.1 kHz (44100 Hz).
 */
object AudioPcmDecoder {

    private const val TAG = "AudioPcmDecoder"
    const val TARGET_SAMPLE_RATE = 44100
    const val TARGET_CHANNELS = 2

    /**
     * Mendekode file audio dari URI SAF menjadi [AudioPcmData] Float32 stereo 44.1 kHz.
     */
    fun decodeToPcm(
        context: Context,
        uri: Uri,
        onProgress: ((Float, String) -> Unit)? = null
    ): Result<AudioPcmData> {
        onProgress?.invoke(0.05f, "Membuka berkas audio...")

        // Coba decode via direct WAV parsing terlebih dahulu jika file bertipe WAV
        val directWavResult = tryDecodeDirectWav(context, uri, onProgress)
        if (directWavResult != null && directWavResult.isSuccess) {
            return directWavResult
        }

        // Decode menggunakan Android MediaExtractor + MediaCodec
        val afd = try {
            context.contentResolver.openAssetFileDescriptor(uri, "r")
                ?: return Result.failure(IllegalStateException("File tidak dapat diakses."))
        } catch (e: Exception) {
            return Result.failure(IllegalStateException("Gagal membuka file audio: ${e.localizedMessage}"))
        }

        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            extractor.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            val audioTrackIndex = findAudioTrack(extractor)
            if (audioTrackIndex < 0) {
                return Result.failure(IllegalArgumentException("Tidak ditemukan trek audio yang valid."))
            }

            extractor.selectTrack(audioTrackIndex)
            val trackFormat = extractor.getTrackFormat(audioTrackIndex)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME)
                ?: return Result.failure(IllegalArgumentException("Format MIME audio tidak dikenal."))

            var sourceSampleRate = if (trackFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            } else 44100

            var sourceChannels = if (trackFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            } else 2

            val durationUs = if (trackFormat.containsKey(MediaFormat.KEY_DURATION)) {
                trackFormat.getLong(MediaFormat.KEY_DURATION)
            } else 0L

            onProgress?.invoke(0.15f, "Inisialisasi decoder $mime...")

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(trackFormat, null, null, 0)
            codec.start()

            val decodedRawPcmBytes = decodeLoop(extractor, codec, durationUs, onProgress)

            if (decodedRawPcmBytes.isEmpty()) {
                return Result.failure(IllegalStateException("Gagal mengekstrak sampel audio (data kosong)."))
            }

            // Periksa apakah format output codec mengubah sample rate atau channel
            try {
                val outputFormat = codec.outputFormat
                if (outputFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                    sourceSampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                }
                if (outputFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                    sourceChannels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                }
            } catch (_: Exception) {}

            onProgress?.invoke(0.80f, "Mengonversi ke PCM Float32 stereo 44.1 kHz...")

            val pcmData = convertRawPcmToFloat32Stereo(
                rawBytes = decodedRawPcmBytes,
                sourceSampleRate = sourceSampleRate,
                sourceChannels = sourceChannels,
                targetSampleRate = TARGET_SAMPLE_RATE
            )

            onProgress?.invoke(1.0f, "Perekaman PCM selesai (${pcmData.durationMs / 1000}s).")
            return Result.success(pcmData)

        } catch (e: Exception) {
            Log.e(TAG, "Gagal decode audio via MediaCodec: ${e.message}", e)
            return Result.failure(IllegalStateException("Gagal mendekode audio: ${e.localizedMessage}"))
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
            try { afd.close() } catch (_: Exception) {}
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                return i
            }
        }
        return -1
    }

    private fun decodeLoop(
        extractor: MediaExtractor,
        codec: MediaCodec,
        durationUs: Long,
        onProgress: ((Float, String) -> Unit)?
    ): ByteArray {
        val kTimeoutUs = 5000L
        val info = MediaCodec.BufferInfo()
        var isExtractorEos = false
        var isDecoderEos = false

        // Menggunakan ByteArray dinamis berkapasitas besar
        val outputChunkList = ArrayList<ByteArray>(64)
        var totalBytesDecoded = 0

        while (!isDecoderEos) {
            if (!isExtractorEos) {
                val inIndex = codec.dequeueInputBuffer(kTimeoutUs)
                if (inIndex >= 0) {
                    val inBuffer = codec.getInputBuffer(inIndex)
                    if (inBuffer != null) {
                        inBuffer.clear()
                        val sampleSize = extractor.readSampleData(inBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isExtractorEos = true
                        } else {
                            val sampleTimeUs = extractor.sampleTime
                            codec.queueInputBuffer(inIndex, 0, sampleSize, sampleTimeUs, 0)
                            extractor.advance()

                            if (durationUs > 0) {
                                val progress = 0.15f + 0.60f * (sampleTimeUs.toFloat() / durationUs.toFloat()).coerceIn(0f, 1f)
                                onProgress?.invoke(progress, "Mendekode audio PCM...")
                            }
                        }
                    }
                }
            }

            val outIndex = codec.dequeueOutputBuffer(info, kTimeoutUs)
            if (outIndex >= 0) {
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    isDecoderEos = true
                }

                if (info.size > 0) {
                    val outBuffer = codec.getOutputBuffer(outIndex)
                    if (outBuffer != null) {
                        outBuffer.position(info.offset)
                        outBuffer.limit(info.offset + info.size)
                        val chunk = ByteArray(info.size)
                        outBuffer.get(chunk)
                        outputChunkList.add(chunk)
                        totalBytesDecoded += info.size
                    }
                }
                codec.releaseOutputBuffer(outIndex, false)
            }
        }

        val allBytes = ByteArray(totalBytesDecoded)
        var offset = 0
        for (chunk in outputChunkList) {
            System.arraycopy(chunk, 0, allBytes, offset, chunk.size)
            offset += chunk.size
        }
        return allBytes
    }

    /**
     * Konversi raw 16-bit PCM Little Endian ke Float32 stereo 44.1 kHz.
     */
    fun convertRawPcmToFloat32Stereo(
        rawBytes: ByteArray,
        sourceSampleRate: Int,
        sourceChannels: Int,
        targetSampleRate: Int = TARGET_SAMPLE_RATE
    ): AudioPcmData {
        val total16BitSamples = rawBytes.size / 2
        val sourceFrames = total16BitSamples / maxOf(1, sourceChannels)
        if (sourceFrames <= 0) {
            return AudioPcmData(FloatArray(0), targetSampleRate, TARGET_CHANNELS)
        }

        // 1. Ekstrak sampel Float [-1.0f, 1.0f] per-channel dari input
        val channelBuffers = Array(sourceChannels) { FloatArray(sourceFrames) }
        val byteBuffer = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN)

        for (frame in 0 until sourceFrames) {
            for (ch in 0 until sourceChannels) {
                if (byteBuffer.remaining() >= 2) {
                    val s16 = byteBuffer.short.toFloat()
                    channelBuffers[ch][frame] = (s16 / 32768.0f).coerceIn(-1.0f, 1.0f)
                }
            }
        }

        // 2. Normalisasi channel: ubah ke stereo (2 channel)
        val stereoL: FloatArray
        val stereoR: FloatArray

        when (sourceChannels) {
            1 -> {
                stereoL = channelBuffers[0]
                stereoR = channelBuffers[0]
            }
            2 -> {
                stereoL = channelBuffers[0]
                stereoR = channelBuffers[1]
            }
            else -> {
                stereoL = channelBuffers[0]
                stereoR = channelBuffers[1]
            }
        }

        // 3. Resample jika sample rate sumber != target (44100)
        val finalStereoL: FloatArray
        val finalStereoR: FloatArray

        if (sourceSampleRate != targetSampleRate && sourceSampleRate > 0) {
            finalStereoL = resampleLinear(stereoL, sourceSampleRate, targetSampleRate)
            finalStereoR = resampleLinear(stereoR, sourceSampleRate, targetSampleRate)
        } else {
            finalStereoL = stereoL
            finalStereoR = stereoR
        }

        val targetFrames = finalStereoL.size
        val interleaved = FloatArray(targetFrames * TARGET_CHANNELS)
        for (f in 0 until targetFrames) {
            interleaved[f * 2] = finalStereoL[f]
            interleaved[f * 2 + 1] = finalStereoR[f]
        }

        return AudioPcmData(interleaved, targetSampleRate, TARGET_CHANNELS)
    }

    /**
     * Resampler linear cepat untuk mengonversi sample rate ke 44.1 kHz.
     */
    private fun resampleLinear(input: FloatArray, srcRate: Int, dstRate: Int): FloatArray {
        if (srcRate == dstRate || input.isEmpty()) return input
        val ratio = srcRate.toDouble() / dstRate.toDouble()
        val outLength = (input.size / ratio).toInt()
        val output = FloatArray(outLength)

        for (i in 0 until outLength) {
            val srcPos = i * ratio
            val index0 = floor(srcPos).toInt()
            val index1 = (index0 + 1).coerceAtMost(input.size - 1)
            val frac = (srcPos - index0).toFloat()
            val s0 = input[index0]
            val s1 = input[index1]
            output[i] = s0 + frac * (s1 - s0)
        }
        return output
    }

    /**
     * Fallback cepat untuk membaca berkas RIFF WAV uncompressed 16-bit PCM secara langsung.
     */
    private fun tryDecodeDirectWav(
        context: Context,
        uri: Uri,
        onProgress: ((Float, String) -> Unit)?
    ): Result<AudioPcmData>? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                parseWavStream(stream, onProgress)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun parseWavStream(stream: InputStream, onProgress: ((Float, String) -> Unit)? = null): Result<AudioPcmData> {
        val header = ByteArray(12)
        val read = stream.read(header)
        if (read < 12) return Result.failure(IllegalArgumentException("Bukan file WAV valid."))

        val riff = String(header, 0, 4)
        val wave = String(header, 8, 4)
        if (riff != "RIFF" || wave != "WAVE") {
            return Result.failure(IllegalArgumentException("Header WAV tidak sesuai."))
        }

        var channels = 2
        var sampleRate = 44100
        var bitsPerSample = 16
        var pcmBytes: ByteArray? = null

        val chunkHeader = ByteArray(8)
        while (stream.read(chunkHeader) == 8) {
            val chunkId = String(chunkHeader, 0, 4)
            val chunkSize = ByteBuffer.wrap(chunkHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (chunkSize < 0) break

            if (chunkId == "fmt ") {
                val fmtData = ByteArray(chunkSize)
                stream.read(fmtData)
                val bb = ByteBuffer.wrap(fmtData).order(ByteOrder.LITTLE_ENDIAN)
                val audioFormat = bb.short.toInt() // 1 = PCM
                if (audioFormat != 1 && audioFormat != 3) { // 1 = PCM, 3 = IEEE Float
                    return Result.failure(IllegalArgumentException("Format kompresi WAV tidak didukung."))
                }
                channels = bb.short.toInt()
                sampleRate = bb.int
                bb.int // byteRate
                bb.short // blockAlign
                bitsPerSample = bb.short.toInt()
            } else if (chunkId == "data") {
                onProgress?.invoke(0.4f, "Membaca data PCM WAV...")
                pcmBytes = stream.readBytes()
                break
            } else {
                // Skip chunk lain (misal LIST, JUNK, ID3)
                var toSkip = chunkSize.toLong()
                while (toSkip > 0) {
                    val skipped = stream.skip(toSkip)
                    if (skipped <= 0) break
                    toSkip -= skipped
                }
            }
        }

        if (pcmBytes == null || pcmBytes.isEmpty()) {
            return Result.failure(IllegalStateException("Data PCM pada WAV kosong."))
        }

        if (bitsPerSample != 16) {
            return Result.failure(IllegalArgumentException("Hanya 16-bit WAV yang didukung untuk direct parse."))
        }

        val pcmData = convertRawPcmToFloat32Stereo(
            rawBytes = pcmBytes,
            sourceSampleRate = sampleRate,
            sourceChannels = channels,
            targetSampleRate = TARGET_SAMPLE_RATE
        )

        return Result.success(pcmData)
    }
}
