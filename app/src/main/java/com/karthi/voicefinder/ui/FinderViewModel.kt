package com.karthi.voicefinder.ui

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.karthi.voicefinder.audio.AlertPlayer
import com.karthi.voicefinder.audio.MicrophoneSource
import com.karthi.voicefinder.service.FinderService
import com.karthi.voicefinder.service.FinderSettings
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

data class EnrollmentState(
    val samples: Int = 0,
    val recording: Boolean = false,
    val profileSaved: Boolean = false,
    val message: String? = null,
)

class FinderViewModel(app: Application) : AndroidViewModel(app) {

    private val store = VoiceProfileStore(app)
    private val settings = FinderSettings(app)
    private val extractor = FeatureExtractor()
    private val alertPlayer = AlertPlayer(app)
    private val samples = mutableListOf<Utterance>()
    private var recordJob: Job? = null

    private val _enrollment = MutableStateFlow(EnrollmentState(profileSaved = store.exists()))
    val enrollment: StateFlow<EnrollmentState> = _enrollment.asStateFlow()

    private val _sensitivity = MutableStateFlow(settings.sensitivity)
    val sensitivity: StateFlow<Float> = _sensitivity.asStateFlow()

    val serviceRunning = FinderService.running
    val lastMatch = FinderService.lastMatch

    /** Caller must have verified RECORD_AUDIO is granted. */
    @SuppressLint("MissingPermission")
    fun recordSample() {
        if (recordJob?.isActive == true || samples.size >= VoiceProfile.MAX_SAMPLES) return
        // The listener and the enrollment recorder can't share the microphone.
        val wasListening = serviceRunning.value
        if (wasListening) FinderService.stop(getApplication())
        _enrollment.update { it.copy(recording = true, message = "Listening… say the phrase now") }
        recordJob = viewModelScope.launch {
            val message = try {
                if (wasListening) delay(MIC_HANDOVER_MS)
                val segmenter = SpeechSegmenter()
                val pcm = withTimeout(RECORD_TIMEOUT_MS) {
                    MicrophoneSource.frames().mapNotNull { segmenter.feed(it) }.first()
                }
                samples += withContext(Dispatchers.Default) { extractor.extract(pcm) }
                "Sample ${samples.size} saved (${pcm.size * 1000 / 16_000} ms)"
            } catch (e: TimeoutCancellationException) {
                "Didn't hear a clear phrase. Speak a little louder, closer to the phone."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Enrollment recording failed", e)
                "Recording failed: ${e.message}"
            }
            _enrollment.update { it.copy(samples = samples.size, recording = false, message = message) }
        }
    }

    fun saveProfile() {
        viewModelScope.launch {
            val message = try {
                val profile = withContext(Dispatchers.Default) { VoiceProfile.build(samples.toList()) }
                withContext(Dispatchers.IO) { store.save(profile) }
                "Voice profile saved. Turn on listening to start."
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

    fun setListening(on: Boolean) {
        if (on) FinderService.start(getApplication()) else FinderService.stop(getApplication())
    }

    fun testResponse() {
        if (alertPlayer.isPlaying) alertPlayer.stop() else alertPlayer.play(repeat = 1)
    }

    override fun onCleared() {
        alertPlayer.stop()
    }

    private companion object {
        const val TAG = "FinderViewModel"
        const val RECORD_TIMEOUT_MS = 8_000L
        const val MIC_HANDOVER_MS = 500L
    }
}
