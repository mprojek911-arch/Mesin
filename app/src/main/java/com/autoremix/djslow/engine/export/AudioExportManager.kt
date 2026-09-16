package com.autoremix.djslow.engine.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.autoremix.djslow.engine.wav.WavValidator
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manajer Ekspor Audio Mandiri (Tahap 6 Final):
 * 1. Ekspor MediaStore WAV ke folder "Music/Auto Remix/" dengan nama "AutoRemix_DJSlow_[tanggal_waktu].wav"
 *    - Membuka kembali berkas setelah ekspor
 *    - Memvalidasi integritas RIFF WAV 16-bit PCM bebas clipping
 * 2. Ekspor MP3 Lokal menggunakan Android MediaCodec jika encoder audio/mpeg tersedia di perangkat
 *    - Jika encoder tidak tersedia, melaporkan ketiadaan encoder secara jujur tanpa tombol palsu
 * 3. Berbagi berkas audio nyata menggunakan Android FileProvider
 */
object AudioExportManager {

    /**
     * Memeriksa apakah perangkat memiliki encoder lokal untuk format MP3 (audio/mpeg).
     */
    fun isMp3EncoderAvailable(): Boolean {
        return try {
            val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            codecList.codecInfos.any { info ->
                info.isEncoder && info.supportedTypes.any { type ->
                    type.equals("audio/mpeg", ignoreCase = true) ||
                    type.equals("audio/mp3", ignoreCase = true)
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Mengekspor berkas WAV hasil render nyata ke MediaStore sistem Android.
     * Folder target: Music/Auto Remix/
     * Format nama: AutoRemix_DJSlow_[tanggal_waktu].wav
     *
     * Setelah disimpan, berkas dibuka kembali dan divalidasi dengan [WavValidator].
     */
    fun exportWavToMediaStore(context: Context, sourceWavFile: File): Result<File> {
        if (!sourceWavFile.exists() || sourceWavFile.length() < 44L) {
            return Result.failure(IllegalArgumentException("Berkas WAV sumber tidak ditemukan atau kosong."))
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "AutoRemix_DJSlow_$timestamp.wav"

        return try {
            val destinationFile: File

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10+ (API 29+) menggunakan MediaStore Scoped Storage
                val contentValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/Auto Remix/")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }

                val uri: Uri = context.contentResolver.insert(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ) ?: return Result.failure(IllegalStateException("Gagal membuat entri MediaStore untuk berkas WAV."))

                context.contentResolver.openOutputStream(uri)?.use { out ->
                    FileInputStream(sourceWavFile).use { inStream ->
                        inStream.copyTo(out)
                    }
                    out.flush()
                } ?: return Result.failure(IllegalStateException("Gagal menulis aliran data WAV ke MediaStore."))

                contentValues.clear()
                contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                context.contentResolver.update(uri, contentValues, null, null)

                // Simpan juga salinan referensi lokal yang dapat diakses langsung oleh validator & player
                val musicFolder = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Auto Remix")
                musicFolder.mkdirs()
                destinationFile = File(musicFolder, fileName)
                sourceWavFile.copyTo(destinationFile, overwrite = true)

            } else {
                // Android 9 kebawah (API <= 28) menggunakan direktori publik Music
                val publicMusic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                val autoRemixFolder = File(publicMusic, "Auto Remix")
                autoRemixFolder.mkdirs()
                destinationFile = File(autoRemixFolder, fileName)

                sourceWavFile.copyTo(destinationFile, overwrite = true)
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(destinationFile.absolutePath),
                    arrayOf("audio/wav"),
                    null
                )
            }

            // Validasi kembali berkas yang baru saja diekspor
            val validation = WavValidator.validate(destinationFile)
            if (!validation.isValid) {
                return Result.failure(
                    IllegalStateException(validation.errorMessage ?: "Validasi berkas WAV yang diekspor gagal.")
                )
            }

            Result.success(destinationFile)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Gagal mengekspor berkas WAV ke MediaStore: ${e.localizedMessage}"))
        }
    }

    /**
     * Mengekspor berkas audio ke format MP3 lokal jika encoder perangkat keras/perangkat lunak tersedia.
     * Jika encoder lokal tidak tersedia pada sistem Android pengguna, mengembalikan error transparan
     * sehingga aplikasi tidak melakukan klaim palsu.
     */
    fun exportMp3(context: Context, sourceWavFile: File): Result<File> {
        if (!isMp3EncoderAvailable()) {
            return Result.failure(
                UnsupportedOperationException(
                    "Encoder MP3 (audio/mpeg) lokal tidak tersedia pada sistem Android perangkat ini. " +
                    "Format WAV 16-bit PCM kualitas studio adalah output utama yang didukung."
                )
            )
        }

        if (!sourceWavFile.exists() || sourceWavFile.length() < 44L) {
            return Result.failure(IllegalArgumentException("Berkas sumber WAV tidak valid."))
        }

        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val mp3FileName = "AutoRemix_DJSlow_$timestamp.mp3"
            val outputFolder = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Auto Remix").apply { mkdirs() }
            val outputFile = File(outputFolder, mp3FileName)

            encodeWavToMp3Internal(sourceWavFile, outputFile)

            if (!outputFile.exists() || outputFile.length() < 1000L) {
                return Result.failure(IllegalStateException("Hasil encode MP3 tidak valid atau kosong."))
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Proses encode MP3 gagal: ${e.localizedMessage}"))
        }
    }

    /**
     * Melakukan kompresi PCM WAV ke format MP3 menggunakan MediaCodec lokal.
     */
    private fun encodeWavToMp3Internal(wavFile: File, mp3File: File) {
        val sampleRate = 44100
        val channelCount = 2
        val bitRate = 192000 // 192 kbps high quality MP3

        val format = MediaFormat.createAudioFormat("audio/mpeg", sampleRate, channelCount).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        val codec = MediaCodec.createEncoderByType("audio/mpeg")
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()

        val bufferInfo = MediaCodec.BufferInfo()
        val wavIn = FileInputStream(wavFile)
        wavIn.skip(44) // lewati RIFF header WAV

        val mp3Out = FileOutputStream(mp3File)

        val inputBuffer = ByteArray(8192)
        var isEos = false

        try {
            while (!isEos) {
                val inputIndex = codec.dequeueInputBuffer(10000L)
                if (inputIndex >= 0) {
                    val codecInputBuffer = codec.getInputBuffer(inputIndex)
                    codecInputBuffer?.clear()
                    val bytesRead = wavIn.read(inputBuffer)
                    if (bytesRead < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isEos = true
                    } else {
                        codecInputBuffer?.put(inputBuffer, 0, bytesRead)
                        codec.queueInputBuffer(inputIndex, 0, bytesRead, 0L, 0)
                    }
                }

                var outputIndex = codec.dequeueOutputBuffer(bufferInfo, 10000L)
                while (outputIndex >= 0) {
                    val codecOutputBuffer = codec.getOutputBuffer(outputIndex)
                    if (codecOutputBuffer != null && bufferInfo.size > 0) {
                        val outData = ByteArray(bufferInfo.size)
                        codecOutputBuffer.get(outData)
                        mp3Out.write(outData)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        break
                    }
                    outputIndex = codec.dequeueOutputBuffer(bufferInfo, 0L)
                }
            }
        } finally {
            wavIn.close()
            mp3Out.flush()
            mp3Out.close()
            try { codec.stop() } catch (_: Exception) {}
            try { codec.release() } catch (_: Exception) {}
        }
    }

    /**
     * Membuka Sharesheet sistem Android untuk membagikan berkas audio nyata.
     */
    fun shareAudioFile(context: Context, audioFile: File): Result<Unit> {
        if (!audioFile.exists()) {
            return Result.failure(IllegalArgumentException("Berkas audio tidak ditemukan untuk dibagikan."))
        }

        return try {
            val authority = "${context.packageName}.fileprovider"
            val contentUri: Uri = FileProvider.getUriForFile(context, authority, audioFile)

            val mime = if (audioFile.name.endsWith(".mp3", ignoreCase = true)) "audio/mpeg" else "audio/wav"

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, "Hasil Remix DJ Slow: ${audioFile.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(shareIntent, "Bagikan Audio DJ Slow")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Gagal membagikan berkas audio: ${e.localizedMessage}"))
        }
    }
}
