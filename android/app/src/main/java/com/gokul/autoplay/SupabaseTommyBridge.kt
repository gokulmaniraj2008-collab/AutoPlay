package com.gokul.autoplay

import android.content.Context
import android.provider.Settings
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class SupabaseTommyBridge(
    private val context: Context,
    private val onCommand: (JSONObject) -> String
) {
    private val running = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()
    private val deviceId: String by lazy {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        if (!androidId.isNullOrBlank()) "android-$androidId" else "android-${UUID.randomUUID()}"
    }

    fun start() {
        if (BuildConfig.SUPABASE_PUBLISHABLE_KEY.isBlank()) return
        if (!running.compareAndSet(false, true)) return
        executor.execute {
            upsertDevice("online")
            while (running.get()) {
                try { pollOnce() } catch (_: Exception) { }
                try { Thread.sleep(800L) } catch (_: InterruptedException) { break }
            }
            upsertDevice("offline")
        }
    }

    fun stop() {
        running.set(false)
        try { upsertDevice("offline") } catch (_: Exception) { }
        executor.shutdownNow()
    }

    private fun pollOnce() {
        val url = "$SUPABASE_URL/rest/v1/tommy_commands?select=*&channel=eq.$CHANNEL&status=eq.pending&order=created_at.asc&limit=1"
        val response = request("GET", url, null)
        if (response.code !in 200..299 || response.body.isBlank() || response.body == "[]") return

        val command = org.json.JSONArray(response.body).getJSONObject(0)
        val id = command.getString("id")
        if (!patchCommand(id, "processing", null)) return

        postEvent("processing", "Tommy received: ${command.optString("command")}", id)
        try {
            val result = onCommand(command)
            if (patchCommand(id, "done", result)) {
                postEvent("done", result, id)
            }
        } catch (e: Exception) {
            val message = e.message ?: "Tommy could not execute the command"
            if (patchCommand(id, "failed", message)) {
                postEvent("failed", message, id)
            }
        }
    }

    private fun patchCommand(id: String, status: String, response: String?): Boolean {
        val payload = JSONObject().apply {
            put("status", status)
            if (response != null) put("response", response)
            put("updated_at", Instant.now().toString())
        }
        val idEncoded = URLEncoder.encode(id, "UTF-8")
        return request(
            "PATCH",
            "$SUPABASE_URL/rest/v1/tommy_commands?id=eq.$idEncoded",
            payload.toString(),
            "return=minimal"
        ).code in 200..299
    }

    private fun postEvent(type: String, message: String, commandId: String?) {
        val payload = JSONObject().apply {
            put("channel", CHANNEL)
            put("source", "android")
            put("device_id", deviceId)
            put("event_type", type)
            put("message", message)
            if (commandId != null) put("command_id", commandId)
        }
        request("POST", "$SUPABASE_URL/rest/v1/tommy_events", payload.toString())
    }

    private fun upsertDevice(status: String) {
        val payload = JSONObject().apply {
            put("device_id", deviceId)
            put("platform", "android")
            put("app_version", "0.7.0")
            put("last_seen", Instant.now().toString())
            put("status", status)
        }
        request("POST", "$SUPABASE_URL/rest/v1/tommy_devices", payload.toString(), "resolution=merge-duplicates")
    }

    private data class HttpResult(val code: Int, val body: String)

    private fun request(method: String, urlString: String, body: String?, prefer: String? = null): HttpResult {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8000
            readTimeout = 10000
            setRequestProperty("apikey", BuildConfig.SUPABASE_PUBLISHABLE_KEY)
            setRequestProperty("Authorization", "Bearer ${BuildConfig.SUPABASE_PUBLISHABLE_KEY}")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (prefer != null) setRequestProperty("Prefer", prefer)
            doInput = true
            if (body != null) doOutput = true
        }
        return try {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..399) connection.inputStream else connection.errorStream
            HttpResult(code, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally { connection.disconnect() }
    }

    companion object {
        private const val SUPABASE_URL = "https://bqrpgtxtmatxwtdpuoyh.supabase.co"
        private const val CHANNEL = "tommy-main"
    }
}
