package com.gokul.autoplay

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private var pendingWebPermissionRequest: PermissionRequest? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = false
                allowContentAccess = false
            }

            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    runOnUiThread {
                        val needsAudio = request.resources.any {
                            it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                        }
                        if (!needsAudio) {
                            request.deny()
                            return@runOnUiThread
                        }

                        if (ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                        } else {
                            pendingWebPermissionRequest = request
                            ActivityCompat.requestPermissions(
                                this@MainActivity,
                                arrayOf(Manifest.permission.RECORD_AUDIO),
                                MIC_PERMISSION_REQUEST
                            )
                        }
                    }
                }
            }

            loadUrl(TOMMY_WEB_URL)
        }

        startTommyWebBridge()
        setContentView(webView)
    }

    private fun startTommyWebBridge() {
        val bridge = SupabaseTommyBridge(this) { command ->
            val result = executeWebCommand(command)
            result
        }
        bridge.start()
    }

    private fun executeWebCommand(command: org.json.JSONObject): String {
        val raw = command.optString("command").trim()
        if (raw.isBlank()) return "FAILED: Empty command from Tommy web"

        val ai = runCatching { org.json.JSONObject(raw) }.getOrNull()
        val action = ai?.optString("action").orEmpty()
        val target = ai?.optString("target").orEmpty()
        val query = ai?.optString("query").orEmpty()
        val original = ai?.optString("original").orEmpty()

        val skillCommand = when (action) {
            "open_app" -> if (target.isNotBlank()) "open $target" else original
            "search_web" -> if (query.isNotBlank()) "search Google for $query" else original
            "open_instagram_reels" -> "open Instagram Reels"
            "open_instagram_comments" -> "open Instagram and open comments"
            "spotify_search" -> if (query.isNotBlank()) "search Spotify for $query" else original
            "none" -> original
            else -> original
        }.trim()

        if (skillCommand.isBlank()) return "FAILED: Tommy could not understand the web command"

        val result = com.gokul.autoplay.skills.TommySkillEngine.execute(this, skillCommand)
        return if (result.success) {
            "OK: ${result.message}"
        } else {
            "FAILED: ${result.message}"
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MIC_PERMISSION_REQUEST) {
            val request = pendingWebPermissionRequest
            pendingWebPermissionRequest = null
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && request != null) {
                request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
            } else {
                request?.deny()
            }
        }
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val MIC_PERMISSION_REQUEST = 501
        private const val TOMMY_WEB_URL = "https://auto-play-4qkv.vercel.app"
    }
}
