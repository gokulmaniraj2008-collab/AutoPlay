package com.gokul.autoplay.skills

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

/** Handles web-search requests through the shared Tommy skill engine. */
class WebSearchSkill : TommySkill {
    override val id: String = "web.search"
    override val name: String = "Web Search"
    override val description: String = "Searches Google for a requested query."

    override fun canHandle(command: String): Boolean {
        val normalized = normalize(command)
        return normalized.containsAny("search google", "search the web", "google search", "search for")
    }

    override fun execute(context: Context, command: String): TommySkillResult {
        val normalized = normalize(command)
        val query = normalized
            .replaceFirst("search google", "")
            .replaceFirst("google search", "")
            .replaceFirst("search the web", "")
            .replaceFirst("search for", "")
            .trim()
            .ifBlank { return TommySkillResult.failure(id, "Tell me what you want me to search for.") }

        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=$encoded")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            TommySkillResult.success(id, "Searching Google for $query.", "SEARCH_WEB")
        } catch (_: Exception) {
            TommySkillResult.failure(id, "I couldn't start the Google search.", "SEARCH_WEB")
        }
    }

    private fun normalize(command: String): String =
        command.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun String.containsAny(vararg values: String): Boolean = values.any { contains(it) }
}
