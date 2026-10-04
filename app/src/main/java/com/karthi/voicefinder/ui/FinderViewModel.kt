package com.karthi.voicefinder.ui

import android.annotation.SuppressLint
import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.karthi.voicefinder.audio.AlertPlayer
import com.karthi.voicefinder.audio.MicrophoneSource
import com.karthi.voicefinder.audio.ResponseSound
import com.karthi.voicefinder.power.PowerRules
import com.karthi.voicefinder.service.FinderService
import com.karthi.voicefinder.service.FinderSettings
import com.karthi.voicefinder.voice.AudioLevel
import com.karthi.voicefinder.voice.FeatureExtractor
import com.karthi.voicefinder.voice.SpeechSegmenter
import com.karthi.voicefinder.voice.Utterance
import com.karthi.voicefinder.voice.VoiceProfile
import com.karthi.voicefinder.voice.VoiceProfileStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class BannerState(val title: String, val message: String, val theme: Int)

data class EnrollmentState(
    val samples: Int = 0,
    val recording: Boolean = false,
    /** Phrases saved as "should not trigger". */
    val wrongPhrases: Int = 0,
    val profileSaved: Boolean = false,
    val message: String? = null,
)

class FinderViewModel(app: Application) : AndroidViewModel(app) {

    private val store = VoiceProfileStore(app)
    private val settings = FinderSettings(app)
    private val sound = ResponseSound(app)
    private val extractor = FeatureExtractor()
    private val previewPlayer = AlertPlayer(app)
    private val samples = mutableListOf<Utterance>()
    private var recordJob: Job? = null

    private val _enrollment = MutableStateFlow(initialEnrollment())
    val enrollment: StateFlow<EnrollmentState> = _enrollment.asStateFlow()

    private val _recordLevel = MutableStateFlow(0f)
    val recordLevel: StateFlow<Float> = _recordLevel.asStateFlow()

    private val _sensitivity = MutableStateFlow(settings.sensitivity)
    val sensitivity: StateFlow<Float> = _sensitivity.asStateFlow()

    private val _repeatCount = MutableStateFlow(settings.repeatCount)
    val repeatCount: StateFlow<Int> = _repeatCount.asStateFlow()

    private val _soundName = MutableStateFlow(sound.customName)
    val soundName: StateFlow<String?> = _soundName.asStateFlow()

    private val _powerRules = MutableStateFlow(settings.powerRules)
    val powerRules: StateFlow<PowerRules> = _powerRules.asStateFlow()

    private val _banner = MutableStateFlow(currentBanner())
    val banner: StateFlow<BannerState> = _banner.asStateFlow()

    private val _previewing = MutableStateFlow(false)
    val previewing: StateFlow<Boolean> = _previewing.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    /** One-off messages for a snackbar. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    val serviceRunning = FinderService.running
    val lastMatch = FinderService.lastMatch
    val micBlocked = FinderService.micBlocked
    val micLevel = FinderService.micLevel
    val pauseReason = FinderService.pauseReason

    fun recordSample() {
        if (samples.size >= VoiceProfile.MAX_SAMPLES) return
        record("Listening… say the phrase now", resumeAfter = false) { utterance ->
            if (utterance.frames.size < VoiceProfile.MIN_PHRASE_FRAMES) {
                "Too short. Say the whole phrase “ஏய் எங்க இருக்க?” in one go."
            } else {
                samples += utterance
                "Sample ${samples.size} saved (${utterance.frames.size * 10} ms of speech)"
            }
        }
    }

    /** Records a sentence that must NOT trigger (e.g. "ஏய் என்ன பண்ற") and adds it to the saved profile. */
    fun recordWrongPhrase() {
        record("Listening… say a phrase that should NOT trigger", resumeAfter = true) { utterance ->
            val profile = withContext(Dispatchers.IO) { store.load() }
            if (profile == null) {
                "Save your voice profile first."
            } else {
                val updated = profile.withNegative(utterance.frames)
                withContext(Dispatchers.IO) { store.save(updated) }
                _enrollment.update { it.copy(wrongPhrases = updated.negatives.size) }
                "Saved. That phrase won't trigger the alert."
            }
        }
    }

    fun clearWrongPhrases() {
        viewModelScope.launch {
            val message = try {
                val profile = withContext(Dispatchers.IO) { store.load() }
                if (profile != null) withContext(Dispatchers.IO) { store.save(profile.withoutNegatives()) }
                restartListenerIfRunning()
                "Cleared the phrases that shouldn't trigger."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not clear wrong phrases", e)
                "Couldn't clear: ${e.message}"
            }
            _enrollment.update { it.copy(wrongPhrases = 0, message = message) }
        }
    }

    /** Re-reads the profile, e.g. after "Wrong phrase" was tapped on the alert screen. */
    fun refresh() {
        viewModelScope.launch {
            val count = withContext(Dispatchers.IO) { store.load()?.negatives?.size ?: 0 }
            _enrollment.update { it.copy(wrongPhrases = count) }
        }
    }

    private fun restartListenerIfRunning() {
        if (serviceRunning.value) {
            FinderService.stop(getApplication())
            FinderService.start(getApplication())
        }
    }

