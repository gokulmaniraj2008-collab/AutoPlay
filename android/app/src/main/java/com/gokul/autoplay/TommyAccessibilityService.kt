package com.gokul.autoplay

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent

class TommyAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString()?.trim().orEmpty()
        if (packageName.isNotBlank()) {
            foregroundPackage = packageName
            synchronized(foregroundLock) { foregroundLock.notifyAll() }
        }
    }

    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        foregroundPackage = rootInActiveWindow?.packageName?.toString().orEmpty()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        synchronized(foregroundLock) { foregroundLock.notifyAll() }
        super.onDestroy()
    }

    private fun openRecentAppsInternal() { performGlobalAction(GLOBAL_ACTION_RECENTS) }

    /**
     * Captures the current display through Android's Accessibility screenshot API.
     * This is the foundation for Tommy's future visual/vision mode.
     */
    private fun captureScreenInternal(callback: (Bitmap?) -> Unit) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) {
            callback(null)
            return
        }

        val displayId = display?.displayId ?: 0
        takeScreenshot(
            displayId,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    val bitmap = Bitmap.wrapHardwareBuffer(
                        screenshot.hardwareBuffer,
                        screenshot.colorSpace
                    )?.copy(Bitmap.Config.ARGB_8888, false)
                    screenshot.hardwareBuffer.close()
                    callback(bitmap)
                }

                override fun onFailure(errorCode: Int) {
                    callback(null)
                }
            }
        )
    }

    private fun performTommyActionInternal(action: String): Boolean {
        return when {
            action == "OPEN_INSTAGRAM_REELS" ->
                waitForInstagramAndClick("Reels", "reels")

            action == "SCROLL_REEL" ->
                if (isInstagramForeground()) dispatchSwipe(0.50f, 0.78f, 0.50f, 0.25f, 350) else false

            action == "LIKE_REEL" -> {
                if (!isInstagramForeground()) return false
                val clicked = findAndClickAny("Like", "like")
                if (!clicked) dispatchTapNearRightCenter(0.90f, 0.62f) else true
            }

            action == "FOLLOW_ACCOUNT" -> {
                if (!isInstagramForeground()) return false
                val clicked = findAndClickExactOrDescription("Follow", "follow")
                if (!clicked) findAndClickExactOrDescription("Follow back", "follow back") else true
            }

            action == "OPEN_COMMENTS" -> {
                if (!isInstagramForeground()) return false
                val clicked = findAndClickAny("Comment", "comment", "Comments", "comments")
                if (!clicked) dispatchTapNearRightCenter(0.90f, 0.53f) else true
            }

            action.startsWith("FOLLOW_PROFILE:") -> {
                val username = action.removePrefix("FOLLOW_PROFILE:").trim().removePrefix("@").trim()
                if (username.isBlank()) return false
                openInstagramProfileAndFollow(username)
            }

            else -> false
        }
    }

    private fun isInstagramForeground(): Boolean =
        rootInActiveWindow?.packageName?.toString() == INSTAGRAM_PACKAGE

    private fun waitForInstagramAndClick(vararg labels: String): Boolean {
        if (!waitForForegroundPackage(INSTAGRAM_PACKAGE, 2500L)) return false
        return findAndClickAny(*labels)
    }

    private fun openInstagramProfileAndFollow(username: String): Boolean {
        return try {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/$username/")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            if (!waitForForegroundPackage(INSTAGRAM_PACKAGE, 4000L)) return false
            Thread.sleep(500L)
            val clicked = findAndClickExactOrDescription("Follow", "follow")
            if (!clicked) findAndClickExactOrDescription("Follow back", "follow back") else true
        } catch (_: Exception) {
            false
        }
    }

    private fun sendChatGPTMessageInternal(message: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val packageName = root.packageName?.toString().orEmpty()
        if (packageName != "com.openai.chatgpt") return false

        val input = findChatInput(root) ?: return false
        if (!input.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, message)
        }
        val typed = input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!typed) return false

        Thread.sleep(250L)
        return clickSendButton(rootInActiveWindow ?: root)
    }

    private fun findChatInput(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val className = node.className?.toString().orEmpty()
            val text = node.text?.toString().orEmpty()
            val hint = node.hintText?.toString().orEmpty()
            if (node.isEditable ||
                className.contains("EditText", ignoreCase = true) ||
                text.contains("message", ignoreCase = true) ||
                hint.contains("message", ignoreCase = true) ||
                hint.contains("ask", ignoreCase = true)) {
                return node
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return null
    }

    private fun clickSendButton(root: AccessibilityNodeInfo): Boolean {
        val labels = listOf("Send", "send")
        for (label in labels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            for (node in nodes) if (clickNodeOrParent(node)) return true
        }
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val description = node.contentDescription?.toString().orEmpty()
            if (description.contains("send", ignoreCase = true) && clickNodeOrParent(node)) return true
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return false
    }

    private fun findAndClickAny(vararg labels: String): Boolean {
        val root = rootInActiveWindow ?: return false
        for (label in labels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            for (node in nodes) if (clickNodeOrParent(node)) return true
        }
        return false
    }

    private fun findAndClickExactOrDescription(vararg labels: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val wanted = labels.map { it.trim().lowercase() }
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val text = node.text?.toString()?.trim()?.lowercase().orEmpty()
            val description = node.contentDescription?.toString()?.trim()?.lowercase().orEmpty()
            if (wanted.any { it == text || it == description }) {
                if (clickNodeOrParent(node)) return true
            }
            for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
        }
        return false
    }

    private fun clickNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        repeat(5) {
            if (current == null) return false
            if (current.isClickable && current.isEnabled) return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            current = current.parent
        }
        return false
    }

    private fun dispatchTapNearRightCenter(xRatio: Float, yRatio: Float): Boolean {
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels * xRatio
        val y = metrics.heightPixels * yRatio
        return dispatchGesture(TapGesture(x, y).build(), null, null)
    }

    private fun dispatchSwipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long): Boolean {
        val metrics = resources.displayMetrics
        val path = android.graphics.Path().apply {
            moveTo(metrics.widthPixels * x1, metrics.heightPixels * y1)
            lineTo(metrics.widthPixels * x2, metrics.heightPixels * y2)
        }
        val gesture = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private class TapGesture(x: Float, y: Float) {
        private val path = android.graphics.Path().apply { moveTo(x, y); lineTo(x + 1f, y + 1f) }
        fun build(): android.accessibilityservice.GestureDescription = android.accessibilityservice.GestureDescription.Builder()
            .addStroke(android.accessibilityservice.GestureDescription.StrokeDescription(path, 0, 80))
            .build()
    }

    companion object {
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
        @Volatile private var instance: TommyAccessibilityService? = null
        @Volatile private var foregroundPackage: String = ""
        private val foregroundLock = Object()

        fun requestRecentApps() { instance?.openRecentAppsInternal() }
        fun performTommyAction(action: String): Boolean = instance?.performTommyActionInternal(action) == true
        fun sendChatGPTMessage(message: String): Boolean = instance?.sendChatGPTMessageInternal(message) == true

        /** Capture the current phone screen for Tommy's future visual reasoning pipeline. */
        fun captureScreen(callback: (Bitmap?) -> Unit) {
            val service = instance
            if (service == null) {
                callback(null)
                return
            }
            Handler(Looper.getMainLooper()).post {
                service.captureScreenInternal(callback)
            }
        }

        fun isServiceEnabled(): Boolean = instance != null

        fun waitForForegroundPackage(packageName: String, timeoutMs: Long = 3000L): Boolean {
            if (foregroundPackage == packageName) return true
            if (instance == null) return false

            val deadline = System.currentTimeMillis() + timeoutMs
            synchronized(foregroundLock) {
                while (System.currentTimeMillis() < deadline) {
                    if (foregroundPackage == packageName) return true
                    val remaining = deadline - System.currentTimeMillis()
                    if (remaining <= 0L) break
                    runCatching { foregroundLock.wait(minOf(remaining, 200L)) }
                }
            }
            return foregroundPackage == packageName
        }
    }
}
