package com.gokul.autoplay

import android.content.Context
import android.content.Intent
import com.gokul.autoplay.skills.TommySkillEngine
import java.time.ZoneId

/** A command Tommy should execute locally at a specific time. */
data class TommyScheduledCommand(
    val id: String,
    val command: String,
    val triggerAtMillis: Long
)

object TommyScheduler {
    private const val ACTION = "com.gokul.autoplay.SCHEDULED_TOMMY_COMMAND"
    private const val EXTRA_COMMAND_ID = "command_id"
    private const val EXTRA_COMMAND = "command"

    fun schedule(context: Context, command: String, triggerAtMillis: Long, id: String = "tommy-${triggerAtMillis}") {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(context, TommyScheduledCommandReceiver::class.java).apply {
            action = ACTION
            putExtra(EXTRA_COMMAND_ID, id)
            putExtra(EXTRA_COMMAND, command)
        }
        val pending = android.app.PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        alarm.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAtMillis, pending)
    }

    fun scheduleAt(context: Context, command: String, localDateTime: java.time.LocalDateTime, id: String = "tommy-${localDateTime}") {
        val millis = localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        require(millis > System.currentTimeMillis()) { "Scheduled time must be in the future" }
        schedule(context, command, millis, id)
    }

    fun cancel(context: Context, id: String) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val intent = Intent(context, TommyScheduledCommandReceiver::class.java).apply { action = ACTION }
        val pending = android.app.PendingIntent.getBroadcast(
            context,
            id.hashCode(),
            intent,
            android.app.PendingIntent.FLAG_NO_CREATE or android.app.PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarm.cancel(pending)
        pending.cancel()
    }

    internal fun commandFromIntent(intent: Intent): Pair<String, String>? {
        val id = intent.getStringExtra(EXTRA_COMMAND_ID).orEmpty()
        val command = intent.getStringExtra(EXTRA_COMMAND).orEmpty()
        return if (id.isNotBlank() && command.isNotBlank()) id to command else null
    }

    internal const val RECEIVER_ACTION = ACTION
}

class TommyScheduledCommandReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TommyScheduler.RECEIVER_ACTION) return
        val pair = TommyScheduler.commandFromIntent(intent) ?: return
        val serviceIntent = Intent(context, TommyScheduledCommandService::class.java).apply {
            putExtra("command_id", pair.first)
            putExtra("command", pair.second)
        }
        androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
    }
}

class TommyScheduledCommandService : android.app.Service() {
    override fun onCreate() {
        super.onCreate()
        val channel = android.app.NotificationChannel(
            "tommy_scheduled_commands",
            "Tommy scheduled commands",
            android.app.NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(android.app.NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val command = intent?.getStringExtra("command").orEmpty()
        startForeground(
            7201,
            android.app.Notification.Builder(this, "tommy_scheduled_commands")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Tommy")
                .setContentText(if (command.isBlank()) "Running scheduled command" else command)
                .setOngoing(false)
                .build()
        )

        if (command.isNotBlank()) {
            Thread {
                val result = TommySkillEngine.execute(this, command)
                sendStatus(result.message)
                stopSelfResult(startId)
            }.start()
        } else {
            stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    private fun sendStatus(message: String) {
        sendBroadcast(Intent(TommyStatusEvents.ACTION).apply {
            setPackage(packageName)
            putExtra(TommyStatusEvents.EXTRA_STATUS, TommyStatusEvents.MESSAGE)
            putExtra(TommyStatusEvents.EXTRA_TEXT, "Scheduled: $message")
        })
    }

    override fun onBind(intent: Intent?) = null
}
