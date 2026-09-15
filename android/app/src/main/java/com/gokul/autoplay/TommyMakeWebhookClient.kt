package com.gokul.autoplay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Sends Tommy commands to the Make workflow without blocking the UI thread. */
object TommyMakeWebhookClient {
    private const val TIMEOUT_MS = 8000

    suspend fun sendCommand(command: String): Result<String?> = withContext(Dispatchers.IO) {
        val webhookUrl = BuildConfig.TOMMY_MAKE_WEBHOOK_URL.trim()
        if (webhookUrl.isBlank()) return@withContext Result.success(null)

        runCatching {
            val connection = (URL(webhookUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }

            val payload = JSONObject().apply {
                put("command", command)
                put("source", "tommy_android")
                put("timestamp", System.currentTimeMillis())
            }.toString()

            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()

            if (status !in 200..299) {
                throw IllegalStateException("Make webhook HTTP $status")
            }

            response.takeIf { it.isNotBlank() }
        }
    }
}
