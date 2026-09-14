package com.gokul.autoplay

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * User-enabled accessibility bridge reserved for AutoPlay phone workflows.
 * AutoPlay does not silently enable this service or bypass Android consent.
 */
class PhoneAutomationService : AccessibilityService() {
    companion object {
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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // The service is intentionally passive until a supported, explicitly authorized
        // automation workflow is implemented and verified on the target Android/third-party UI.
    }

    override fun onInterrupt() {
        clearPendingTask()
    }
}
