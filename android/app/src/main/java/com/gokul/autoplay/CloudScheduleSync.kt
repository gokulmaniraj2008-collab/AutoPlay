package com.gokul.autoplay

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object CloudScheduleSync {
    fun sync(context: Context, callback: (Result<Int>) -> Unit) {
        Thread {
            try {
                val endpoint = SupabaseConfig.URL + "/rest/v1/schedules?select=id,name,time,playlist_url,enabled,timezone&order=time.asc"
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                    setRequestProperty("Authorization", "Bearer ${SupabaseConfig.PUBLISHABLE_KEY}")
                    setRequestProperty("Accept", "application/json")
                }

                val code = connection.responseCode
                if (code !in 200..299) throw IllegalStateException("Supabase returned HTTP $code")
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(body)
                val schedules = buildList {
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        add(CloudScheduleStore.Schedule(
                            id = item.getString("id"),
                            name = item.optString("name", "AutoPlay"),
                            enabled = item.optBoolean("enabled", true),
                            time = item.optString("time", "15:00"),
                            playlistUrl = item.optString("playlist_url", ""),
                            timezone = item.optString("timezone", "Asia/Kolkata")
                        ))
                    }
                }
                CloudScheduleStore.saveAll(context, schedules)
                callback(Result.success(schedules.size))
            } catch (error: Throwable) {
                callback(Result.failure(error))
            }
        }.start()
    }

    fun setEnabled(context: Context, scheduleId: String, enabled: Boolean, callback: (Result<Unit>) -> Unit) {
        Thread {
            try {
                val endpoint = SupabaseConfig.URL + "/rest/v1/schedules?id=eq.$scheduleId"
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "PATCH"
                    connectTimeout = 10_000
                    readTimeout = 10_000
                    doOutput = true
                    setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY)
                    setRequestProperty("Authorization", "Bearer ${SupabaseConfig.PUBLISHABLE_KEY}")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Prefer", "return=minimal")
                }
                connection.outputStream.use { output ->
                    output.write(JSONObject().put("enabled", enabled).toString().toByteArray())
                }
                val code = connection.responseCode
                if (code !in 200..299) throw IllegalStateException("Supabase returned HTTP $code")

                val updated = CloudScheduleStore.loadAll(context).map { schedule ->
                    if (schedule.id == scheduleId) schedule.copy(enabled = enabled) else schedule
                }
                CloudScheduleStore.saveAll(context, updated)
                callback(Result.success(Unit))
            } catch (error: Throwable) {
                callback(Result.failure(error))
            }
        }.start()
    }
}
