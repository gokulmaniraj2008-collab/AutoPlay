package com.gokul.autoplay

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.setPadding

/**
 * User-enabled accessibility bridge for AutoPlay phone workflows.
 * It also provides a small chatbot-style command bubble while another app is in the foreground.
 */
class PhoneAutomationService : AccessibilityService() {
    companion object {
        @Volatile private var instance: PhoneAutomationService? = null
        @Volatile private var pendingTask: String? = null

        fun beginWhatsAppMessage(person: String, message: String) {
            pendingTask = "WHATSAPP|$person|$message"
        }

        fun beginInstagramBio(bio: String) {
            pendingTask = "INSTAGRAM_BIO|$bio"
        }

        fun clearPendingTask() {
            pendingTask = null
        }

        fun showCommandBubble() {
            instance?.showBubble()
        }

        fun hideCommandBubble() {
            instance?.hideBubble()
        }
    }

    private var windowManager: WindowManager? = null
    private var bubble: TextView? = null
    private var panel: LinearLayout? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() {
        clearPendingTask()
    }

    override fun onDestroy() {
        hideBubble()
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun overlayParams(width: Int, height: Int): WindowManager.LayoutParams = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        android.graphics.PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.BOTTOM or Gravity.END
        x = 18
        y = 110
    }

    private fun showBubble() {
        if (bubble != null || panel != null) return
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        bubble = TextView(this).apply {
            text = "✦"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.rgb(25, 99, 220))
            }
            setOnClickListener { expandBubble() }
        }
        runCatching { windowManager?.addView(bubble, overlayParams(dp(58), dp(58))) }
            .onFailure { bubble = null }
    }

    private fun expandBubble() {
        bubble?.let { runCatching { windowManager?.removeView(it) } }
        bubble = null

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14))
            background = GradientDrawable().apply {
                cornerRadius = dp(22).toFloat()
                setColor(Color.WHITE)
                setStroke(dp(1), Color.rgb(225, 229, 236))
            }
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "AutoPlay"
            textSize = 18f
            setTextColor(Color.rgb(20, 25, 35))
        }
        val close = TextView(this).apply {
            text = "×"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.DKGRAY)
            setOnClickListener { collapseBubble() }
        }
        header.addView(title, LinearLayout.LayoutParams(0, dp(42), 1f))
        header.addView(close, LinearLayout.LayoutParams(dp(42), dp(42)))

        val input = EditText(this).apply {
            hint = "Try: search Tamil songs"
            singleLine = true
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
        }
        val run = Button(this).apply {
            text = "Run command"
            setOnClickListener {
                val command = input.text?.toString().orEmpty().trim()
                if (command.isBlank()) return@setOnClickListener
                val parsed = TaskParser.parse(command)
                val task = parsed.task
                if (task == null) {
                    Toast.makeText(this@PhoneAutomationService, parsed.message, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (task.scheduledAtMillis != null) {
                    Toast.makeText(this@PhoneAutomationService, "Scheduled commands are available from the main app.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val result = TaskExecutor.execute(this@PhoneAutomationService, task)
                Toast.makeText(this@PhoneAutomationService, result.message, Toast.LENGTH_SHORT).show()
                if (result.success && task.action == PhoneTask.Action.YOUTUBE_SEARCH) collapseBubble()
            }
        }

        root.addView(header)
        root.addView(input, LinearLayout.LayoutParams(-1, dp(52)))
        root.addView(run, LinearLayout.LayoutParams(-1, dp(50)))
        panel = root
        runCatching { windowManager?.addView(panel, overlayParams(dp(300), dp(160))) }
            .onFailure { panel = null; showBubble() }
        input.requestFocus()
    }

    private fun collapseBubble() {
        panel?.let { runCatching { windowManager?.removeView(it) } }
        panel = null
        showBubble()
    }

    private fun hideBubble() {
        bubble?.let { runCatching { windowManager?.removeView(it) } }
        panel?.let { runCatching { windowManager?.removeView(it) } }
        bubble = null
        panel = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
