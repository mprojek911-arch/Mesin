package com.autoremix.djslow.storage

import android.content.Context
import android.content.SharedPreferences
import com.autoremix.djslow.engine.arrangement.AutoDjPreset
import com.autoremix.djslow.engine.mastering.MasteringPreset
import com.autoremix.djslow.engine.music.Chord
import com.autoremix.djslow.engine.music.ChordType
import com.autoremix.djslow.engine.music.MusicKey
import com.autoremix.djslow.engine.music.MusicMode
import com.autoremix.djslow.engine.music.PitchClass
import com.autoremix.djslow.engine.synth.BassPatternType
import com.autoremix.djslow.engine.synth.ChordSynthPreset
import org.json.JSONArray
import org.json.JSONObject

/**
 * Penyimpanan Proyek Lokal Mandiri (Offline-First):
 * Menyimpan dan memuat konfigurasi proyek remix (Vocal URI, Beat URI, BPM, Key, Chord, Preset, Volume, Aransemen, Melody Seed)
 * sepenuhnya di penyimpanan lokal perangkat tanpa server atau cloud.
 */
object ProjectStateRepository {

    private const val PREFS_NAME = "auto_remix_project_prefs"
    private const val KEY_HAS_PROJECT = "has_saved_project"
    private const val KEY_PROJECT_JSON = "saved_project_json"

    data class SavedProject(
        val vocalUri: String?,
        val vocalFileName: String?,
        val beatUri: String?,
        val beatFileName: String?,
        val bpm: Float,
        val keyPitch: String,
        val keyMode: String,
        val chordPresetName: String,
        val bassPatternName: String,
        val djPresetName: String,
        val masteringPresetName: String,
        val melodySeed: Long,
        val chordsJson: String,
        val vocalVolume: Float,
        val beatVolume: Float,
        val masterGain: Float,
        val timestamp: Long = System.currentTimeMillis()
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun hasSavedProject(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_HAS_PROJECT, false)
    }

    fun saveProject(
        context: Context,
        vocalUri: String?,
        vocalFileName: String?,
        beatUri: String?,
        beatFileName: String?,
        bpm: Float,
        key: MusicKey,
        chordPreset: ChordSynthPreset,
        bassPattern: BassPatternType,
        djPreset: AutoDjPreset,
        masteringPreset: MasteringPreset,
        melodySeed: Long,
        chords: List<Chord>,
        vocalVolume: Float,
        beatVolume: Float,
        masterGain: Float
    ): Result<Unit> {
        return try {
            val jsonArray = JSONArray()
            chords.forEach { chord ->
                val cObj = JSONObject().apply {
                    put("root", chord.root.name)
                    put("type", chord.type.name)
                }
                jsonArray.put(cObj)
            }

            val projectObj = JSONObject().apply {
                put("vocalUri", vocalUri ?: "")
                put("vocalFileName", vocalFileName ?: "")
                put("beatUri", beatUri ?: "")
                put("beatFileName", beatFileName ?: "")
                put("bpm", bpm.toDouble())
                put("keyPitch", key.tonic.name)
                put("keyMode", key.mode.name)
                put("chordPresetName", chordPreset.name)
                put("bassPatternName", bassPattern.name)
                put("djPresetName", djPreset.name)
                put("masteringPresetName", masteringPreset.name)
                put("melodySeed", melodySeed)
                put("chords", jsonArray.toString())
                put("vocalVolume", vocalVolume.toDouble())
                put("beatVolume", beatVolume.toDouble())
                put("masterGain", masterGain.toDouble())
                put("timestamp", System.currentTimeMillis())
            }

            getPrefs(context).edit()
                .putBoolean(KEY_HAS_PROJECT, true)
                .putString(KEY_PROJECT_JSON, projectObj.toString())
                .apply()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun loadProject(context: Context): Result<SavedProject> {
        return try {
            val jsonStr = getPrefs(context).getString(KEY_PROJECT_JSON, null)
                ?: return Result.failure(NoSuchElementException("Tidak ada proyek yang tersimpan."))

            val obj = JSONObject(jsonStr)
            val saved = SavedProject(
                vocalUri = obj.optString("vocalUri").takeIf { it.isNotBlank() },
                vocalFileName = obj.optString("vocalFileName").takeIf { it.isNotBlank() },
                beatUri = obj.optString("beatUri").takeIf { it.isNotBlank() },
                beatFileName = obj.optString("beatFileName").takeIf { it.isNotBlank() },
                bpm = obj.optDouble("bpm", 80.0).toFloat(),
                keyPitch = obj.optString("keyPitch", PitchClass.A.name),
                keyMode = obj.optString("keyMode", MusicMode.MINOR.name),
                chordPresetName = obj.optString("chordPresetName", ChordSynthPreset.SOFT_PIANO.name),
                bassPatternName = obj.optString("bassPatternName", BassPatternType.DJ_SLOW_BASS.name),
                djPresetName = obj.optString("djPresetName", AutoDjPreset.DJ_SLOW.name),
                masteringPresetName = obj.optString("masteringPresetName", MasteringPreset.DJ_SLOW.name),
                melodySeed = obj.optLong("melodySeed", 42L),
                chordsJson = obj.optString("chords", "[]"),
                vocalVolume = obj.optDouble("vocalVolume", 1.0).toFloat(),
                beatVolume = obj.optDouble("beatVolume", 0.8).toFloat(),
                masterGain = obj.optDouble("masterGain", 0.9).toFloat(),
                timestamp = obj.optLong("timestamp", 0L)
            )
            Result.success(saved)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parseChords(jsonStr: String): List<Chord> {
        val list = mutableListOf<Chord>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val pitch = PitchClass.valueOf(obj.getString("root"))
                val type = ChordType.valueOf(obj.getString("type"))
                list.add(Chord(pitch, type))
            }
        } catch (_: Exception) {}
        return list
    }

    fun clearProject(context: Context) {
        getPrefs(context).edit().clear().apply()
    }
}
