package com.karthi.voicefinder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.karthi.voicefinder.MainActivity
import com.karthi.voicefinder.R

object Notifications {
    const val LISTENING_ID = 1
    const val RESUME_ID = 2
    private const val CHANNEL_LISTENING = "listening"
    private const val CHANNEL_ALERTS = "alerts"

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannels(
            listOf(
                // Low importance: always visible in the shade, but silent and no status-bar noise.
                NotificationChannel(CHANNEL_LISTENING, context.getString(R.string.channel_listening), NotificationManager.IMPORTANCE_LOW)
                    .apply { setShowBadge(false) },
                NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH),
            ),
        )
    }

    fun listening(context: Context): Notification {
        val stop = PendingIntent.getService(
            context, 0, FinderService.stopIntent(context), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_LISTENING)
            .setSmallIcon(R.drawable.ic_finder)
            .setContentTitle(context.getString(R.string.notif_listening_title))
            .setContentText(context.getString(R.string.notif_listening_text))
            .setContentIntent(openApp(context))
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

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 2, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )
}
