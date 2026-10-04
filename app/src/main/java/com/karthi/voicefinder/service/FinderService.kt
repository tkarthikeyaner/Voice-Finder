package com.karthi.voicefinder.service

import android.Manifest
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.BatteryManager
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.annotation.MainThread
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.karthi.voicefinder.audio.AlertPlayer
import com.karthi.voicefinder.audio.MicrophoneSource
import com.karthi.voicefinder.power.PauseReason
import com.karthi.voicefinder.power.PowerPolicy
import com.karthi.voicefinder.voice.AudioLevel
import com.karthi.voicefinder.voice.FeatureExtractor
import com.karthi.voicefinder.voice.MatchResult
import com.karthi.voicefinder.voice.SilenceWatchdog
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
 * Doze), holds a partial wake lock so the CPU keeps processing audio, and raises the "found" alert when the
 * owner's voice says the enrolled phrase.
 */
class FinderService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listenJob: Job? = null
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var alertPlayer: AlertPlayer
    private lateinit var settings: FinderSettings
    private lateinit var notificationManager: NotificationManager

    /** Ignore anything heard until this time (our own playback, or just after a trigger). */
    @Volatile private var mutedUntil = 0L

    /** Set once a voice profile is loaded; null means the service isn't set up to listen. */
    private var matcher: WakePhraseMatcher? = null
    private var batteryPercent = -1
    private var pluggedIn = false

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            readBattery(intent)
            applyPowerRules()
        }
    }

    private val settingsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key in FinderSettings.POWER_KEYS) applyPowerRules()
    }

    override fun onCreate() {
        super.onCreate()
        settings = FinderSettings(this)
        alertPlayer = AlertPlayer(this)
        notificationManager = getSystemService(NotificationManager::class.java)
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VoiceFinder::Listening")
            .apply { setReferenceCounted(false) }
        // Battery changes are a sticky broadcast: registering also returns the current state.
        ContextCompat.registerReceiver(
            this, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )?.let(::readBattery)
        settings.registerListener(settingsListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                settings.listeningEnabled = false
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_STOP_ALERT -> {
                alertPlayer.stop()
                // Started only to deliver this (process had died): don't linger as a non-foreground service.
                if (matcher == null) stopSelf()
                return if (matcher == null) START_NOT_STICKY else START_STICKY
            }
            ACTION_TEST_TRIGGER -> if (matcher != null) {
                onWakePhrase()
                return START_STICKY
            }
            ACTION_RESUME -> {
                // A user tap lets Android give us real mic audio again, but only for a capture opened from
                // now on, so drop the silenced one.
                listenJob?.cancel()
                listenJob = null
                notificationManager.cancel(Notifications.MIC_BLOCKED_ID)
                _micBlocked.value = false
            }
        }
        if (!goForeground()) return START_NOT_STICKY

        val profile = VoiceProfileStore(this).load()
        if (profile == null) {
            Log.w(TAG, "No voice profile enrolled; stopping")
            stopSelf()
            return START_NOT_STICKY
        }
        settings.listeningEnabled = true
        matcher = WakePhraseMatcher(profile)
        _running.value = true
        applyPowerRules()
        startListening()
        // Sticky: if the system kills us under memory pressure it recreates the service with a null intent.
        return START_STICKY
    }

    private fun startListening() {
        val m = matcher ?: return
        if (_pauseReason.value != null || listenJob?.isActive == true) return
        // Audio analysis runs off the main thread; only the alert hops back to it.
        listenJob = scope.launch(Dispatchers.Default) { listenLoop(m) }
    }

    private fun readBattery(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        batteryPercent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        pluggedIn = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    }

    /** Pauses or resumes listening to match the battery rules; the mic and wake lock are released while paused. */
    @MainThread
    private fun applyPowerRules() {
        if (matcher == null) return
        val current = _pauseReason.value
        val next = PowerPolicy.pauseReason(batteryPercent, pluggedIn, settings.powerRules, current)
        if (next == current) return
        _pauseReason.value = next
        notificationManager.notify(Notifications.LISTENING_ID, Notifications.listening(this, next))
        if (next != null) {
            Log.i(TAG, "Pausing listening: $next at $batteryPercent%")
            listenJob?.cancel()
            listenJob = null
            _micLevel.value = 0f
            _micBlocked.value = false
            if (wakeLock.isHeld) wakeLock.release()
        } else {
            Log.i(TAG, "Resuming listening at $batteryPercent%")
            startListening()
        }
    }

    private fun goForeground(): Boolean {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "Microphone permission missing; stopping")
            stopSelf()
            return false
        }
        return try {
            ServiceCompat.startForeground(
                this, Notifications.LISTENING_ID, Notifications.listening(this, _pauseReason.value),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
            true
        } catch (e: Exception) {
            // Android 12+ refuses a microphone service restarted from the background (e.g. after the system
            // killed the app). Ask the user for the one tap that is allowed to bring it back.
            Log.e(TAG, "Not allowed to start the microphone service now", e)
            if (settings.listeningEnabled) notifyMicBlocked()
            stopSelf()
            false
        }
    }

    private suspend fun listenLoop(matcher: WakePhraseMatcher) {
        val segmenter = SpeechSegmenter()
        val extractor = FeatureExtractor()
        val watchdog = SilenceWatchdog()
        var backoffMs = INITIAL_BACKOFF_MS
        while (scope.isActive) {
            try {
                refreshWakeLock()
                var lastRefresh = SystemClock.elapsedRealtime()
                var lastLevel = 0L
                MicrophoneSource.frames().collect { frame ->
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastRefresh > WAKE_LOCK_REFRESH_MS) {
                        refreshWakeLock()
                        lastRefresh = now
                    }
                    if (now - lastLevel >= LEVEL_INTERVAL_MS) {
                        _micLevel.value = AudioLevel.normalized(frame)
                        lastLevel = now
                    }
                    if (watchdog.feed(frame)) {
                        Log.w(TAG, "Android is muting this app's microphone in the background")
                        notifyMicBlocked()
                    }
                    if (now < mutedUntil) {
                        segmenter.reset()
                        return@collect
                    }
                    val segment = segmenter.feed(frame) ?: return@collect
                    val result = matcher.evaluate(extractor.extract(segment), settings.sensitivity)
                    _lastMatch.value = result
                    _micBlocked.value = false
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

    private fun notifyMicBlocked() {
        _micBlocked.value = true
        notificationManager.notify(Notifications.MIC_BLOCKED_ID, Notifications.micBlocked(this))
    }

    @MainThread
    private fun onWakePhrase() {
        if (alertPlayer.isPlaying) return
        Log.i(TAG, "Wake phrase recognised")
        mutedUntil = Long.MAX_VALUE
        _alerting.value = true
        // Full-screen STOP screen over the lock screen (heads-up with a STOP button when unlocked).
        notificationManager.notify(Notifications.FOUND_ID, Notifications.found(this))
        alertPlayer.play(settings.repeatCount) {
            _alerting.value = false
            notificationManager.cancel(Notifications.FOUND_ID)
            mutedUntil = SystemClock.elapsedRealtime() + COOLDOWN_MS
        }
    }

    private fun refreshWakeLock() {
        // A timeout guarantees the lock is released even if the process is torn down abnormally.
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
    }

    override fun onDestroy() {
        unregisterReceiver(batteryReceiver)
        settings.unregisterListener(settingsListener)
        matcher = null
        _pauseReason.value = null
        _running.value = false
        _micBlocked.value = false
        _micLevel.value = 0f
        scope.cancel()
        alertPlayer.stop()
        if (wakeLock.isHeld) wakeLock.release()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "FinderService"
        private const val ACTION_STOP = "com.karthi.voicefinder.STOP"
        private const val ACTION_RESUME = "com.karthi.voicefinder.RESUME"
        private const val ACTION_STOP_ALERT = "com.karthi.voicefinder.STOP_ALERT"
        private const val ACTION_TEST_TRIGGER = "com.karthi.voicefinder.TEST_TRIGGER"
        private const val WAKE_LOCK_TIMEOUT_MS = 15 * 60 * 1000L
        private const val WAKE_LOCK_REFRESH_MS = 10 * 60 * 1000L
        private const val LEVEL_INTERVAL_MS = 100L
        private const val COOLDOWN_MS = 2_000L
        private const val INITIAL_BACKOFF_MS = 2_000L
        private const val MAX_BACKOFF_MS = 60_000L

        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()

        private val _lastMatch = MutableStateFlow<MatchResult?>(null)
        /** Scores of the last speech segment heard; shown in the UI to help tune sensitivity. */
        val lastMatch: StateFlow<MatchResult?> = _lastMatch.asStateFlow()

        private val _micBlocked = MutableStateFlow(false)
        /** True while Android is feeding this app silence, or refused to restart it in the background. */
        val micBlocked: StateFlow<Boolean> = _micBlocked.asStateFlow()

        private val _micLevel = MutableStateFlow(0f)
        /** Live input level 0..1, so the user can see the listener really hears the room. */
        val micLevel: StateFlow<Float> = _micLevel.asStateFlow()

        private val _pauseReason = MutableStateFlow<PauseReason?>(null)
        /** Why listening is paused to save battery, or null while it listens. */
        val pauseReason: StateFlow<PauseReason?> = _pauseReason.asStateFlow()

        private val _alerting = MutableStateFlow(false)
        /** True while the "found" sound is playing. */
        val alerting: StateFlow<Boolean> = _alerting.asStateFlow()

        fun startIntent(context: Context) = Intent(context, FinderService::class.java)
        fun stopIntent(context: Context) = Intent(context, FinderService::class.java).setAction(ACTION_STOP)
        fun resumeIntent(context: Context) = Intent(context, FinderService::class.java).setAction(ACTION_RESUME)
        fun stopAlertIntent(context: Context) = Intent(context, FinderService::class.java).setAction(ACTION_STOP_ALERT)
        fun testTriggerIntent(context: Context) = Intent(context, FinderService::class.java).setAction(ACTION_TEST_TRIGGER)

        fun start(context: Context) = ContextCompat.startForegroundService(context, startIntent(context))
        fun stop(context: Context) {
            FinderSettings(context).listeningEnabled = false
            context.stopService(startIntent(context))
        }
    }
}
