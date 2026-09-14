package com.gokul.autoplay

import android.content.Context

/** Simple local persistence for the first AutoPlay MVP. */
object ScheduleStore {
    private const val PREFS = "autoplay_prefs"
    private const val KEY_ENABLED = "schedule_enabled"
    private const val KEY_TIME = "schedule_time"
    private const val KEY_URL = "playlist_url"

    data class Schedule(
        val enabled: Boolean,
        val time: String,
        val playlistUrl: String
    )

    fun load(context: Context): Schedule {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Schedule(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            time = prefs.getString(KEY_TIME, "15:00") ?: "15:00",
            playlistUrl = prefs.getString(KEY_URL, "") ?: ""
        )
    }

    fun save(context: Context, schedule: Schedule) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, schedule.enabled)
            .putString(KEY_TIME, schedule.time)
            .putString(KEY_URL, schedule.playlistUrl)
            .apply()
    }
}
