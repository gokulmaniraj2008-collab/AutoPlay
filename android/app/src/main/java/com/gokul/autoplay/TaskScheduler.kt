package com.gokul.autoplay

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object TaskScheduler {
    private const val BASE_REQUEST_CODE = 9000

    fun schedule(context: Context, task: PhoneTask): Boolean {
        val at = task.scheduledAtMillis ?: return false
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, TaskReceiver::class.java).putExtra(TaskReceiver.EXTRA_TASK_ID, task.id)
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(task.id),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= 31 && !alarmManager.canScheduleExactAlarms()) return false
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        return true
    }

    fun cancel(context: Context, taskId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, TaskReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode(taskId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
    }

    private fun requestCode(id: String) = BASE_REQUEST_CODE + (id.hashCode() and 0x7fffffff) % 100000
}
