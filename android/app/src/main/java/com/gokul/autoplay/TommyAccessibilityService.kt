package com.gokul.autoplay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

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

    private fun openRecentAppsInternal() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    private fun openInstagramReelsInternal(): Boolean {
        val root = rootInActiveWindow ?: return false
        val candidates = listOf("Reels", "reels")
        for (label in candidates) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            for (node in nodes) {
                if (node.isVisibleToUser && node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                var parent = node.parent
                repeat(3) {
                    if (parent?.isVisibleToUser == true && parent.isClickable && parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
                    parent = parent?.parent
                }
            }
        }
        return false
    }

    private fun scrollReelInternal(): Boolean {
        val root = rootInActiveWindow
        val scrollable = findScrollableNode(root)
        if (scrollable != null && scrollable.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true

        // Instagram Reels uses a full-screen vertical feed, so fall back to a swipe gesture.
        val path = Path().apply {
            moveTo(resources.displayMetrics.widthPixels * 0.5f, resources.displayMetrics.heightPixels * 0.78f)
            lineTo(resources.displayMetrics.widthPixels * 0.5f, resources.displayMetrics.heightPixels * 0.22f)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 450))
            .build()
        return dispatchGesture(gesture, null, null)
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isVisibleToUser && node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val found = findScrollableNode(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    companion object {
        @Volatile
        private var instance: TommyAccessibilityService? = null

        fun requestRecentApps() {
            instance?.openRecentAppsInternal()
        }

        fun performTommyAction(action: String): Boolean {
            val service = instance ?: return false
            return when (action) {
                "OPEN_INSTAGRAM_REELS" -> service.openInstagramReelsInternal()
                "SCROLL_REEL" -> service.scrollReelInternal()
                else -> false
            }
        }
    }
}
