package com.gokul.autoplay

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.text.TextUtils
import androidx.core.content.ContextCompat

object TaskExecutor {
    data class Result(val success: Boolean, val message: String)

    fun execute(context: Context, task: PhoneTask): Result = when (task.action) {
        PhoneTask.Action.PLAY_SPOTIFY -> openSpotifyTarget(context)
        PhoneTask.Action.CALL -> call(context, task.target)
        PhoneTask.Action.OPEN_APP -> openApp(context, task.target)
        PhoneTask.Action.OPEN_URL -> openUrl(context, task.target)
        PhoneTask.Action.SEND_WHATSAPP -> runWhatsAppMessage(context, task.target)
        PhoneTask.Action.EDIT_INSTAGRAM_BIO -> runInstagramBio(context, task.target)
        PhoneTask.Action.UNKNOWN -> Result(false, "Unsupported task")
    }

    private fun openSpotifyTarget(context: Context): Result {
        val spotify = context.packageManager.getLaunchIntentForPackage("com.spotify.music") ?: return Result(false, "Spotify is not installed")
        spotify.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(spotify)
        return Result(true, "Spotify opened. Full unattended playback requires Spotify App Remote authorization.")
    }

    private fun call(context: Context, target: String): Result {
        val number = target.filter { it.isDigit() || it == '+' }
        if (number.isBlank()) return Result(false, "For now, use a phone number for scheduled calls.")
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) return Result(false, "Call permission is required. Open AutoPlay once and grant phone permission.")
        val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        return runCatching { context.startActivity(intent); Result(true, "Calling $number") }.getOrElse { Result(false, "Android blocked the call: ${it.message ?: "unknown error"}") }
    }

    private fun openApp(context: Context, name: String): Result {
        val query = name.trim().lowercase()
        val packageName = when {
            "spotify" in query -> "com.spotify.music"
            "youtube" in query -> "com.google.android.youtube"
            "instagram" in query -> "com.instagram.android"
            "whatsapp" in query -> "com.whatsapp"
            "maps" in query || "google map" in query -> "com.google.android.apps.maps"
            "chrome" in query -> "com.android.chrome"
            else -> return Result(false, "I don't have a package mapping for '$name' yet.")
        }
        val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: return Result(false, "$name is not installed")
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        return Result(true, "Opened $name")
    }

    private fun runWhatsAppMessage(context: Context, target: String): Result {
        val parts = target.split("|||", limit = 2)
        if (parts.size != 2 || parts[0].isBlank() || parts[1].isBlank()) return Result(false, "Use: Open WhatsApp and send Praneesh Hi")
        if (!isAccessibilityEnabled(context)) return Result(false, "Enable Phone Automation in Android Accessibility Settings first.")
        val launch = context.packageManager.getLaunchIntentForPackage("com.whatsapp") ?: return Result(false, "WhatsApp is not installed")
        PhoneAutomationService.beginWhatsAppMessage(parts[0].trim(), parts[1].trim())
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        return Result(true, "WhatsApp opened for ${parts[0].trim()}. The automation bridge is enabled; the actual send flow still needs device-specific UI verification.")
    }

    private fun runInstagramBio(context: Context, bio: String): Result {
        if (bio.isBlank()) return Result(false, "Use: Change my Instagram bio to <your bio>")
        if (!isAccessibilityEnabled(context)) return Result(false, "Enable Phone Automation in Android Accessibility Settings first.")
        val launch = context.packageManager.getLaunchIntentForPackage("com.instagram.android") ?: return Result(false, "Instagram is not installed")
        PhoneAutomationService.beginInstagramBio(bio)
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
        return Result(true, "Instagram opened for bio editing. The automation bridge is enabled; the actual edit/save flow still needs device-specific UI verification.")
    }

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as android.view.accessibility.AccessibilityManager
        val services = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        val expected = "${context.packageName}/${PhoneAutomationService::class.java.name}"
        return services.any { service ->
            val info = service.resolveInfo.serviceInfo
            TextUtils.equals("${info.packageName}/${info.name}", expected)
        }
    }

    private fun openUrl(context: Context, value: String): Result {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(value)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        return if (intent.resolveActivity(context.packageManager) != null) { context.startActivity(intent); Result(true, "Opened $value") } else Result(false, "No app can open that URL")
    }
}
