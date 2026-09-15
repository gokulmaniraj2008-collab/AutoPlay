package com.gokul.autoplay.skills

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.gokul.autoplay.TommyAccessibilityService
import java.net.URLEncoder

/** Handles YouTube open and search commands with foreground verification. */
class YouTubeSkill : TommySkill {
    override val id: String = "youtube"
    override val name: String = "YouTube"
    override val description: String = "Opens YouTube and searches for videos or topics."

    override fun canHandle(command: String): Boolean {
        val normalized = normalize(command)
        return normalized.contains("youtube")
    }

    override fun execute(context: Context, command: String): TommySkillResult {
        val normalized = normalize(command)
        val isSearch = normalized.containsAny("search", "find", "look for")
        val query = normalized
            .replace("youtube", "")
            .replace(Regex("\\b(search|find|look for)\\b"), "")
            .replace(Regex("\\b(on|in)\\b"), "")
            .trim()

        if (!TommyAccessibilityService.isServiceEnabled()) {
            return TommySkillResult.failure(
                id,
                "I opened YouTube, but I can't verify it because Tommy Accessibility is OFF. Enable Tommy Accessibility and try again.",
                if (isSearch) "YOUTUBE_SEARCH" else "OPEN_YOUTUBE"
            )
        }

        return try {
            if (isSearch && query.isNotBlank()) {
                val encoded = URLEncoder.encode(query, "UTF-8")
                val searchUrl = "https://www.youtube.com/results?search_query=$encoded"
                val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)).apply {
                    setPackage(YOUTUBE_PACKAGE)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (context.packageManager.resolveActivity(appIntent, 0) == null) {
                    return TommySkillResult.failure(id, "YouTube app is not installed, so I can't verify the real YouTube app.", "YOUTUBE_SEARCH")
                }

                context.startActivity(appIntent)
                val verified = TommyAccessibilityService.waitForForegroundPackage(YOUTUBE_PACKAGE)
                if (!verified) {
                    TommySkillResult.failure(id, "YouTube search was requested, but Tommy could not verify that the YouTube app reached the foreground.", "YOUTUBE_SEARCH")
                } else {
                    TommySkillResult.success(id, "YouTube search results for $query are open.", "YOUTUBE_SEARCH")
                }
            } else {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(YOUTUBE_PACKAGE)
                if (launchIntent == null) {
                    return TommySkillResult.failure(id, "YouTube app is not installed, so I can't verify the real YouTube app.", "OPEN_YOUTUBE")
                }

                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                val verified = TommyAccessibilityService.waitForForegroundPackage(YOUTUBE_PACKAGE)
                if (!verified) {
                    TommySkillResult.failure(id, "YouTube was opened, but Tommy could not verify that the YouTube app reached the foreground.", "OPEN_YOUTUBE")
                } else {
                    TommySkillResult.success(id, "YouTube opened successfully.", "OPEN_YOUTUBE")
                }
            }
        } catch (_: Exception) {
            TommySkillResult.failure(
                id,
                if (isSearch) "I couldn't open YouTube search." else "I couldn't open YouTube.",
                if (isSearch) "YOUTUBE_SEARCH" else "OPEN_YOUTUBE"
            )
        }
    }

    private fun normalize(command: String): String =
        command.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun String.containsAny(vararg values: String): Boolean = values.any { contains(it) }

    companion object {
        private const val YOUTUBE_PACKAGE = "com.google.android.youtube"
    }
}
