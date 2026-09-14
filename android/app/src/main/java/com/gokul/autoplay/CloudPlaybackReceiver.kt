package com.gokul.autoplay

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import java.time.ZoneId
import java.time.ZonedDateTime

class CloudPlaybackReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val scheduleId = intent?.getStringExtra("schedule_id") ?: return
        val schedule = CloudScheduleStore.find(context, scheduleId) ?: return
        if (!schedule.enabled || schedule.playlistUrl.isBlank()) return

        val zone = runCatching { ZoneId.of(schedule.timezone) }.getOrDefault(ZoneId.of("Asia/Kolkata"))
        val now = ZonedDateTime.now(zone)
        val occurrence = "${now.toLocalDate()}-${schedule.time.take(5)}"
        if (CloudScheduleStore.lastOccurrence(context, schedule.id) == occurrence) return
        CloudScheduleStore.markOccurrence(context, schedule.id, occurrence)

        val spotifyUri = toSpotifyPlaylistUri(schedule.playlistUrl)
        val launchIntent = Intent(Intent.ACTION_VIEW, Uri.parse(spotifyUri)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val canOpen = launchIntent.resolveActivity(context.packageManager) != null
        if (canOpen) {
            try {
                context.startActivity(launchIntent)
                showNotification(context, "AutoPlay started", schedule.name)
            } catch (_: SecurityException) {
                showNotification(context, "AutoPlay ready", "Open Spotify to start ${schedule.name}")
            }
        } else {
            showNotification(context, "Spotify not found", "Install Spotify to play ${schedule.name}")
        }

        CloudAlarmScheduler.scheduleNext(context, schedule)
    }

    private fun toSpotifyPlaylistUri(url: String): String {
        val match = Regex("open\\.spotify\\.com/playlist/([A-Za-z0-9]+)").find(url)
        return match?.let { "spotify:playlist:${it.groupValues[1]}" } ?: url
    }

    private fun showNotification(context: Context, title: String, message: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "AutoPlay", NotificationManager.IMPORTANCE_DEFAULT)
        )
        manager.notify(
            scheduleNotificationId(message),
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .build()
        )
    }

    private fun scheduleNotificationId(value: String): Int = value.hashCode() and 0x7fffffff

    companion object {
        private const val CHANNEL = "autoplay_execution"
    }
}
