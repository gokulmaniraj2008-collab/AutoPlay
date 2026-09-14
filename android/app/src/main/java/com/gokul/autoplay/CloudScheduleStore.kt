package com.gokul.autoplay

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object CloudScheduleStore {
    private const val PREFS = "autoplay_prefs_v2"
    private const val KEY_SCHEDULES = "cloud_schedules"
    private const val KEY_LAST_PREFIX = "last_occurrence_"

    data class Schedule(
        val id: String,
        val name: String,
        val enabled: Boolean,
        val scheduledDate: String?,
        val time: String,
        val playlistUrl: String,
        val timezone: String
    )

    fun loadAll(context: Context): List<Schedule> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SCHEDULES, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(Schedule(
                        id = item.getString("id"),
                        name = item.optString("name", "AutoPlay"),
                        enabled = item.optBoolean("enabled", true),
                        scheduledDate = item.optString("scheduled_date", "").ifBlank { null },
                        time = item.optString("time", "15:00"),
                        playlistUrl = item.optString("playlist_url", ""),
                        timezone = item.optString("timezone", "Asia/Kolkata")
                    ))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveAll(context: Context, schedules: List<Schedule>) {
        val array = JSONArray()
        schedules.forEach { s ->
            array.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("enabled", s.enabled)
                put("scheduled_date", s.scheduledDate ?: JSONObject.NULL)
                put("time", s.time)
                put("playlist_url", s.playlistUrl)
                put("timezone", s.timezone)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SCHEDULES, array.toString()).apply()
    }

    fun find(context: Context, id: String): Schedule? = loadAll(context).firstOrNull { it.id == id }

    fun lastOccurrence(context: Context, id: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LAST_PREFIX + id, null)

    fun markOccurrence(context: Context, id: String, occurrence: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LAST_PREFIX + id, occurrence).apply()
    }
}
