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
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlin.math.abs

class FloatingTommyService : Service() {
    private var windowManager: WindowManager? = null
    private var bubble: TextView? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var listening = false
    private var stopping = false
    private var flashlightOn = false
    private var directCommandMode = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech?.language = Locale.ENGLISH
            }
        }

        if (!Settings.canDrawOverlays(this) || !hasMicrophonePermission()) {
            stopSelf()
            return
        }

        showBubble()
        startHeyTommyListening()
    }

    private fun hasMicrophonePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun showBubble() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val bubbleBackground = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.rgb(35, 105, 255))
        }

        val view = TextView(this).apply {
            text = "T"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = bubbleBackground
            elevation = 12f
        }

        val size = (56 * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = resources.displayMetrics.widthPixels - size - (16 * resources.displayMetrics.density).toInt()
            y = resources.displayMetrics.heightPixels / 2 - size / 2
        }

        var downRawX = 0f
        var downRawY = 0f
        var startX = 0
        var startY = 0
        var moved = false
        val touchSlop = (8 * resources.displayMetrics.density).toInt()

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = event.rawX
                    downRawY = event.rawY
                    startX = params.x
                    startY = params.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - downRawX).toInt()
                    val dy = (event.rawY - downRawY).toInt()
                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) moved = true
                    params.x = (startX + dx).coerceIn(0, resources.displayMetrics.widthPixels - size)
                    params.y = (startY + dy).coerceIn(0, resources.displayMetrics.heightPixels - size)
                    windowManager?.updateViewLayout(view, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        startDirectCommandListening()
                    }
                    true
                }
                else -> true
            }
        }

        bubble = view
        windowManager?.addView(view, params)
    }

    private fun startHeyTommyListening() {
        if (listening || !SpeechRecognizer.isRecognitionAvailable(this)) return

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) {
                    listening = true
                    bubble?.text = if (directCommandMode) "…" else "T"
                }

                override fun onBeginningOfSpeech() {
                    bubble?.text = "…"
                }

                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit

                override fun onError(error: Int) {
                    listening = false
                    if (directCommandMode) {
                        directCommandMode = false
                        bubble?.text = "T"
                    }
                    scheduleListeningRestart()
                }

                override fun onResults(results: android.os.Bundle?) {
                    listening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        .orEmpty()
                    val heardText = matches.firstOrNull()?.normalizeVoiceText().orEmpty()
                    val heardHeyTommy = matches.any { it.normalizeVoiceText().contains("hey tommy") }

                    if (directCommandMode) {
                        directCommandMode = false
                        bubble?.text = "✓"
                        if (heardText.isNotBlank()) {
                            executeVoiceCommand(heardText.removeWakePhrase())
                        } else {
                            toast("I didn't hear a command")
                        }
                        mainHandler.postDelayed({ bubble?.text = "T" }, 1200)
                    } else if (heardHeyTommy) {
                        bubble?.text = "✓"
                        val command = heardText.substringAfter("hey tommy", "").trim()
                        if (command.isNotBlank()) {
                            executeVoiceCommand(command)
                        } else {
                            toast("Tommy is ready")
                        }
                        mainHandler.postDelayed({ bubble?.text = "T" }, 1200)
                    }

                    scheduleListeningRestart()
                }

                override fun onPartialResults(partialResults: android.os.Bundle?) {
                    val matches = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        .orEmpty()
                    if (directCommandMode || matches.any { it.normalizeVoiceText().contains("hey tommy") }) {
                        bubble?.text = "✓"
                    }
                }

                override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
            })
        }

        listenNow()
    }

    private fun startDirectCommandListening() {
        if (stopping || !hasMicrophonePermission() || speechRecognizer == null) return

        directCommandMode = true
        bubble?.text = "…"
        listening = false
        try {
            speechRecognizer?.cancel()
        } catch (_: Exception) {
            // Ignore cancellation errors; a fresh recognition session starts below.
        }
        mainHandler.postDelayed({
            if (!stopping && directCommandMode) {
                listenNow()
            }
        }, 120L)
    }

    private fun executeVoiceCommand(command: String) {
        val cleanCommand = command.normalizeVoiceText()
        when {
            cleanCommand.contains("light") && (cleanCommand.contains("on") || cleanCommand.contains("turn")) -> {
                setFlashlight(true)
            }
            cleanCommand.contains("light") && cleanCommand.contains("off") -> {
                setFlashlight(false)
            }
            cleanCommand.contains("instagram") && cleanCommand.contains("close") -> {
                openRecentApps("Instagram")
            }
            cleanCommand.contains("youtube") && cleanCommand.contains("close") -> {
                openRecentApps("YouTube")
            }
            cleanCommand.contains("whatsapp") && cleanCommand.contains("close") -> {
                openRecentApps("WhatsApp")
            }
            cleanCommand.contains("google") && cleanCommand.contains("close") -> {
                openRecentApps("Google")
            }
            cleanCommand.contains("instagram") -> {
                openApp("Instagram", "com.instagram.android", "https://www.instagram.com")
            }
            cleanCommand.contains("youtube") -> {
                openApp("YouTube", "com.google.android.youtube", "https://www.youtube.com")
            }
            cleanCommand.contains("google") -> {
                openApp("Google", "com.google.android.googlequicksearchbox", "https://www.google.com")
            }
            cleanCommand.contains("whatsapp") -> {
                openWhatsApp()
            }
            else -> toast("Tommy heard: $cleanCommand")
        }
    }

    private fun openRecentApps(appName: String) {
        try {
            startActivity(Intent("android.intent.action.RECENT_APPS").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            toast("Recent Apps opened — swipe $appName away to close it")
        } catch (_: Exception) {
            toast("Android did not allow Recent Apps to open")
        }
    }

    private fun setFlashlight(enabled: Boolean) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            toast("Camera permission needed for the flashlight. Open Commands → Light once to allow it.")
            return
        }

        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id)
                .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }

        if (cameraId == null) {
            toast("This phone has no available flashlight")
            return
        }

        try {
            cameraManager.setTorchMode(cameraId, enabled)
            flashlightOn = enabled
            toast(if (enabled) "Flashlight ON" else "Flashlight OFF")
        } catch (_: Exception) {
            toast("Unable to control flashlight")
        }
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
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (_: Exception) {
            toast("$appName is not available")
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
                toast("WhatsApp is not installed")
            }
        }
    }

    private fun toast(message: String) {
        mainHandler.post {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            textToSpeech?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "tommy")
        }
    }

    private fun listenNow() {
        if (stopping || !hasMicrophonePermission() || speechRecognizer == null) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (_: Exception) {
            scheduleListeningRestart()
        }
    }

    private fun scheduleListeningRestart() {
        mainHandler.postDelayed({
            if (!stopping && !listening && speechRecognizer != null) {
                listenNow()
            }
        }, 600L)
    }

    private fun String.removeWakePhrase(): String =
        normalizeVoiceText().removePrefix("hey tommy").trim()

    private fun String.normalizeVoiceText(): String =
        lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    override fun onDestroy() {
        stopping = true
        directCommandMode = false
        mainHandler.removeCallbacksAndMessages(null)
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        listening = false
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        bubble?.let { view ->
            if (view.isAttachedToWindow) windowManager?.removeView(view)
        }
        bubble = null
        windowManager = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hey Tommy",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the floating Tommy assistant and voice listener active"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
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
    }

    companion object {
        private const val CHANNEL_ID = "tommy_floating"
        private const val NOTIFICATION_ID = 1001
    }
}
