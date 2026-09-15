package com.gokul.autoplay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.gokul.autoplay.skills.TommySkillEngine
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private lateinit var webView: WebView
    private var pendingWebPermissionRequest: PermissionRequest? = null
    private var supabaseBridge: SupabaseTommyBridge? = null
    private var pageRetryUsed = false
    private var browserFallbackUsed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        TommySkillEngine.initialize()

        webView = WebView(this).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mediaPlaybackRequiresUserGesture = false
                allowFileAccess = false
                allowContentAccess = false
                userAgentString = "Mozilla/5.0 (Linux; Android 16; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"
            }

            clearCache(true)
            clearHistory()

            webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    super.onReceivedError(view, request, error)
                    if (!request.isForMainFrame) return
                    if (!pageRetryUsed) {
                        pageRetryUsed = true
                        view.postDelayed({ view.loadUrl(TOMMY_WEB_URL) }, 700L)
                    } else if (!browserFallbackUsed) {
                        browserFallbackUsed = true
                        openTommyInBrowser()
                    }
                }

                override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: android.webkit.WebResourceResponse) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    if (request.isForMainFrame && errorResponse.statusCode == 404 && !browserFallbackUsed) {
                        browserFallbackUsed = true
                        openTommyInBrowser()
                    }
                }

                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: android.net.http.SslError) {
                    handler.cancel()
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest) {
                    runOnUiThread {
                        val needsAudio = request.resources.any { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }
                        if (!needsAudio) {
                            request.deny()
                            return@runOnUiThread
                        }
                        if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
                        } else {
                            pendingWebPermissionRequest = request
                            ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.RECORD_AUDIO), MIC_PERMISSION_REQUEST)
                        }
                    }
                }
            }
            loadUrl(TOMMY_WEB_URL)
        }

        startTommyWebBridge()
        setContentView(webView)
    }

    private fun openTommyInBrowser() {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TOMMY_WEB_URL))) }
    }

    private fun startTommyWebBridge() {
        supabaseBridge = SupabaseTommyBridge(this) { command -> executeWebCommand(command) }
        supabaseBridge?.start()
    }

    private fun executeWebCommand(command: JSONObject): String {
        val raw = command.optString("command").trim()
        if (raw.isBlank()) return "FAILED: Empty command from Tommy web"

        val ai = runCatching { JSONObject(raw) }.getOrNull()
        val action = ai?.optString("action").orEmpty()
        val target = ai?.optString("target").orEmpty()
        val query = ai?.optString("query").orEmpty()
        val original = ai?.optString("original").orEmpty()

        val skillCommand = when (action) {
            "open_app" -> if (target.isNotBlank()) "open $target" else original
            "search_web" -> if (query.isNotBlank()) "search Google for $query" else original
            "youtube_search" -> if (query.isNotBlank()) "search YouTube for $query" else "open YouTube"
            "open_instagram_reels" -> "open Instagram Reels"
            "open_instagram_comments" -> "open Instagram and open comments"
            "spotify_search" -> if (query.isNotBlank()) "search Spotify for $query" else original
            "none" -> original
            else -> original
        }.trim()

        if (skillCommand.isBlank()) return "FAILED: Tommy could not understand the web command"

        val result = TommySkillEngine.execute(this, skillCommand)
        return if (result.success) "OK: ${result.message}" else "FAILED: ${result.message}"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == MIC_PERMISSION_REQUEST) {
            val request = pendingWebPermissionRequest
            pendingWebPermissionRequest = null
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && request != null) request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) else request?.deny()
        }
    }

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        supabaseBridge?.stop()
        supabaseBridge = null
        if (::webView.isInitialized) {
            webView.stopLoading()
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val MIC_PERMISSION_REQUEST = 501
        private const val TOMMY_WEB_URL = "https://auto-play.vercel.app"
    }
}
