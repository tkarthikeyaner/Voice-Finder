package com.karthi.voicefinder.audio

import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import androidx.annotation.MainThread

/**
 * Plays the response sound a set number of times. For a real alert it first overrides silent, vibrate and
 * Do Not Disturb and maxes the volume, then puts ringer mode, DND state and volumes back exactly as they were.
 */
class AlertPlayer(private val context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val sound = ResponseSound(context)

    // USAGE_ALARM is the one stream Android lets through silent mode and default DND rules.
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
        .setAudioAttributes(attributes)
        .build()

    private var player: MediaPlayer? = null
    private var saved: SavedState? = null
    private var onFinished: (() -> Unit)? = null

    val isPlaying: Boolean get() = player != null

    /**
     * [overrideSilent] = false is for previews: plays at the current alarm volume without touching settings.
     * [onFinished] runs once, whether playback completes, fails or is stopped.
     */
    @MainThread
    fun play(repeat: Int, overrideSilent: Boolean = true, onFinished: () -> Unit = {}) {
        if (player != null) return
        this.onFinished = onFinished
        if (overrideSilent) {
            saved = captureState()
            overrideSilentMode()
            maximiseVolume(AudioManager.STREAM_MUSIC)
            maximiseVolume(AudioManager.STREAM_ALARM)
        }
        audioManager.requestAudioFocus(focusRequest)

        val mp = sound.createPlayer(attributes, audioManager.generateAudioSessionId())
        if (mp == null) {
            Log.e(TAG, "Could not load the response audio")
            finish()
            return
        }
        var remaining = repeat.coerceAtLeast(1)
        mp.setOnCompletionListener {
            if (--remaining > 0) it.start() else finish()
        }
        mp.setOnErrorListener { _, what, extra ->
            Log.e(TAG, "Playback error what=$what extra=$extra")
            finish()
            true
        }
        player = mp
        mp.start()
    }

    @MainThread
    fun stop() {
        if (player != null) finish()
    }

    private fun finish() {
        player?.run {
            runCatching { if (isPlaying) stop() }
            release()
        }
        player = null
        audioManager.abandonAudioFocusRequest(focusRequest)
        saved?.let(::restoreState)
        saved = null
        val callback = onFinished
        onFinished = null
        callback?.invoke()
    }

    private fun overrideSilentMode() {
        // Leaving DND or silent requires "Do Not Disturb access"; without it the alarm stream still plays.
        if (notificationManager.isNotificationPolicyAccessGranted) {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
            try {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
            } catch (e: SecurityException) {
                Log.w(TAG, "Ringer mode unchanged: grant Do Not Disturb access for full override", e)
            }
        }
    }

    private fun maximiseVolume(stream: Int) {
        try {
            audioManager.setStreamVolume(stream, audioManager.getStreamMaxVolume(stream), 0)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not raise volume of stream $stream", e)
        }
    }

    private fun captureState() = SavedState(
        ringerMode = audioManager.ringerMode,
        interruptionFilter = notificationManager.currentInterruptionFilter,
        musicVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
        alarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM),
    )

    private fun restoreState(state: SavedState) {
        try {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, state.musicVolume, 0)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, state.alarmVolume, 0)
            if (audioManager.ringerMode != state.ringerMode) audioManager.ringerMode = state.ringerMode
            if (notificationManager.isNotificationPolicyAccessGranted &&
                state.interruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
            ) {
                notificationManager.setInterruptionFilter(state.interruptionFilter)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not fully restore audio state", e)
        }
    }

    private data class SavedState(val ringerMode: Int, val interruptionFilter: Int, val musicVolume: Int, val alarmVolume: Int)

    private companion object {
        const val TAG = "AlertPlayer"
    }
}
