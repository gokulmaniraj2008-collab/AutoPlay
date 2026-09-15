package com.gokul.autoplay.skills

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.gokul.autoplay.TommyAccessibilityService

/**
 * Instagram capability for Tommy.
 *
 * This skill owns Instagram-specific intent matching and delegates screen
 * interaction to TommyAccessibilityService. It intentionally does not contain
 * Gemini/voice/UI code, so more Instagram actions can be added independently.
 */
class InstagramSkill : TommySkill {
    override val id: String = "instagram"
    override val name: String = "Instagram"
    override val description: String =
        "Opens Instagram and controls Reels, likes, follows and comments."

    override fun canHandle(command: String): Boolean {
        val normalized = normalize(command)
        if (!normalized.contains("instagram")) return false

        return normalized.containsAny(
            "open", "start", "launch", "reel", "reels", "scroll", "swipe",
            "next", "like", "follow", "comment", "comments"
        )
    }

    override fun execute(context: Context, command: String): TommySkillResult {
        val normalized = normalize(command)

        // Specific actions must win over the generic "reel" matcher.
        // Example: "like this reel" must LIKE the current reel, not reopen Reels.
        return when {
            normalized.contains("like") ->
                executeAccessibilityAction("LIKE_REEL", "LIKE_REEL", "Liking this Reel.")

            normalized.contains("follow") -> {
                val username = extractFollowUsername(normalized)
                if (username != null) {
                    executeAccessibilityAction(
                        "FOLLOW_PROFILE:$username",
                        "FOLLOW_PROFILE",
                        "Opening @$username and following the account."
                    )
                } else {
                    executeAccessibilityAction(
                        "FOLLOW_ACCOUNT",
                        "FOLLOW_ACCOUNT",
                        "Following this account."
                    )
                }
            }

            normalized.contains("comment") ->
                executeAccessibilityAction("OPEN_COMMENTS", "OPEN_COMMENTS", "Opening comments.")

            normalized.containsAny("scroll", "swipe", "next") &&
                normalized.containsAny("reel", "reels") ->
                executeAccessibilityAction("SCROLL_REEL", "SCROLL_REEL", "Scrolling to the next Reel.")

            normalized.containsAny("reel", "reels") ->
                executeAccessibilityAction("OPEN_INSTAGRAM_REELS", "OPEN_INSTAGRAM_REELS", "Opening Instagram Reels.")

            isOpenCommand(normalized) -> openInstagram(context)

            else -> openInstagram(context)
        }
    }

    private fun extractFollowUsername(command: String): String? {
        val tokens = command.split(" ").filter { it.isNotBlank() }
        val followIndex = tokens.indexOfFirst { it == "follow" }
        if (followIndex < 0 || followIndex + 1 >= tokens.size) return null

        val candidate = tokens[followIndex + 1]
            .removePrefix("@")
            .trim()
            .replace(Regex("[^a-z0-9._]"), "")

        if (candidate.isBlank() || candidate in setOf("this", "the", "account", "user", "back")) return null
        if (candidate.length > 30) return null
        return candidate
    }

    private fun openInstagram(context: Context): TommySkillResult {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(INSTAGRAM_PACKAGE)
        return try {
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            } else {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(INSTAGRAM_URL)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
            TommySkillResult.success(id, "Instagram opened.", "OPEN_INSTAGRAM")
        } catch (_: Exception) {
            TommySkillResult.failure(id, "I couldn't open Instagram on this device.", "OPEN_INSTAGRAM")
        }
    }

    private fun executeAccessibilityAction(
        accessibilityAction: String,
        action: String,
        successMessage: String
    ): TommySkillResult {
        val executed = TommyAccessibilityService.performTommyAction(accessibilityAction)
        return if (executed) {
            TommySkillResult.success(id, successMessage, action)
        } else {
            TommySkillResult.failure(
                id,
                "I received the command, but Tommy Accessibility is not ready to control Instagram.",
                action
            )
        }
    }

    private fun isOpenCommand(command: String): Boolean =
        command.containsAny("open", "start", "launch")

    private fun normalize(command: String): String =
        command.lowercase()
            .replace(Regex("[^a-z0-9@._ ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun String.containsAny(vararg values: String): Boolean =
        values.any { contains(it) }

    companion object {
        private const val INSTAGRAM_PACKAGE = "com.instagram.android"
        private const val INSTAGRAM_URL = "https://www.instagram.com"
    }
}
