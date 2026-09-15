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
                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: WebResourceError
                ) {
                    super.onReceivedError(view, request, error)
                    if (!request.isForMainFrame) return

                    if (!pageRetryUsed) {
                        pageRetryUsed = true
                        view.postDelayed({
                            view.loadUrl(TOMMY_WEB_URL)
                        }, 700L)
                    } else if (!browserFallbackUsed) {
                        browserFallbackUsed = true
                        openTommyInBrowser()
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView,
                    request: WebResourceRequest,
                    errorResponse: android.webkit.WebResourceResponse
                ) {
                    super.onReceivedHttpError(view, request, errorResponse)
                    if (request.isForMainFrame && errorResponse.statusCode == 404 && !browserFallbackUsed) {
                        browserFallbackUsed = true
                        openTommyInBrowser()
                    }
                }

                override fun onReceivedSslError(
                    view: WebView,
                    handler: SslErrorHandler,
                    error: android.net.http.SslError
                ) {
                    handler.cancel()
                }
            }

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

    private fun openTommyInBrowser() {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(TOMMY_WEB_URL)))
        }
    }

    private fun startTommyWebBridge() {
        supabaseBridge = SupabaseTommyBridge(this) { command ->
            executeWebCommand(command)
        }
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

        // Show the same ChatGPT-style YouTube card for BOTH:
        // 1) "Open YouTube"
        // 2) "Open YouTube and search ..."
        if (result.success && (
                action == "youtube_search" ||
                (action == "open_app" && target.equals("YouTube", ignoreCase = true))
            )) {
            showYouTubeCard(query)
        }

        return if (result.success) {
            "OK: ${result.message}"
        } else {
            "FAILED: ${result.message}"
        }
    }

    private fun showYouTubeCard(query: String) {
        val safeQuery = JSONObject.quote(query)
        runOnUiThread {
            webView.postDelayed({
                val script = """
                    (function() {
                      const query = $safeQuery;
                      const chat = document.querySelector('.chat');
                      if (!chat) return;

                      const old = document.getElementById('tommy-youtube-card');
                      if (old) old.remove();

                      if (!document.getElementById('tommy-youtube-card-style')) {
                        const style = document.createElement('style');
                        style.id = 'tommy-youtube-card-style';
                        style.textContent = `
                          .tommyYoutubeCard { margin: 14px 0; border: 1px solid #27272a; border-radius: 18px; overflow: hidden; background: #0b0b0c; box-shadow: 0 8px 28px rgba(0,0,0,.18); }
                          .tommyYoutubeCardTop { padding: 14px 16px; display:flex; align-items:center; gap:10px; color:#fff; font-weight:700; }
                          .tommyYoutubeDot { width:28px; height:28px; border-radius:9px; display:grid; place-items:center; background:#ff0000; color:#fff; font-size:14px; }
                          .tommyYoutubeSearch { margin:0 14px 14px; padding:12px 14px; border-radius:12px; background:#171719; color:#f4f4f5; font-size:14px; }
                          .tommyYoutubeStatus { padding:0 16px 16px; color:#a1a1aa; font-size:13px; }
                          .tommyYoutubeOpen { margin:0 14px 14px; width:calc(100% - 28px); border:0; border-radius:12px; padding:11px 14px; background:#fff; color:#111; font-weight:700; cursor:pointer; }
                        `;
                        document.head.appendChild(style);
                      }

                      const hasQuery = query.trim().length > 0;
                      const safeText = query.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
                      const card = document.createElement('div');
                      card.id = 'tommy-youtube-card';
                      card.className = 'tommyYoutubeCard';
                      card.innerHTML = `
                        <div class="tommyYoutubeCardTop"><span class="tommyYoutubeDot">▶</span><span>YouTube</span></div>
                        ${hasQuery ? `<div class="tommyYoutubeSearch">🔍 ${safeText}</div>` : `<div class="tommyYoutubeSearch">▶ YouTube is ready</div>`}
                        <div class="tommyYoutubeStatus">${hasQuery ? 'YouTube search results are open.' : 'YouTube is open.'}</div>
                        <button class="tommyYoutubeOpen" type="button">${hasQuery ? 'Open YouTube results' : 'Open YouTube'}</button>
                      `;
                      chat.appendChild(card);
                      card.querySelector('button')?.addEventListener('click', function() {
                        window.location.href = hasQuery
                          ? 'https://www.youtube.com/results?search_query=' + encodeURIComponent(query)
                          : 'https://www.youtube.com';
                      });
                      card.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                    })();
                """.trimIndent()
                webView.evaluateJavascript(script, null)
            }, 250L)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
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
