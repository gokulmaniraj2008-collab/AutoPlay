package com.gokul.autoplay

import android.graphics.Bitmap
import android.util.Base64
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Tommy's visual reasoning bridge.
 * Screenshot -> Gemini vision -> strict target/normalized coordinates JSON.
 * The API key is supplied at build time through GEMINI_API_KEY and is never stored in source.
 */
object TommyVisionEngine {
    private const val MODEL = "gemini-2.5-flash-lite"
    private const val ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"
    private const val MAX_IMAGE_BYTES = 4 * 1024 * 1024
    private val executor = Executors.newSingleThreadExecutor()

    data class Target(
        val found: Boolean,
        val label: String,
        val xRatio: Float,
        val yRatio: Float,
        val confidence: Float,
        val reason: String
    )

    fun locate(bitmap: Bitmap, instruction: String, callback: (Target?, String?) -> Unit) {
        executor.execute {
            try {
                val key = BuildConfig.GEMINI_API_KEY.trim()
                if (key.isBlank()) {
                    callback(null, "GEMINI_API_KEY is not configured")
                    return@execute
                }

                val jpeg = compress(bitmap)
                if (jpeg == null) {
                    callback(null, "Could not encode screenshot")
                    return@execute
                }

                val prompt = """
                    You are Tommy Vision, an Android screen-control vision model.
                    Inspect the supplied phone screenshot and locate the UI target requested by the user.
                    Return ONLY valid JSON with this exact schema:
                    {"found":true,"label":"Like","x":0.90,"y":0.62,"confidence":0.96,"reason":"..."}
                    x and y MUST be normalized 0.0..1.0 relative to the full screenshot.
                    If the target is not visible, return found=false and x=0,y=0.
                    Do not guess a target that is not visible.
                    User request: $instruction
                """.trimIndent()

                val imageData = Base64.encodeToString(jpeg, Base64.NO_WRAP)
                val body = JSONObject().apply {
                    put("contents", org.json.JSONArray().put(
                        JSONObject().put("parts", org.json.JSONArray()
                            .put(JSONObject().put("text", prompt))
                            .put(JSONObject().put("inlineData", JSONObject()
                                .put("mimeType", "image/jpeg")
                                .put("data", imageData)
                            ))
                        )
                    ))
                }.toString()

                val connection = (URL("$ENDPOINT?key=$key").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                }

                try {
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val status = connection.responseCode
                    val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                    val response = BufferedReader(InputStreamReader(stream)).use { it.readText() }
                    if (status !in 200..299) {
                        callback(null, "Gemini HTTP $status")
                        return@execute
                    }
                    callback(parseTarget(response), null)
                } finally {
                    connection.disconnect()
                }
            } catch (t: Throwable) {
                callback(null, t.message ?: "Vision request failed")
            }
        }
    }

    private fun compress(bitmap: Bitmap): ByteArray? {
        var quality = 82
        while (quality >= 45) {
            val output = java.io.ByteArrayOutputStream()
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) return null
            val bytes = output.toByteArray()
            if (bytes.size <= MAX_IMAGE_BYTES) return bytes
            quality -= 10
        }
        return null
    }

    private fun parseTarget(response: String): Target? {
        return try {
            val root = JSONObject(response)
            val text = root.getJSONArray("candidates")
                .getJSONObject(0)
                .getJSONObject("content")
                .getJSONArray("parts")
                .getJSONObject(0)
                .getString("text")
                .trim()
            val json = JSONObject(extractJson(text))
            Target(
                found = json.optBoolean("found", false),
                label = json.optString("label", ""),
                xRatio = json.optDouble("x", 0.0).toFloat().coerceIn(0f, 1f),
                yRatio = json.optDouble("y", 0.0).toFloat().coerceIn(0f, 1f),
                confidence = json.optDouble("confidence", 0.0).toFloat().coerceIn(0f, 1f),
                reason = json.optString("reason", "")
            )
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractJson(text: String): String {
        val cleaned = text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val start = cleaned.indexOf('{')
        val end = cleaned.lastIndexOf('}')
        return if (start >= 0 && end > start) cleaned.substring(start, end + 1) else cleaned
    }
}