    /**
     * Captures one spoken segment and hands it to [onCaptured], whose return value is shown as the message.
     * The listener and the recorder can't share the microphone, so listening pauses meanwhile.
     */
    @SuppressLint("MissingPermission")
    private fun record(prompt: String, resumeAfter: Boolean, onCaptured: suspend (Utterance) -> String) {
        if (recordJob?.isActive == true) return
        val wasListening = serviceRunning.value
        if (wasListening) FinderService.stop(getApplication())
        _enrollment.update { it.copy(recording = true, message = prompt) }
        recordJob = viewModelScope.launch {
            val message = try {
                if (wasListening) delay(MIC_HANDOVER_MS)
                val segmenter = SpeechSegmenter()
                val pcm = withTimeout(RECORD_TIMEOUT_MS) {
                    MicrophoneSource.frames()
                        .onEach { _recordLevel.value = AudioLevel.normalized(it) }
                        .mapNotNull { segmenter.feed(it) }
                        .first()
                }
                onCaptured(withContext(Dispatchers.Default) { extractor.extract(pcm) })
            } catch (e: TimeoutCancellationException) {
                "Didn't hear a clear phrase. Speak a little louder, closer to the phone."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Recording failed", e)
                "Recording failed: ${e.message}"
            } finally {
                _recordLevel.value = 0f
            }
            _enrollment.update { it.copy(samples = samples.size, recording = false, message = message) }
            if (wasListening && resumeAfter) FinderService.start(getApplication())
        }
    }

    private fun initialEnrollment(): EnrollmentState {
        val hadProfile = store.exists()
        val profile = store.load()
        return EnrollmentState(
            profileSaved = profile != null,
            wrongPhrases = profile?.negatives?.size ?: 0,
            message = if (hadProfile && profile == null) {
                "Update: Voice Finder now tells words apart more strictly. Please record your phrase again."
            } else {
                null
            },
        )
    }

    fun saveProfile() {
        viewModelScope.launch {
            val message = try {
                val profile = withContext(Dispatchers.Default) { VoiceProfile.build(samples.toList()) }
                withContext(Dispatchers.IO) { store.save(profile) }
                "Voice profile saved. Tap the mic to start listening."
            } catch (e: Exception) {
                Log.e(TAG, "Could not build profile", e)
                "Could not save profile: ${e.message}"
            }
            _enrollment.update { it.copy(profileSaved = store.exists(), message = message) }
        }
    }

    fun resetEnrollment() {
        recordJob?.cancel()
        samples.clear()
        FinderService.stop(getApplication())
        store.clear()
        _enrollment.value = EnrollmentState(message = "Profile cleared. Record new samples.")
    }

    fun setSensitivity(value: Float) {
        settings.sensitivity = value
        _sensitivity.value = settings.sensitivity
    }

    fun changeRepeat(delta: Int) {
        settings.repeatCount = settings.repeatCount + delta
        _repeatCount.value = settings.repeatCount
    }

    fun setPauseOnLowBattery(on: Boolean) {
        settings.pauseOnLowBattery = on
        _powerRules.value = settings.powerRules
    }

    fun setLowBatteryPercent(percent: Int) {
        settings.lowBatteryPercent = percent
        _powerRules.value = settings.powerRules
    }

    fun setPauseWhileCharging(on: Boolean) {
        settings.pauseWhileCharging = on
        _powerRules.value = settings.powerRules
    }

    // The fields show exactly what was typed (even empty); the stored value falls back to the default when blank.
    fun setBannerTitle(text: String) {
        val clipped = text.take(FinderSettings.MAX_TITLE_LENGTH)
        settings.bannerTitle = clipped
        _banner.update { it.copy(title = clipped) }
    }

    fun setBannerMessage(text: String) {
        val clipped = text.take(FinderSettings.MAX_MESSAGE_LENGTH)
        settings.bannerMessage = clipped
        _banner.update { it.copy(message = clipped) }
    }

    fun setBannerTheme(index: Int) {
        settings.bannerTheme = index
        _banner.update { it.copy(theme = index) }
    }

    fun resetBanner() {
        settings.resetBanner()
        _banner.value = currentBanner()
    }

    private fun currentBanner() = BannerState(settings.bannerTitle, settings.bannerMessage, settings.bannerTheme)

    fun setListening(on: Boolean) {
        if (on) FinderService.start(getApplication()) else FinderService.stop(getApplication())
    }

    fun resumeListening() {
        getApplication<Application>().startForegroundService(FinderService.resumeIntent(getApplication()))
    }

    fun simulateTrigger() {
        if (serviceRunning.value) {
            getApplication<Application>().startService(FinderService.testTriggerIntent(getApplication()))
        } else {
            _messages.tryEmit("Turn on listening first")
        }
    }

    fun importSound(uri: Uri) {
        stopPreview()
        viewModelScope.launch {
            val message = try {
                val name = withContext(Dispatchers.IO) { sound.import(uri) }
                _soundName.value = name
                "Response sound set to $name"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not import sound", e)
                "Couldn't use that file: ${e.message}"
            }
            _messages.emit(message)
        }
    }

    fun resetSound() {
        stopPreview()
        sound.resetToDefault()
        _soundName.value = null
        _messages.tryEmit("Back to the default sound")
    }

    fun togglePreview() {
        if (previewPlayer.isPlaying) {
            stopPreview()
            return
        }
        _previewing.value = true
        previewPlayer.play(repeat = 1, overrideSilent = false) { _previewing.value = false }
    }

    private fun stopPreview() = previewPlayer.stop()

    override fun onCleared() {
        previewPlayer.stop()
    }

    private companion object {
        const val TAG = "FinderViewModel"
        const val RECORD_TIMEOUT_MS = 8_000L
        const val MIC_HANDOVER_MS = 500L
    }
}
