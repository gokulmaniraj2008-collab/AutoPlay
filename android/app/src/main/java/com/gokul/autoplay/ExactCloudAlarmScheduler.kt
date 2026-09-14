package com.gokul.autoplay

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object ExactCloudAlarmScheduler {
    fun schedule(context: Context, schedule: CloudScheduleStore.Schedule) {
        if (!schedule.enabled || LocalTrackStore.get(context, schedule.id) == null) return
        val zone = runCatching { ZoneId.of(schedule.timezone) }.getOrDefault(ZoneId.of("Asia/Kolkata"))
        val time = runCatching { LocalTime.parse(schedule.time.take(5)) }.getOrDefault(LocalTime.of(15, 0))
        val now = ZonedDateTime.now(zone)
        var next = now.withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val intent = Intent(context, ExactCloudPlaybackReceiver::class.java).putExtra("schedule_id", schedule.id)
        val pending = PendingIntent.getBroadcast(
            context,
            schedule.id.hashCode() and 0x7fffffff,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarms = context.getSystemService(AlarmManager::class.java)
        val millis = next.toInstant().toEpochMilli()
        if (Build.VERSION.SDK_INT >= 31 && alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pending)
        }
    }

    fun sync(context: Context) {
        CloudScheduleStore.loadAll(context)
            .filter { it.enabled && LocalTrackStore.get(context, it.id) != null }
            .forEach { schedule(context, it) }
    }
}
