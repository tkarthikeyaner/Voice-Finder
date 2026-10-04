package com.karthi.voicefinder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.karthi.voicefinder.AlertActivity
import com.karthi.voicefinder.MainActivity
import com.karthi.voicefinder.R
import com.karthi.voicefinder.power.PauseReason

object Notifications {
    const val LISTENING_ID = 1
    const val RESUME_ID = 2
    const val MIC_BLOCKED_ID = 3
    const val FOUND_ID = 4
    private const val CHANNEL_LISTENING = "listening"
    private const val CHANNEL_ALERTS = "alerts"
    private const val CHANNEL_FOUND = "found"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                // Low importance: always visible in the shade, but silent and no status-bar noise.
                NotificationChannel(CHANNEL_LISTENING, context.getString(R.string.channel_listening), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
                NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH),
                // The app plays its own sound; the channel only carries the STOP screen.
                NotificationChannel(CHANNEL_FOUND, context.getString(R.string.channel_found), NotificationManager.IMPORTANCE_HIGH).apply {
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setBypassDnd(true)
                },
            ),
        )
    }

    fun listening(context: Context, paused: PauseReason? = null): Notification {
        val stop = PendingIntent.getService(
            context, 0, FinderService.stopIntent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_LISTENING)
            .setSmallIcon(R.drawable.ic_finder)
            .setContentTitle(
                when (paused) {
                    PauseReason.LOW_BATTERY -> context.getString(R.string.notif_paused_battery_title)
                    PauseReason.CHARGING -> context.getString(R.string.notif_paused_charging_title)
                    null -> context.getString(R.string.notif_listening_title)
                },
            )
            .setContentText(
                when (paused) {
                    PauseReason.LOW_BATTERY -> context.getString(R.string.notif_paused_battery_text)
                    PauseReason.CHARGING -> context.getString(R.string.notif_paused_charging_text)
                    null -> context.getString(R.string.notif_listening_text)
                },
            )
            .setContentIntent(openApp(context))
            .setOnlyAlertOnce(true)
            .addAction(0, context.getString(R.string.notif_stop), stop)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    /** Shown after reboot on Android 11+, where a microphone service may only start from a user action. */
    fun resume(context: Context): Notification {
        val start = PendingIntent.getForegroundService(
            context, 1, FinderService.startIntent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_finder)
            .setContentTitle(context.getString(R.string.notif_resume_title))
            .setContentText(context.getString(R.string.notif_resume_text))
            .setContentIntent(start)
            .setAutoCancel(true)
            .build()
    }

    /** Android is muting the mic in the background; one tap is the user action it needs to restore it. */
    fun micBlocked(context: Context): Notification {
        val resume = PendingIntent.getForegroundService(
            context, 3, FinderService.resumeIntent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_finder)
            .setContentTitle(context.getString(R.string.notif_blocked_title))
            .setContentText(context.getString(R.string.notif_blocked_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notif_blocked_text)))
            .setContentIntent(resume)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
    }

    /** Shown while the response plays: full-screen STOP screen when locked, heads-up with STOP when in use. */
    fun found(context: Context): Notification {
        val stop = PendingIntent.getService(
            context, 4, FinderService.stopAlertIntent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val alertScreen = PendingIntent.getActivity(
            context, 5,
            Intent(context, AlertActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val settings = FinderSettings(context)
        return NotificationCompat.Builder(context, CHANNEL_FOUND)
            .setSmallIcon(R.drawable.ic_finder)
            .setContentTitle(settings.bannerTitle)
            .setContentText(settings.bannerMessage)
            .setSubText(context.getString(R.string.notif_found_text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(alertScreen, true)
            .setContentIntent(alertScreen)
            .addAction(0, context.getString(R.string.notif_stop_sound), stop)
            .setOngoing(true)
            .build()
    }

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 2, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )
}
