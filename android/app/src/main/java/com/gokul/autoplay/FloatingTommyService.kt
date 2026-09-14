package com.gokul.autoplay

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs

class FloatingTommyService : Service(), TommyVoiceController.Listener {
    private var windowManager: WindowManager? = null
    private var bubble: TextView? = null
    private var textToSpeech: TextToSpeech? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var stopping = false
    private var commandMode = false
    private var flashlightOn = false
    private var supabaseBridge: SupabaseTommyBridge? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) textToSpeech?.language = Locale.ENGLISH
        }
        if (!Settings.canDrawOverlays(this) || !hasMicrophonePermission()) {
            sendStatus(TommyStatusEvents.OFF, "Tommy is OFF")
            stopSelf()
            return
        }
        TommyVoiceController.addListener(this)
        showBubble()
        supabaseBridge = SupabaseTommyBridge(applicationContext) { remote ->
            executeRemoteCommand(remote)
        }.also { it.start() }
        sendStatus(TommyStatusEvents.ON, "Tommy is ON")
        startHeyTommyListening()
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun showBubble() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val bubbleBackground = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.rgb(35, 105, 255)) }
        val view = TextView(this).apply {
            text = "T"; textSize = 18f; setTextColor(Color.WHITE); gravity = Gravity.CENTER; background = bubbleBackground; elevation = 12f
        }
        val size = (56 * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(size, size, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size - (16 * resources.displayMetrics.density).toInt()
            y = resources.displayMetrics.heightPixels / 2 - size / 2
        }
        var downRawX = 0f; var downRawY = 0f; var startX = 0; var startY = 0; var moved = false
        val touchSlop = (8 * resources.displayMetrics.density).toInt()
        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downRawX = event.rawX; downRawY = event.rawY; startX = params.x; startY = params.y; moved = false; true }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt(); val dy = (event.rawY - downRawY).toInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) moved = true
                    params.x = (startX + dx).coerceIn(0, resources.displayMetrics.widthPixels - size)
                    params.y = (startY + dy).coerceIn(0, resources.displayMetrics.heightPixels - size)
                    windowManager?.updateViewLayout(view, params); true
                }
                MotionEvent.ACTION_UP -> { if (!moved) enterDirectCommandMode(); true }
                else -> true
            }
        }
        bubble = view
        windowManager?.addView(view, params)
    }

    private fun enterDirectCommandMode() {
        commandMode = true
        bubble?.text = "…"
        sendStatus(TommyStatusEvents.LISTENING, "Tommy is listening…")
        textToSpeech?.speak("I'm listening", TextToSpeech.QUEUE_FLUSH, null, "tommy-listening")
        TommyVoiceController.cancel()
        mainHandler.postDelayed({ if (!stopping) TommyVoiceController.start(this, TommyVoiceController.Source.BUBBLE) }, 120L)
    }

    private fun startHeyTommyListening() {
        if (stopping || !hasMicrophonePermission()) return
        TommyVoiceController.start(this, TommyVoiceController.Source.WAKE_WORD)
    }

    override fun onStateChanged(state: TommyVoiceController.State, message: String) {
        if (stopping) return
        when (state) {
            TommyVoiceController.State.LISTENING -> if (commandMode) { bubble?.text = "…"; sendStatus(TommyStatusEvents.LISTENING, message) }
            TommyVoiceController.State.PROCESSING -> if (commandMode) sendStatus(TommyStatusEvents.WORKING, message)
            TommyVoiceController.State.ERROR -> {
                if (commandMode) { commandMode = false; bubble?.text = "T"; sendStatus(TommyStatusEvents.ON, "Tommy is ON") }
                scheduleListeningRestart()
            }
            TommyVoiceController.State.IDLE -> Unit
        }
    }

    override fun onPartialText(text: String) {
        if (stopping) return
        val normalized = text.normalizeVoiceText()
        if (commandMode) sendStatus(TommyStatusEvents.LISTENING, "Tommy heard: $normalized")
        else if (normalized.contains("hey tommy")) bubble?.text = "✓"
    }

    override fun onFinalText(text: String, source: TommyVoiceController.Source) {
        if (stopping) return
        val heardText = text.normalizeVoiceText()
        when (source) {
            TommyVoiceController.Source.BUBBLE, TommyVoiceController.Source.MIC -> {
                commandMode = false; bubble?.text = "✓"
                if (heardText.isNotBlank()) { sendStatus(TommyStatusEvents.HEARD, "Tommy heard: $heardText"); executeVoiceCommand(heardText) }
                mainHandler.postDelayed({ bubble?.text = "T" }, 1200L)
                scheduleListeningRestart()
            }
            TommyVoiceController.Source.WAKE_WORD -> {
                val wakeIndex = heardText.indexOf("hey tommy")
                if (wakeIndex >= 0) {
                    bubble?.text = "✓"
                    val command = heardText.removeRange(wakeIndex, wakeIndex + "hey tommy".length).trim()
                    if (command.isNotBlank()) { sendStatus(TommyStatusEvents.HEARD, "Tommy heard: $command"); executeVoiceCommand(command) }
                    else { sendStatus(TommyStatusEvents.ON, "Tommy is ON"); toast("Tommy is ready") }
                    mainHandler.postDelayed({ bubble?.text = "T" }, 1200L)
                }
                scheduleListeningRestart()
            }
        }
    }

    private fun executeVoiceCommand(command: String) {
        sendStatus(TommyStatusEvents.WORKING, "Tommy is working…")
        when {
            command.contains("light") && (command.contains("on") || command.contains("turn")) -> setFlashlight(true)
            command.contains("light") && command.contains("off") -> setFlashlight(false)
            command.contains("instagram") && command.contains("close") -> openRecentApps("Instagram")
            command.contains("youtube") && command.contains("close") -> openRecentApps("YouTube")
            command.contains("whatsapp") && command.contains("close") -> openRecentApps("WhatsApp")
            command.contains("google") && command.contains("close") -> openRecentApps("Google")
            command.contains("instagram") -> openApp("Instagram", "com.instagram.android", "https://www.instagram.com")
            command.contains("youtube") -> openApp("YouTube", "com.google.android.youtube", "https://www.youtube.com")
            command.contains("google") -> openApp("Google", "com.google.android.googlequicksearchbox", "https://www.google.com")
            command.contains("whatsapp") -> openWhatsApp()
            else -> toast("Tommy heard: $command")
        }
    }

    private fun executeRemoteCommand(commandRow: JSONObject): String {
        val raw = commandRow.optString("command")
        val ai = try { JSONObject(raw) } catch (_: Exception) { JSONObject().put("action", "none").put("reply", "I received the command but could not parse the AI action.") }
        val action = ai.optString("action", "none")
        val query = ai.optString("query", "").trim()
        val target = ai.optString("target", "").trim()
        sendStatus(TommyStatusEvents.WORKING, "Tommy is executing: ${ai.optString("reply", raw)}")
        when (action) {
            "open_instagram_reels" -> { openUrl("https://www.instagram.com/reels/"); return "Done — opening Instagram Reels." }
            "open_instagram_comments" -> { openApp("Instagram", "com.instagram.android", "https://www.instagram.com/"); return "Instagram opened. Comments require the visible post UI/accessibility layer." }
            "spotify_search" -> { openUrl("https://open.spotify.com/search/${URLEncoder.encode(query, "UTF-8")}"); return "Done — opening Spotify search for $query." }
            "search_web" -> { openUrl("https://www.google.com/search?q=${URLEncoder.encode(query, "UTF-8")}"); return "Done — searching Google for $query." }
            "open_app" -> {
                val normalized = (target.ifBlank { query }).lowercase()
                return when {
                    normalized.contains("instagram") -> { openApp("Instagram", "com.instagram.android", "https://www.instagram.com"); "Done — opening Instagram." }
                    normalized.contains("youtube") -> { openApp("YouTube", "com.google.android.youtube", "https://www.youtube.com"); "Done — opening YouTube." }
                    normalized.contains("spotify") -> { openApp("Spotify", "com.spotify.music", "https://open.spotify.com"); "Done — opening Spotify." }
                    normalized.contains("whatsapp") -> { openWhatsApp(); "Done — opening WhatsApp." }
                    normalized.contains("google") -> { openApp("Google", "com.google.android.googlequicksearchbox", "https://www.google.com"); "Done — opening Google." }
                    else -> "I understood the request, but I don't have a safe launcher for $target yet."
                }
            }
            else -> return ai.optString("reply", "I understood you, but no Android action was selected.")
        }
    }

    private fun openUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) }
        catch (_: Exception) { throw IllegalStateException("Could not open $url") }
    }

    private fun openRecentApps(appName: String) { try { startActivity(Intent("android.intent.action.RECENT_APPS").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }); toast("Recent Apps opened — swipe $appName away to close it") } catch (_: Exception) { toast("Android did not allow Recent Apps to open") } }

    private fun setFlashlight(enabled: Boolean) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { toast("Camera permission needed for the flashlight. Open Commands → Light once to allow it."); return }
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull { id -> cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
        if (cameraId == null) { toast("This phone has no available flashlight"); return }
        try { cameraManager.setTorchMode(cameraId, enabled); flashlightOn = enabled; toast(if (enabled) "Flashlight ON" else "Flashlight OFF") } catch (_: Exception) { toast("Unable to control flashlight") }
    }

    private fun openApp(appName: String, packageName: String, fallbackUrl: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) { toast("OK, opening $appName"); launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(launchIntent); return }
        try { toast("OK, opening $appName in browser"); startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (_: Exception) { throw IllegalStateException("$appName is not available") }
    }

    private fun openWhatsApp() {
        val whatsappIntent = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; setPackage("com.whatsapp"); putExtra(Intent.EXTRA_TEXT, ""); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        try { toast("OK, opening WhatsApp"); startActivity(whatsappIntent) } catch (_: Exception) {
            val launchIntent = packageManager.getLaunchIntentForPackage("com.whatsapp")
            if (launchIntent != null) { toast("OK, opening WhatsApp"); launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(launchIntent) } else throw IllegalStateException("WhatsApp is not installed")
        }
    }

    private fun toast(message: String) { mainHandler.post { sendStatus(TommyStatusEvents.MESSAGE, message); Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "tommy") } }

    private fun scheduleListeningRestart() { mainHandler.postDelayed({ if (!stopping && !TommyVoiceController.isListening()) startHeyTommyListening() }, 600L) }

    private fun sendStatus(status: String, text: String) { sendBroadcast(Intent(TommyStatusEvents.ACTION).apply { setPackage(packageName); putExtra(TommyStatusEvents.EXTRA_STATUS, status); putExtra(TommyStatusEvents.EXTRA_TEXT, text) }) }

    private fun String.normalizeVoiceText(): String = lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    override fun onDestroy() {
        stopping = true; isRunning = false; mainHandler.removeCallbacksAndMessages(null)
        supabaseBridge?.stop(); supabaseBridge = null
        TommyVoiceController.removeListener(this); TommyVoiceController.cancel()
        sendStatus(TommyStatusEvents.OFF, "Tommy is OFF")
        textToSpeech?.stop(); textToSpeech?.shutdown(); textToSpeech = null
        bubble?.let { view -> if (view.isAttachedToWindow) windowManager?.removeView(view) }
        bubble = null; windowManager = null; super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { val channel = NotificationChannel(NotificationChannelId, "Hey Tommy", NotificationManager.IMPORTANCE_LOW).apply { description = "Keeps the floating Tommy assistant and voice listener active" }; getSystemService(NotificationManager::class.java).createNotificationChannel(channel) } }

    private fun buildNotification(): Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(this, NotificationChannelId).setContentTitle("Hey Tommy is listening").setContentText("Say Hey Tommy to activate the assistant").setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).build()
    } else {
        @Suppress("DEPRECATION")
        Notification.Builder(this).setContentTitle("Hey Tommy is listening").setContentText("Say Hey Tommy to activate the assistant").setSmallIcon(android.R.drawable.ic_btn_speak_now).setOngoing(true).build()
    }

    companion object {
        @Volatile var isRunning: Boolean = false
            private set
        private const val NotificationChannelId = "tommy_floating"
        private const val NOTIFICATION_ID = 1001
    }
}
