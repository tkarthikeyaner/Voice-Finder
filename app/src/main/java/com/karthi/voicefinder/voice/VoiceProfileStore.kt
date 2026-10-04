package com.karthi.voicefinder.voice

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** Persists the enrolled [VoiceProfile] as JSON in app-private storage (never leaves the device). */
class VoiceProfileStore(context: Context) {
    private val file = File(context.filesDir, "voice_profile.json")

    fun exists(): Boolean = file.isFile

    fun load(): VoiceProfile? {
        if (!file.isFile) return null
        return try {
            val json = JSONObject(file.readText())
            VoiceProfile(
                templates = json.getJSONArray("templates").let { arr -> List(arr.length()) { arr.getJSONArray(it).toMatrix() } },
                voiceprintCentroid = json.getJSONArray("centroid").toVector(),
                phraseThreshold = json.getDouble("phraseThreshold").toFloat(),
                voiceThreshold = json.getDouble("voiceThreshold").toFloat(),
            )
        } catch (e: Exception) {
            // A corrupt profile must not crash the service; the user simply re-enrolls.
            Log.e(TAG, "Discarding unreadable voice profile", e)
            file.delete()
            null
        }
    }

    @Throws(IOException::class)
    fun save(profile: VoiceProfile) {
        val json = JSONObject()
            .put("version", 1)
            .put("templates", JSONArray().apply { profile.templates.forEach { put(it.toJson()) } })
            .put("centroid", profile.voiceprintCentroid.toJson())
            .put("phraseThreshold", profile.phraseThreshold.toDouble())
            .put("voiceThreshold", profile.voiceThreshold.toDouble())
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(json.toString())
        if (!tmp.renameTo(file)) throw IOException("Could not replace ${file.name}")
    }

    fun clear() {
        file.delete()
    }

    private fun FloatArray.toJson() = JSONArray().apply { this@toJson.forEach { put(it.toDouble()) } }
    private fun Array<FloatArray>.toJson() = JSONArray().apply { this@toJson.forEach { put(it.toJson()) } }
    private fun JSONArray.toVector() = FloatArray(length()) { getDouble(it).toFloat() }
    private fun JSONArray.toMatrix() = Array(length()) { getJSONArray(it).toVector() }

    private companion object {
        const val TAG = "VoiceProfileStore"
    }
}
