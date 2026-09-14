package com.gokul.autoplay

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object CloudScheduleSync {
    private fun connection(path: String, method: String, body: String? = null): HttpURLConnection {
        return (URL(SupabaseConfig.URL + "/rest/v1/" + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = body != null
            setRequestProperty("apikey", SupabaseConfig.PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer ${SupabaseConfig.PUBLISHABLE_KEY}")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (body != null) setRequestProperty("Prefer", "return=minimal")
        }
    }

    fun sync(context: Context, callback: (Result<Int>) -> Unit) {
        Thread {
            try {
                val c = connection("schedules?select=id,name,time,playlist_url,enabled,timezone&order=time.asc", "GET")
                val code = c.responseCode
                if (code !in 200..299) throw IllegalStateException("Supabase returned HTTP $code")
                val array = JSONArray(c.inputStream.bufferedReader().use { it.readText() })
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
            } catch (error: Throwable) { callback(Result.failure(error)) }
        }.start()
    }

    fun create(context: Context, name: String, time: String, enabled: Boolean, timezone: String, callback: (Result<String>) -> Unit) {
        Thread {
            try {
                val id = java.util.UUID.randomUUID().toString()
                val body = JSONObject().apply {
                    put("id", id)
                    put("name", name)
                    put("time", time)
                    put("playlist_url", "")
                    put("enabled", enabled)
                    put("timezone", timezone)
                }.toString()
                val c = connection("schedules", "POST", body)
                val code = c.responseCode
                if (code !in 200..299) throw IllegalStateException("Supabase returned HTTP $code")
                callback(Result.success(id))
            } catch (error: Throwable) { callback(Result.failure(error)) }
        }.start()
    }

    fun update(context: Context, scheduleId: String, name: String, time: String, enabled: Boolean, timezone: String, callback: (Result<Unit>) -> Unit) {
        Thread {
            try {
                val body = JSONObject().apply {
                    put("name", name)
                    put("time", time)
                    put("enabled", enabled)
                    put("timezone", timezone)
                    put("updated_at", java.time.Instant.now().toString())
                }.toString()
                val c = connection("schedules?id=eq.$scheduleId", "PATCH", body)
                val code = c.responseCode
                if (code !in 200..299) throw IllegalStateException("Supabase returned HTTP $code")
                callback(Result.success(Unit))
            } catch (error: Throwable) { callback(Result.failure(error)) }
        }.start()
    }

    fun setEnabled(context: Context, scheduleId: String, enabled: Boolean, callback: (Result<Unit>) -> Unit) {
        Thread {
            try {
                val c = connection("schedules?id=eq.$scheduleId", "PATCH", JSONObject().put("enabled", enabled).put("updated_at", java.time.Instant.now().toString()).toString())
                val code = c.responseCode
                if (code !in 200..299) throw IllegalStateException("Supabase returned HTTP $code")
                val updated = CloudScheduleStore.loadAll(context).map { s -> if (s.id == scheduleId) s.copy(enabled = enabled) else s }
                CloudScheduleStore.saveAll(context, updated)
                callback(Result.success(Unit))
            } catch (error: Throwable) { callback(Result.failure(error)) }
        }.start()
    }

    fun delete(context: Context, scheduleId: String, callback: (Result<Unit>) -> Unit) {
        Thread {
            try {
                val c = connection("schedules?id=eq.$scheduleId", "DELETE")
                val code = c.responseCode
                if (code !in 200..299) throw IllegalStateException("Supabase returned HTTP $code")
                ExactCloudAlarmScheduler.cancel(context, scheduleId)
                LocalTrackStore.remove(context, scheduleId)
                CloudScheduleStore.saveAll(context, CloudScheduleStore.loadAll(context).filterNot { it.id == scheduleId })
                callback(Result.success(Unit))
            } catch (error: Throwable) { callback(Result.failure(error)) }
        }.start()
    }
}
