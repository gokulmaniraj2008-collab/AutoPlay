package com.gokul.autoplay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat

class LocalPlaybackService : Service() {
    private var player: MediaPlayer? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val uri = intent?.getStringExtra(EXTRA_URI)?.let(Uri::parse)
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "AutoPlay"
        if (uri == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, notification(title))
        player?.release()
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setDataSource(this@LocalPlaybackService, uri)
            setOnPreparedListener { it.start() }
            setOnCompletionListener {
                it.release()
                player = null
                stopSelf(startId)
            }
            setOnErrorListener { mp, _, _ ->
                mp.release()
                player = null
                stopSelf(startId)
                true
            }
            prepareAsync()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(title: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "AutoPlay playback", NotificationManager.IMPORTANCE_LOW)
        )
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pending = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                9001,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("AutoPlay playing")
            .setContentText(title)
            .setOngoing(true)
            .setContentIntent(pending)
            .build()
    }

    companion object {
        const val EXTRA_URI = "audio_uri"
        const val EXTRA_TITLE = "audio_title"
        private const val CHANNEL = "autoplay_playback"
        private const val NOTIFICATION_ID = 9002
    }
}
