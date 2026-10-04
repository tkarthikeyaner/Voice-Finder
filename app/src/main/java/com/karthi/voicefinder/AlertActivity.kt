package com.karthi.voicefinder

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karthi.voicefinder.service.FinderService
import com.karthi.voicefinder.service.FinderSettings
import com.karthi.voicefinder.ui.AlertScreen
import com.karthi.voicefinder.ui.VoiceFinderTheme

/** Full-screen STOP screen shown over the lock screen while the "found" sound plays. */
class AlertActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge()
        val settings = FinderSettings(this)
        setContent {
            VoiceFinderTheme {
                val alerting by FinderService.alerting.collectAsStateWithLifecycle()
                // Close by itself once the sound has finished its plays.
                LaunchedEffect(alerting) { if (!alerting) finish() }
                AlertScreen(
                    repeatCount = settings.repeatCount,
                    title = settings.bannerTitle,
                    message = settings.bannerMessage,
                    themeIndex = settings.bannerTheme,
                    onStop = ::stopAlert,
                )
            }
        }
    }

    private fun stopAlert() {
        startService(FinderService.stopAlertIntent(this))
        finish()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}
