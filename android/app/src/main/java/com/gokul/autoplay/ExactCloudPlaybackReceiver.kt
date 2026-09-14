package com.gokul.autoplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
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
        val spotifyUri = Regex("open\\.spotify\\.com/playlist/([A-Za-z0-9]+)").find(schedule.playlistUrl)?.let { "spotify:playlist:${it.groupValues[1]}" } ?: schedule.playlistUrl
        val launch = Intent(Intent.ACTION_VIEW, Uri.parse(spotifyUri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launch.resolveActivity(context.packageManager) != null) {
            runCatching { context.startActivity(launch) }
        }
        ExactCloudAlarmScheduler.schedule(context, schedule)
    }
}
