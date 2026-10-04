package com.karthi.voicefinder.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.karthi.voicefinder.R
import com.karthi.voicefinder.service.FinderSettings
import java.io.File
import java.io.IOException

/**
 * The clip played when the phone is found: the bundled respond.mp3, or a file the user picked.
 * A picked file is copied into app storage so it keeps working even if the original is moved or deleted.
 */
class ResponseSound(private val context: Context) {
    private val settings = FinderSettings(context)
    private val customFile = File(context.filesDir, CUSTOM_FILE)

    /** Name of the user's chosen file, or null while the bundled default is in use. */
    val customName: String?
        get() = if (customFile.isFile) settings.customSoundName ?: DEFAULT_CUSTOM_NAME else null

    /** Copies and validates the picked audio file; returns its display name. Call off the main thread. */
    @Throws(IOException::class)
    fun import(uri: Uri): String {
        val resolver = context.contentResolver
        var name = DEFAULT_CUSTOM_NAME
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                if (!cursor.isNull(0)) name = cursor.getString(0)
                if (!cursor.isNull(1) && cursor.getLong(1) > MAX_BYTES) throw IOException("File is larger than $MAX_MB MB")
            }
        }
        val tmp = File(context.cacheDir, "import_response.tmp")
        try {
            val input = resolver.openInputStream(uri) ?: throw IOException("Could not open the selected file")
            val copied = input.use { src -> tmp.outputStream().use { dst -> src.copyTo(dst) } }
            if (copied > MAX_BYTES) throw IOException("File is larger than $MAX_MB MB")
            validate(tmp)
            tmp.copyTo(customFile, overwrite = true)
        } finally {
            tmp.delete()
        }
        settings.customSoundName = name
        return name
    }

    fun resetToDefault() {
        customFile.delete()
        settings.customSoundName = null
    }

    /** A prepared player for the current sound; falls back to the bundled clip if the custom file is unusable. */
    fun createPlayer(attributes: AudioAttributes, sessionId: Int): MediaPlayer? {
        if (customFile.isFile) {
            val player = MediaPlayer()
            try {
                player.setAudioAttributes(attributes)
                player.setDataSource(customFile.path)
                player.prepare()
                return player
            } catch (e: Exception) {
                Log.e(TAG, "Custom sound unplayable, using the default", e)
                player.release()
            }
        }
        return MediaPlayer.create(context, R.raw.respond, attributes, sessionId)
    }

    private fun validate(file: File) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.path)
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            if (hasAudio != "yes" || durationMs == null || durationMs <= 0) throw IOException("That file has no playable audio")
            if (durationMs > MAX_DURATION_MS) throw IOException("Pick a clip shorter than 5 minutes")
        } catch (e: RuntimeException) {
            throw IOException("That file isn't a supported audio format", e)
        } finally {
            runCatching { retriever.release() }
        }
    }

    private companion object {
        const val TAG = "ResponseSound"
        const val CUSTOM_FILE = "custom_response"
        const val DEFAULT_CUSTOM_NAME = "Custom sound"
        const val MAX_MB = 20
        const val MAX_BYTES = MAX_MB * 1_000_000L
        const val MAX_DURATION_MS = 5 * 60 * 1000L
    }
}
