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
import com.gokul.autoplay.skills.TommySkillEngine
import org.json.JSONObject
import java.util.Locale
import kotlin.math.abs

class FloatingTommyService : Service(), TommyVoiceController.Listener {
    private var windowManager: WindowManager? = null
    private var bubble: TextView? = null
    private var textToSpeech: TextToSpeech? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var stopping = false
    private var commandMode = false
    private var supabaseBridge: SupabaseTommyBridge? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        TommySkillEngine.initialize()
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

    /** All local voice commands now use the same skill engine as Chat. */
    private fun executeVoiceCommand(command: String) {
        executeSkillCommand(command)
    }

    /**
     * Web commands arrive through Supabase as JSON produced by the Gemini web route.
     * Convert that structured payload back into the canonical natural-language command
     * and send it through the exact same skill engine used by Android Chat/voice.
     */
    private fun executeRemoteCommand(commandRow: JSONObject): String {
        val raw = commandRow.optString("command")
        val ai = runCatching { JSONObject(raw) }.getOrNull()
        val canonical = ai?.optString("original")?.trim()?.takeIf { it.isNotBlank() }
            ?: canonicalCommandFromAi(ai)
            ?: raw.trim()

        if (canonical.isBlank()) {
            return "I received the command, but there was no executable request."
        }
        return executeSkillCommand(canonical)
    }

    private fun canonicalCommandFromAi(ai: JSONObject?): String? {
        if (ai == null) return null
        val action = ai.optString("action").trim().lowercase()
        val query = ai.optString("query").trim()
        val target = ai.optString("target").trim()
        return when (action) {
            "open_instagram_reels" -> "Open Instagram Reels"
            "open_instagram_comments" -> "Open Instagram comments"
            "spotify_search" -> if (query.isNotBlank()) "Search Spotify for $query" else "Open Spotify"
            "search_web" -> if (query.isNotBlank()) "Search Google for $query" else "Open Google"
            "open_app" -> if (target.isNotBlank()) "Open $target" else null
            else -> null
        }
    }

    private fun executeSkillCommand(command: String): String {
        sendStatus(TommyStatusEvents.WORKING, "Tommy is executing: $command")
        val result = TommySkillEngine.execute(applicationContext, command)
        sendStatus(TommyStatusEvents.MESSAGE, result.message)
        mainHandler.post {
            Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
            textToSpeech?.speak(result.message, TextToSpeech.QUEUE_FLUSH, null, "tommy")
        }
        return result.message
    }

    private fun openApp(appName: String, packageName: String, fallbackUrl: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            toast("OK, opening $appName")
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
            return
        }
        try {
            toast("OK, opening $appName in browser")
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        } catch (_: Exception) {
            throw IllegalStateException("$appName is not available")
        }
    }

    private fun openWhatsApp() {
        val whatsappIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            setPackage("com.whatsapp")
            putExtra(Intent.EXTRA_TEXT, "")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            toast("OK, opening WhatsApp")
            startActivity(whatsappIntent)
        } catch (_: Exception) {
            val launchIntent = packageManager.getLaunchIntentForPackage("com.whatsapp")
            if (launchIntent != null) {
                toast("OK, opening WhatsApp")
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
            } else {
                throw IllegalStateException("WhatsApp is not installed")
            }
        }
    }

    private fun toast(message: String) {
        mainHandler.post {
            sendStatus(TommyStatusEvents.MESSAGE, message)
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "tommy")
        }
    }

    private fun scheduleListeningRestart() {
        mainHandler.postDelayed({ if (!stopping && !TommyVoiceController.isListening()) startHeyTommyListening() }, 600L)
    }

    private fun sendStatus(status: String, text: String) {
        sendBroadcast(Intent(TommyStatusEvents.ACTION).apply {
            setPackage(packageName)
            putExtra(TommyStatusEvents.EXTRA_STATUS, status)
            putExtra(TommyStatusEvents.EXTRA_TEXT, text)
        })
    }

    private fun String.normalizeVoiceText(): String =
        lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(NotificationChannelId, "Hey Tommy", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Keeps the floating Tommy assistant and voice listener active"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(this, NotificationChannelId)
            .setContentTitle("Hey Tommy is listening")
            .setContentText("Say Hey Tommy to activate the assistant")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    } else {
        @Suppress("DEPRECATION")
        Notification.Builder(this)
            .setContentTitle("Hey Tommy is listening")
            .setContentText("Say Hey Tommy to activate the assistant")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    companion object {
        @Volatile var isRunning: Boolean = false
            private set
        private const val NotificationChannelId = "tommy_floating"
        private const val NOTIFICATION_ID = 1001
    }
}
