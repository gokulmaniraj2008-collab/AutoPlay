package com.gokul.autoplay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import java.time.ZoneId
import java.time.ZonedDateTime

class ExactCloudPlaybackReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val id = intent?.getStringExtra("schedule_id") ?: return
        val schedule = CloudScheduleStore.find(context, id) ?: return
        if (!schedule.enabled) return

        val zone = runCatching { ZoneId.of(schedule.timezone) }.getOrDefault(ZoneId.of("Asia/Kolkata"))
        val occurrence = "${ZonedDateTime.now(zone).toLocalDate()}-${schedule.time.take(5)}"
        if (CloudScheduleStore.lastOccurrence(context, id) == occurrence) return
        CloudScheduleStore.markOccurrence(context, id, occurrence)

        val track = LocalTrackStore.get(context, id)
        if (track == null) {
            showNotification(context, "No local music selected", "Open AutoPlay and choose a song for ${schedule.name}")
        } else {
            val playbackIntent = Intent(context, LocalPlaybackService::class.java).apply {
                putExtra(LocalPlaybackService.EXTRA_URI, track.uri)
                putExtra(LocalPlaybackService.EXTRA_TITLE, track.name)
            }
            runCatching {
                ContextCompat.startForegroundService(context, playbackIntent)
            }.onFailure { error ->
                showNotification(context, "AutoPlay playback failed", error.message ?: "Unable to start music")
            }
        }

        ExactCloudAlarmScheduler.schedule(context, schedule)
    }

    private fun showNotification(context: Context, title: String, message: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "AutoPlay", NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.notify(
            (title + message).hashCode() and 0x7fffffff,
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .build()
        )
    }

    companion object {
        private const val CHANNEL = "autoplay_execution"
    }
}
