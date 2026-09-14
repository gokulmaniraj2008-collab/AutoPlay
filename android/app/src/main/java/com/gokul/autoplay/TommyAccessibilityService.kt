package com.gokul.autoplay

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

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

    companion object {
        @Volatile
        private var instance: TommyAccessibilityService? = null

        fun requestRecentApps() {
            instance?.openRecentAppsInternal()
        }
    }
}
