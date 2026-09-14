package com.gokul.autoplay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import java.time.ZoneId
import java.time.ZonedDateTime

class ExactCloudPlaybackReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val id = intent?.getStringExtra("schedule_id") ?: return
        val schedule = CloudScheduleStore.find(context, id) ?: return
        if (!schedule.enabled || schedule.playlistUrl.isBlank()) return

        val zone = runCatching { ZoneId.of(schedule.timezone) }.getOrDefault(ZoneId.of("Asia/Kolkata"))
        val occurrence = "${ZonedDateTime.now(zone).toLocalDate()}-${schedule.time.take(5)}"
        if (CloudScheduleStore.lastOccurrence(context, id) == occurrence) return
        CloudScheduleStore.markOccurrence(context, id, occurrence)

        val spotifyUri = Regex("open\\.spotify\\.com/playlist/([A-Za-z0-9]+)")
            .find(schedule.playlistUrl)
            ?.let { "spotify:playlist:${it.groupValues[1]}" }
            ?: schedule.playlistUrl

        val pending = goAsync()
        SpotifyPlayback.play(context, spotifyUri) { result ->
            try {
                result.onSuccess {
                    showNotification(context, "AutoPlay playing", schedule.name)
                }.onFailure { error ->
                    showNotification(
                        context,
                        "Spotify playback failed",
                        error.message ?: "Open AutoPlay and connect Spotify once"
                    )
                }
            } finally {
                pending.finish()
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
