package com.gokul.autoplay

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityEvent
import android.os.Bundle
import android.text.InputType

class TommyAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    private fun openRecentAppsInternal() { performGlobalAction(GLOBAL_ACTION_RECENTS) }

    private fun performTommyActionInternal(action: String): Boolean {
        return when (action) {
            "OPEN_INSTAGRAM_REELS" -> findAndClickAny("Reels", "reels")
            "SCROLL_REEL" -> dispatchSwipe(0.50f, 0.78f, 0.50f, 0.25f, 350)
            "LIKE_REEL" -> {
                val clicked = findAndClickAny("Like", "like")
                if (!clicked) dispatchTapNearRightCenter(0.90f, 0.62f) else true
            }
            "FOLLOW_ACCOUNT" -> {
                val clicked = findAndClickAny("Follow", "follow")
                if (!clicked) findAndClickAny("Follow back", "follow back") else true
            }
            "OPEN_COMMENTS" -> {
                val clicked = findAndClickAny("Comment", "comment", "Comments", "comments")
                if (!clicked) dispatchTapNearRightCenter(0.90f, 0.53f) else true
            }
            else -> false
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
        @Volatile private var instance: TommyAccessibilityService? = null

        fun requestRecentApps() { instance?.openRecentAppsInternal() }
        fun performTommyAction(action: String): Boolean = instance?.performTommyActionInternal(action) == true
        fun sendChatGPTMessage(message: String): Boolean = instance?.sendChatGPTMessageInternal(message) == true
    }
}
