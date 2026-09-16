package com.autoremix.djslow.engine.core

import android.content.Context
import android.net.Uri
import com.autoremix.djslow.engine.AudioDecoder
import com.autoremix.djslow.engine.AudioSource
import com.autoremix.djslow.engine.pcm.AudioPcmData
import com.autoremix.djslow.engine.pcm.AudioPcmDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 1. SOURCE ENGINE
 * Menangani sumber audio nyata: VOCAL, BEAT, REFERENCE, SAMPLE, VOICE TAG.
 * Memvalidasi berkas sebelum diproses untuk mencegah crash jika berkas hilang/rusak.
 */
object SourceEngine {

    enum class SourceRole(val label: String, val description: String) {
        VOCAL("Vokal", "Track vokal acapella atau lagu utama yang akan di-remix"),
        BEAT("Beat / Drum", "Track ketukan atau drum loop eksternal opsional"),
        REFERENCE("Referensi", "Track lagu referensi gaya remix"),
        SAMPLE("Sample Audio", "Sample melodi, instrumen, atau loop pendukung"),
        VOICE_TAG("Voice Tag", "Label vokal DJ (contoh: 'DJ Slow', 'Let's Go', 'Auto Remix')")
    }

    data class SourceItem(
        val sourceId: String = UUID.randomUUID().toString(),
        val fileUri: Uri?,
        val fileName: String,
        val durationMs: Long,
        val sampleRate: Int,
        val channels: Int,
        val format: String,
        val fileSizeBytes: Long,
        val role: SourceRole
    ) {
        val formattedDuration: String
            get() {
                val totalSec = durationMs / 1000
                val min = totalSec / 60
                val sec = totalSec % 60
                return String.format("%02d:%02d", min, sec)
            }

        fun toAudioSource(): AudioSource {
            return AudioSource(
                uri = fileUri ?: Uri.EMPTY,
                fileName = fileName,
                fileSizeBytes = fileSizeBytes,
                mimeType = if (format.lowercase() == "mp3") "audio/mpeg" else "audio/wav",
                durationMs = durationMs,
                sampleRateHz = sampleRate,
                channelCount = channels,
                formatExtension = format
            )
        }
    }

    data class SourceProject(
        val projectId: String = UUID.randomUUID().toString(),
        val projectName: String = "Proyek Auto Remix",
        val items: Map<SourceRole, SourceItem> = emptyMap(),
        val pcmCache: Map<SourceRole, AudioPcmData> = emptyMap()
    ) {
        val vocalItem: SourceItem? get() = items[SourceRole.VOCAL]
        val beatItem: SourceItem? get() = items[SourceRole.BEAT]
        val referenceItem: SourceItem? get() = items[SourceRole.REFERENCE]
        val sampleItem: SourceItem? get() = items[SourceRole.SAMPLE]
        val voiceTagItem: SourceItem? get() = items[SourceRole.VOICE_TAG]

        val hasAnySource: Boolean get() = items.isNotEmpty()
        val maxDurationMs: Long
            get() = items.values.maxOfOrNull { it.durationMs } ?: 0L
    }

    /**
     * Memvalidasi keberadaan dan format file audio secara aman tanpa crash.
     */
    fun validateFileUri(context: Context, uri: Uri?): Result<Boolean> {
        if (uri == null) {
            return Result.failure(IllegalArgumentException("URI berkas tidak boleh null."))
        }
        return try {
            val scheme = uri.scheme
            if (scheme == "file") {
                val file = File(uri.path ?: "")
                if (!file.exists() || !file.canRead()) {
                    return Result.failure(IllegalStateException("Berkas tidak ditemukan atau tidak dapat dibaca di penyimpanan."))
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val available = stream.available()
                    if (available <= 0) {
                        // Tidak selalu fatal jika stream tidak melaporkan size awal, tapi jika baca 1 byte gagal maka error
                        val firstByte = stream.read()
                        if (firstByte == -1) {
                            return Result.failure(IllegalStateException("Berkas audio kosong (0 byte)."))
                        }
                    }
                } ?: return Result.failure(IllegalStateException("Tidak dapat membuka akses stream ke berkas audio."))
            }
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Validasi berkas gagal: ${e.localizedMessage ?: e.message}"))
        }
    }

    /**
     * Memuat dan mengidentifikasi metadata berkas audio sumber.
     */
    suspend fun loadSourceItem(
        context: Context,
        uri: Uri,
        role: SourceRole
    ): Result<SourceItem> = withContext(Dispatchers.IO) {
        val validation = validateFileUri(context, uri)
        if (validation.isFailure) {
            return@withContext Result.failure(validation.exceptionOrNull()!!)
        }

        val decodeResult = AudioDecoder.decode(context, uri)
        decodeResult.fold(
            onSuccess = { audioSource ->
                val item = SourceItem(
                    fileUri = uri,
                    fileName = audioSource.fileName,
                    durationMs = audioSource.durationMs,
                    sampleRate = audioSource.sampleRateHz ?: 44100,
                    channels = audioSource.channelCount ?: 2,
                    format = audioSource.formatExtension,
                    fileSizeBytes = audioSource.fileSizeBytes,
                    role = role
                )
                Result.success(item)
            },
            onFailure = { ex ->
                Result.failure(IllegalStateException("Format audio tidak didukung atau berkas rusak: ${ex.message}"))
            }
        )
    }

    /**
     * Mendekode berkas sumber audio menjadi PCM Float32 stereo 44.1 kHz.
     */
    suspend fun decodeSourcePcm(
        context: Context,
        item: SourceItem,
        onProgress: ((Float, String) -> Unit)? = null
    ): Result<AudioPcmData> = withContext(Dispatchers.IO) {
        val uri = item.fileUri ?: return@withContext Result.failure(IllegalArgumentException("URI berkas kosong."))
        AudioPcmDecoder.decodeToPcm(context, uri, onProgress)
    }
}
