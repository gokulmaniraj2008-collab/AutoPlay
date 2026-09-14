package com.gokul.autoplay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat

class PlaybackReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val schedule = ScheduleStore.load(context)
        if (!schedule.enabled || schedule.playlistUrl.isBlank()) return

        val launchIntent = Intent(Intent.ACTION_VIEW, Uri.parse(schedule.playlistUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val packageManager = context.packageManager
        val resolved = launchIntent.resolveActivity(packageManager)

        showNotification(
            context,
            if (resolved != null) "Opening your playlist" else "Playlist link needs attention",
            if (resolved != null) "AutoPlay launched the playlist link. Playback is controlled by the music app." else "No app can open the saved playlist URL."
        )

        if (resolved != null) context.startActivity(launchIntent)

        // Keep the daily schedule alive after this execution.
        AutoPlayScheduler.schedule3Pm(context)
    }

    private fun showNotification(context: Context, title: String, message: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "AutoPlay execution",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        manager.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val CHANNEL_ID = "autoplay_execution"
        private const val NOTIFICATION_ID = 3001
    }
}
