package com.karthi.voicefinder.service

import android.Manifest
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.karthi.voicefinder.voice.VoiceProfileStore

/** Restores listening after a reboot or app update if the user had it switched on. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!FinderSettings(context).listeningEnabled || !VoiceProfileStore(context).exists()) return

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            try {
                FinderService.start(context)
            } catch (e: Exception) {
                Log.e(TAG, "Could not restart listening after boot", e)
            }
            return
        }
        // Android 11+ only grants a background-started service microphone access after a user action,
        // so ask for one tap instead of starting a service that would record silence.
        val canNotify = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (canNotify) {
            context.getSystemService(NotificationManager::class.java).notify(Notifications.RESUME_ID, Notifications.resume(context))
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
