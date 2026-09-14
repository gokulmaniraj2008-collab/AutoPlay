package com.gokul.autoplay

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Accessibility-backed phone automation UI and command bubble. */
class PhoneAutomationService : AccessibilityService() {
    private var windowManager: WindowManager? = null
    private var bubble: View? = null
    private var panel: View? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    fun showCommandBubble() {
        if (bubble != null) return
        val wm = windowManager ?: return
        val view = TextView(this).apply {
            text = "✦"
            textSize = 24f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(40, 120, 255))
            contentDescription = "AutoPlay chatbot. Tap to enter a command."
            setOnClickListener { expandBubble() }
        }
        val params = WindowManager.LayoutParams(
            dp(58), dp(58),
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(16)
            y = dp(180)
        }
        wm.addView(view, params)
        bubble = view
    }

    private fun expandBubble() {
        if (panel != null) return
        val wm = windowManager ?: return
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(Color.WHITE)
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
        root.addView(header)

        val input = EditText(this).apply {
            hint = "Try: search Tamil songs"
            maxLines = 1
            setTextColor(Color.BLACK)
            setHintTextColor(Color.GRAY)
        }
        root.addView(input, LinearLayout.LayoutParams(-1, dp(52)))

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
                    TaskStore.upsert(this@PhoneAutomationService, task)
                    TaskScheduler.schedule(this@PhoneAutomationService, task)
                    Toast.makeText(this@PhoneAutomationService, parsed.message, Toast.LENGTH_SHORT).show()
                } else {
                    val result = TaskExecutor.execute(this@PhoneAutomationService, task)
                    TaskHistoryStore.record(this@PhoneAutomationService, task, result)
                    Toast.makeText(this@PhoneAutomationService, result.message, Toast.LENGTH_SHORT).show()
                    if (result.success) collapseBubble()
                }
            }
        }
        root.addView(run, LinearLayout.LayoutParams(-1, dp(52)))

        val params = WindowManager.LayoutParams(
            dp(320), WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(140)
        }
        wm.addView(root, params)
        panel = root
    }

    private fun collapseBubble() {
        panel?.let { runCatching { windowManager?.removeView(it) } }
        panel = null
    }

    override fun onDestroy() {
        collapseBubble()
        bubble?.let { runCatching { windowManager?.removeView(it) } }
        bubble = null
        super.onDestroy()
    }
}
