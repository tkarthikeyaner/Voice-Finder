package com.karthi.voicefinder

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karthi.voicefinder.power.Reliability
import com.karthi.voicefinder.service.FinderService
import com.karthi.voicefinder.ui.FinderActions
import com.karthi.voicefinder.ui.FinderScreen
import com.karthi.voicefinder.ui.FinderUiState
import com.karthi.voicefinder.ui.FinderViewModel
import com.karthi.voicefinder.ui.ReliabilityState
import com.karthi.voicefinder.ui.VoiceFinderTheme

class MainActivity : ComponentActivity() {

    private val viewModel: FinderViewModel by viewModels()
    private var reliability by mutableStateOf(ReliabilityState())

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshReliability()
    }

    private val soundPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importSound)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val actions = FinderActions(
            grantPermissions = ::requestPermissions,
            recordSample = { if (reliability.micGranted) viewModel.recordSample() else requestPermissions() },
            saveProfile = viewModel::saveProfile,
            recordWrongPhrase = { if (reliability.micGranted) viewModel.recordWrongPhrase() else requestPermissions() },
            clearWrongPhrases = viewModel::clearWrongPhrases,
            resetProfile = viewModel::resetEnrollment,
            setSensitivity = viewModel::setSensitivity,
            setListening = viewModel::setListening,
            resumeListening = viewModel::resumeListening,
            simulateTrigger = viewModel::simulateTrigger,
            chooseSound = ::chooseSound,
            resetSound = viewModel::resetSound,
            togglePreview = viewModel::togglePreview,
            changeRepeat = viewModel::changeRepeat,
            setPauseOnLowBattery = viewModel::setPauseOnLowBattery,
            setLowBatteryPercent = viewModel::setLowBatteryPercent,
            setPauseWhileCharging = viewModel::setPauseWhileCharging,
            setBannerTitle = viewModel::setBannerTitle,
            setBannerMessage = viewModel::setBannerMessage,
            setBannerTheme = viewModel::setBannerTheme,
            resetBanner = viewModel::resetBanner,
            openBatterySettings = { launch(Reliability.batteryExemptionIntent(this)) },
            openDndSettings = { launch(Reliability.dndAccessIntent()) },
            openFullScreenSettings = { launch(Reliability.fullScreenIntentSettings(this)) },
            openAppSettings = { launch(Reliability.appDetailsIntent(this)) },
        )
        setContent {
            VoiceFinderTheme {
                val snackbar = remember { SnackbarHostState() }
                LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
                val enrollment by viewModel.enrollment.collectAsStateWithLifecycle()
                val listening by viewModel.serviceRunning.collectAsStateWithLifecycle()
                val micBlocked by viewModel.micBlocked.collectAsStateWithLifecycle()
                val micLevel by viewModel.micLevel.collectAsStateWithLifecycle()
                val recordLevel by viewModel.recordLevel.collectAsStateWithLifecycle()
                val sensitivity by viewModel.sensitivity.collectAsStateWithLifecycle()
                val lastMatch by viewModel.lastMatch.collectAsStateWithLifecycle()
                val soundName by viewModel.soundName.collectAsStateWithLifecycle()
                val repeatCount by viewModel.repeatCount.collectAsStateWithLifecycle()
                val previewing by viewModel.previewing.collectAsStateWithLifecycle()
                val pauseReason by viewModel.pauseReason.collectAsStateWithLifecycle()
                val powerRules by viewModel.powerRules.collectAsStateWithLifecycle()
                val banner by viewModel.banner.collectAsStateWithLifecycle()
                FinderScreen(
                    phrase = getString(R.string.wake_phrase),
                    state = FinderUiState(
                        enrollment = enrollment,
                        reliability = reliability,
                        listening = listening,
                        micBlocked = micBlocked,
                        micLevel = micLevel,
                        recordLevel = recordLevel,
                        sensitivity = sensitivity,
                        lastMatch = lastMatch,
                        soundName = soundName,
                        repeatCount = repeatCount,
                        previewing = previewing,
                        pauseReason = pauseReason,
                        powerRules = powerRules,
                        banner = banner,
                    ),
                    actions = actions,
                    snackbar = snackbar,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Settings screens return here, so re-check what the user changed.
        refreshReliability()
        viewModel.refresh()
        // Opening the app is a user action, so it also restores a microphone Android muted in the background.
        if (FinderService.micBlocked.value) viewModel.resumeListening()
    }

    private fun refreshReliability() {
        reliability = ReliabilityState(
            micGranted = granted(Manifest.permission.RECORD_AUDIO),
            notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(Manifest.permission.POST_NOTIFICATIONS),
            batteryUnrestricted = Reliability.isBatteryUnrestricted(this),
            dndAccess = Reliability.hasDndAccess(this),
            fullScreenAllowed = Reliability.canShowFullScreen(this),
        )
    }

    private fun requestPermissions() {
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun chooseSound() {
        try {
            soundPicker.launch(arrayOf("audio/*"))
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No file picker available", e)
        }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun launch(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Some OEM builds strip these settings screens; fall back to the app details page.
            Log.w(TAG, "Settings screen unavailable: ${intent.action}", e)
            startActivity(Reliability.appDetailsIntent(this))
        }
    }

    private companion object {
        const val TAG = "MainActivity"
    }
}
