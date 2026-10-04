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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karthi.voicefinder.power.Reliability
import com.karthi.voicefinder.service.FinderService
import com.karthi.voicefinder.ui.FinderActions
import com.karthi.voicefinder.ui.FinderScreen
import com.karthi.voicefinder.ui.FinderViewModel
import com.karthi.voicefinder.ui.ReliabilityState

class MainActivity : ComponentActivity() {

    private val viewModel: FinderViewModel by viewModels()
    private var reliability by mutableStateOf(ReliabilityState(false, false, false, false))

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refreshReliability()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val actions = FinderActions(
            grantPermissions = ::requestPermissions,
            recordSample = { if (reliability.micGranted) viewModel.recordSample() else requestPermissions() },
            saveProfile = viewModel::saveProfile,
            resetProfile = viewModel::resetEnrollment,
            setSensitivity = viewModel::setSensitivity,
            setListening = viewModel::setListening,
            testResponse = viewModel::testResponse,
            openBatterySettings = { launch(Reliability.batteryExemptionIntent(this)) },
            openDndSettings = { launch(Reliability.dndAccessIntent()) },
            openAppSettings = { launch(Reliability.appDetailsIntent(this)) },
        )
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            MaterialTheme(colorScheme = colors) {
                Surface {
                    val enrollment by viewModel.enrollment.collectAsStateWithLifecycle()
                    val listening by viewModel.serviceRunning.collectAsStateWithLifecycle()
                    val sensitivity by viewModel.sensitivity.collectAsStateWithLifecycle()
                    val lastMatch by viewModel.lastMatch.collectAsStateWithLifecycle()
                    FinderScreen(
                        phrase = getString(R.string.wake_phrase),
                        enrollment = enrollment,
                        reliability = reliability,
                        listening = listening,
                        sensitivity = sensitivity,
                        lastMatch = lastMatch,
                        actions = actions,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Settings screens return here, so re-check what the user changed.
        refreshReliability()
        // Opening the app is a user action, so it also restores a microphone Android muted in the background.
        if (FinderService.micBlocked.value) startForegroundService(FinderService.resumeIntent(this))
    }

    private fun refreshReliability() {
        reliability = ReliabilityState(
            micGranted = granted(Manifest.permission.RECORD_AUDIO),
            notificationsGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || granted(Manifest.permission.POST_NOTIFICATIONS),
            batteryUnrestricted = Reliability.isBatteryUnrestricted(this),
            dndAccess = Reliability.hasDndAccess(this),
        )
    }

    private fun requestPermissions() {
        val permissions = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun launch(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Some OEM builds strip these settings screens; fall back to the app details page.
            Log.w("MainActivity", "Settings screen unavailable: ${intent.action}", e)
            startActivity(Reliability.appDetailsIntent(this))
        }
    }
}
