package com.alsaeeddev.recapp.util

import com.alsaeeddev.recapp.R
import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.alsaeeddev.recapp.MainActivity

object NotificationHelper {
    const val CHANNEL_ID = "screen_recording_channel"
    const val NOTIFICATION_ID = 1001

    const val ACTION_START = "com.alsaeeddev.recapp.action.START"
    const val ACTION_PAUSE = "com.alsaeeddev.recapp.action.PAUSE"
    const val ACTION_RESUME = "com.alsaeeddev.recapp.action.RESUME"
    const val ACTION_STOP = "com.alsaeeddev.recapp.action.STOP"
    const val ACTION_SCREENSHOT = "com.alsaeeddev.recapp.action.SCREENSHOT"

/*    const val ACTION_START = "com.example.action.START"
    const val ACTION_PAUSE = "com.example.action.PAUSE"
    const val ACTION_RESUME = "com.example.action.RESUME"
    const val ACTION_STOP = "com.example.action.STOP"
    const val ACTION_SCREENSHOT = "com.example.action.SCREENSHOT"*/

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Recorder Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows screen recording status and quick controls"
                setSound(null, null)
                enableVibration(false)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun buildNotification(
        context: Context,
        serviceClass: Class<*>,
        statusText: String,
        isRecording: Boolean,
        isPaused: Boolean
    ): Notification {
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenApp = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val pauseIntent = Intent(context, serviceClass).apply { action = ACTION_PAUSE }
        val pendingPause = PendingIntent.getService(
            context, 1, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val resumeIntent = Intent(context, serviceClass).apply { action = ACTION_RESUME }
        val pendingResume = PendingIntent.getService(
            context, 2, resumeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, serviceClass).apply { action = ACTION_STOP }
        val pendingStop = PendingIntent.getService(
            context, 3, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val screenshotIntent = Intent(context, serviceClass).apply { action = ACTION_SCREENSHOT }
        val pendingScreenshot = PendingIntent.getService(
            context, 4, screenshotIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Screen Recorder")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingOpenApp)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (isRecording) {
            if (isPaused) {
                builder.addAction(android.R.drawable.ic_media_play, "Resume", pendingResume)
            } else {
                builder.addAction(android.R.drawable.ic_media_pause, "Pause", pendingPause)
            }
            builder.addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStop)
            builder.addAction(android.R.drawable.ic_menu_camera, "Screenshot", pendingScreenshot)
        }

        return builder.build()
    }
}
