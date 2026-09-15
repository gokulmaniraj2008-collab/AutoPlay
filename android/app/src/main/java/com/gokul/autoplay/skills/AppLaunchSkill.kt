package com.gokul.autoplay.skills

import android.content.Context
import android.content.Intent
import android.net.Uri

/** Handles common app-opening commands through the same Tommy skill engine. */
class AppLaunchSkill : TommySkill {
    override val id: String = "apps.launch"
    override val name: String = "App Launcher"
    override val description: String = "Opens YouTube, Google, WhatsApp and other supported apps."

    override fun canHandle(command: String): Boolean {
        val normalized = normalize(command)
        return normalized.containsAny("youtube", "google", "whatsapp")
    }

    override fun execute(context: Context, command: String): TommySkillResult {
        val normalized = normalize(command)
        return when {
            normalized.contains("youtube") -> launch(
                context,
                "com.google.android.youtube",
                "https://www.youtube.com",
                "YouTube",
                "OPEN_YOUTUBE"
            )
            normalized.contains("google") -> launch(
                context,
                "com.google.android.googlequicksearchbox",
                "https://www.google.com",
                "Google",
                "OPEN_GOOGLE"
            )
            normalized.contains("whatsapp") -> launch(
                context,
                "com.whatsapp",
                "https://www.whatsapp.com",
                "WhatsApp",
                "OPEN_WHATSAPP"
            )
            else -> TommySkillResult.failure(id, "I don't have an app action for that yet.")
        }
    }

    private fun launch(
        context: Context,
        packageName: String,
        fallbackUrl: String,
        appName: String,
        action: String
    ): TommySkillResult {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
            TommySkillResult.success(id, "Opening $appName.", action)
        } catch (_: Exception) {
            TommySkillResult.failure(id, "I couldn't open $appName on this device.", action)
        }
    }

    private fun normalize(command: String): String =
        command.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun String.containsAny(vararg values: String): Boolean =
        values.any { contains(it) }
}
