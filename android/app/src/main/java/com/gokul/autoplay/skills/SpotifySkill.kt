package com.gokul.autoplay.skills

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

/** Handles Spotify search/open requests through the shared Tommy skill engine. */
class SpotifySkill : TommySkill {
    override val id: String = "spotify"
    override val name: String = "Spotify"
    override val description: String = "Opens Spotify or searches Spotify for a song or artist."

    override fun canHandle(command: String): Boolean {
        val normalized = normalize(command)
        return normalized.contains("spotify")
    }

    override fun execute(context: Context, command: String): TommySkillResult {
        val normalized = normalize(command)
        val searchTerms = listOf("search", "find", "play")
        val isSearch = searchTerms.any { normalized.contains(it) }
        val query = normalized
            .replace("spotify", "")
            .replace(Regex("\\b(search|find|play)\\b"), "")
            .trim()

        return try {
            val uri = if (isSearch && query.isNotBlank()) {
                "https://open.spotify.com/search/${URLEncoder.encode(query, "UTF-8")}"
            } else {
                "https://open.spotify.com"
            }
            val launchIntent = context.packageManager.getLaunchIntentForPackage(SPOTIFY_PACKAGE)
            if (launchIntent != null && !isSearch) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
            } else {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
            val message = if (isSearch && query.isNotBlank()) {
                "Searching Spotify for $query."
            } else {
                "Opening Spotify."
            }
            TommySkillResult.success(id, message, if (isSearch) "SPOTIFY_SEARCH" else "OPEN_SPOTIFY")
        } catch (_: Exception) {
            TommySkillResult.failure(id, "I couldn't open Spotify.", if (isSearch) "SPOTIFY_SEARCH" else "OPEN_SPOTIFY")
        }
    }

    private fun normalize(command: String): String =
        command.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    companion object {
        private const val SPOTIFY_PACKAGE = "com.spotify.music"
    }
}
