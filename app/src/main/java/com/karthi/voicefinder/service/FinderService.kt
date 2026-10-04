package com.karthi.voicefinder.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.karthi.voicefinder.audio.AlertPlayer
import com.karthi.voicefinder.audio.MicrophoneSource
import com.karthi.voicefinder.voice.FeatureExtractor
import com.karthi.voicefinder.voice.MatchResult
import com.karthi.voicefinder.voice.SpeechSegmenter
import com.karthi.voicefinder.voice.VoiceProfileStore
import com.karthi.voicefinder.voice.WakePhraseMatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Always-on listener. Runs as a microphone foreground service (so it survives the screen turning off and
 * Doze), holds a partial wake lock so the CPU keeps processing audio, and plays the response when the
 * owner's voice says the enrolled phrase.
 */
class FinderService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listenJob: Job? = null
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var alertPlayer: AlertPlayer
    private lateinit var settings: FinderSettings

    /** Ignore anything heard until this time (our own playback, or just after a trigger). */
    @Volatile private var mutedUntil = 0L

    override fun onCreate() {
        super.onCreate()
        settings = FinderSettings(this)
        alertPlayer = AlertPlayer(this)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VoiceFinder::Listening")
            .apply { setReferenceCounted(false) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            settings.listeningEnabled = false
            stopSelf()
            return START_NOT_STICKY
        }
        if (!goForeground()) return START_NOT_STICKY

        val profile = VoiceProfileStore(this).load()
        if (profile == null) {
            Log.w(TAG, "No voice profile enrolled; stopping")
            stopSelf()
            return START_NOT_STICKY
        }
        settings.listeningEnabled = true
        if (listenJob?.isActive != true) {
            // Audio analysis runs off the main thread; only playback hops back to it.
            listenJob = scope.launch(Dispatchers.Default) { listenLoop(WakePhraseMatcher(profile)) }
        }
        _running.value = true
        // Sticky: if the system kills us under memory pressure it recreates the service with a null intent.
        return START_STICKY
    }

    private fun goForeground(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Microphone permission missing; stopping")
            stopSelf()
            return false
        }
        return try {
            ServiceCompat.startForeground(
                this, Notifications.LISTENING_ID, Notifications.listening(this), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
            true
        } catch (e: Exception) {
            // ForegroundServiceStartNotAllowedException / SecurityException when started from the background.
            Log.e(TAG, "Not allowed to start the microphone service now", e)
            stopSelf()
            false
        }
    }

    private suspend fun listenLoop(matcher: WakePhraseMatcher) {
        val segmenter = SpeechSegmenter()
        val extractor = FeatureExtractor()
        var backoffMs = INITIAL_BACKOFF_MS
        while (scope.isActive) {
            try {
                refreshWakeLock()
                var lastRefresh = SystemClock.elapsedRealtime()
                MicrophoneSource.frames().collect { frame ->
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastRefresh > WAKE_LOCK_REFRESH_MS) {
                        refreshWakeLock()
                        lastRefresh = now
                    }
                    if (now < mutedUntil) {
                        segmenter.reset()
                        return@collect
                    }
                    val segment = segmenter.feed(frame) ?: return@collect
                    val result = matcher.evaluate(extractor.extract(segment), settings.sensitivity)
                    _lastMatch.value = result
                    if (result.accepted) {
                        mutedUntil = Long.MAX_VALUE
                        withContext(Dispatchers.Main) { onWakePhrase() }
                    }
                    backoffMs = INITIAL_BACKOFF_MS
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Another app (call, recorder) can hold the mic; retry instead of dying.
                Log.w(TAG, "Microphone unavailable, retrying in ${backoffMs}ms", e)
                delay(backoffMs)
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
    }

    private fun onWakePhrase() {
        Log.i(TAG, "Wake phrase recognised")
        alertPlayer.play {
            mutedUntil = SystemClock.elapsedRealtime() + COOLDOWN_MS
        }
    }

    private fun refreshWakeLock() {
        // A timeout guarantees the lock is released even if the process is torn down abnormally.
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
    }

    override fun onDestroy() {
        _running.value = false
        scope.cancel()
        alertPlayer.stop()
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "FinderService"
        private const val ACTION_STOP = "com.karthi.voicefinder.STOP"
        private const val WAKE_LOCK_TIMEOUT_MS = 15 * 60 * 1000L
        private const val WAKE_LOCK_REFRESH_MS = 10 * 60 * 1000L
        private const val COOLDOWN_MS = 2_000L
        private const val INITIAL_BACKOFF_MS = 2_000L
        private const val MAX_BACKOFF_MS = 60_000L

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private val _lastMatch = MutableStateFlow<MatchResult?>(null)
        /** Scores of the last speech segment heard; shown in the UI to help tune sensitivity. */
        val lastMatch: StateFlow<MatchResult?> = _lastMatch.asStateFlow()

        fun startIntent(context: Context) = Intent(context, FinderService::class.java)
        fun stopIntent(context: Context) = Intent(context, FinderService::class.java).setAction(ACTION_STOP)

        fun start(context: Context) = ContextCompat.startForegroundService(context, startIntent(context))
        fun stop(context: Context) {
            FinderSettings(context).listeningEnabled = false
            context.stopService(startIntent(context))
        }
    }
}
