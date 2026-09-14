package com.gokul.autoplay

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object CloudAlarmScheduler {
    private const val EXTRA_SCHEDULE_ID = "schedule_id"
    private const val PREFS = "autoplay_alarm_ids"
    private const val KEY_IDS = "ids"

    fun sync(context: Context) {
        val previous = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_IDS, emptySet()) ?: emptySet()
        previous.forEach { cancel(context, it) }
        val current = mutableSetOf<String>()
        CloudScheduleStore.loadAll(context)
            .filter { it.enabled && it.playlistUrl.isNotBlank() }
            .forEach {
                scheduleNext(context, it)
                current.add(it.id)
            }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_IDS, current).apply()
    }

    fun scheduleNext(context: Context, schedule: CloudScheduleStore.Schedule) {
        val zone = runCatching { ZoneId.of(schedule.timezone) }.getOrDefault(ZoneId.of("Asia/Kolkata"))
        val time = runCatching { LocalTime.parse(schedule.time.take(5)) }.getOrDefault(LocalTime.of(15, 0))
        val now = ZonedDateTime.now(zone)
        var next = now.withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)

        val intent = Intent(context, CloudPlaybackReceiver::class.java).apply {
            putExtra(EXTRA_SCHEDULE_ID, schedule.id)
        }
        val pending = PendingIntent.getBroadcast(
            context, schedule.id.hashCode() and 0x7fffffff, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, next.toInstant().toEpochMilli(), pending
        )
    }

    fun cancel(context: Context, id: String) {
        val intent = Intent(context, CloudPlaybackReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, id.hashCode() and 0x7fffffff, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        context.getSystemService(AlarmManager::class.java).cancel(pending)
    }
}
