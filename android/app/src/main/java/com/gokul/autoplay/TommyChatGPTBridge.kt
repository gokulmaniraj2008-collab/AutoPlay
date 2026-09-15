package com.gokul.autoplay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Secure client-side bridge to the server-side ChatGPT brain. No OpenAI key is stored in Android. */
object TommyChatGPTBridge {
    private const val ENDPOINT = "https://auto-play-gokulmaniraj2008-collabs-projects.vercel.app/api/tommy-chatgpt"
    private const val TIMEOUT_MS = 12_000

    data class Result(
        val reply: String,
        val action: String,
        val target: String,
        val query: String,
        val executionCommand: String,
        val model: String?
    )

    suspend fun sendCommand(command: String): kotlin.Result<Result> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
            }
            val payload = JSONObject().apply { put("text", command) }.toString()
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            if (status !in 200..299) {
                val details = runCatching { JSONObject(body).optString("error") }.getOrNull().orEmpty()
                throw IllegalStateException(details.ifBlank { "ChatGPT bridge HTTP $status" })
            }
            val json = JSONObject(body)
            Result(
                reply = json.optString("reply", "Tommy understood your command."),
                action = json.optString("action", "none"),
                target = json.optString("target"),
                query = json.optString("query"),
                executionCommand = json.optString("executionCommand"),
                model = json.optString("model").takeIf { it.isNotBlank() },
            )
        }
    }
}
