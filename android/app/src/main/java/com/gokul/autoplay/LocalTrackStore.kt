package com.gokul.autoplay

import android.content.Context
import android.net.Uri
import org.json.JSONObject

object LocalTrackStore {
    private const val PREFS = "autoplay_local_tracks_v1"
    private const val KEY_TRACKS = "tracks"

    data class Track(val uri: String, val name: String)

    fun get(context: Context, scheduleId: String): Track? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TRACKS, "{}") ?: "{}"
        return runCatching {
            val item = JSONObject(raw).optJSONObject(scheduleId) ?: return null
            Track(item.optString("uri"), item.optString("name", "Local music"))
        }.getOrNull()?.takeIf { it.uri.isNotBlank() }
    }

    fun put(context: Context, scheduleId: String, uri: Uri, name: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val root = runCatching { JSONObject(prefs.getString(KEY_TRACKS, "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        root.put(scheduleId, JSONObject().apply {
            put("uri", uri.toString())
            put("name", name)
        })
        prefs.edit().putString(KEY_TRACKS, root.toString()).apply()
    }

    fun remove(context: Context, scheduleId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val root = runCatching { JSONObject(prefs.getString(KEY_TRACKS, "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        root.remove(scheduleId)
        prefs.edit().putString(KEY_TRACKS, root.toString()).apply()
    }
}
