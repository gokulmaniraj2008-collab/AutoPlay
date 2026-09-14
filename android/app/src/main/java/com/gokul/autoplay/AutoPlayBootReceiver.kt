package com.gokul.autoplay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AutoPlayBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> CloudAlarmScheduler.sync(context)
        }
    }
}
