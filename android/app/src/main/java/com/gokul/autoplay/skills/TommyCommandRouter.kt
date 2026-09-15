package com.gokul.autoplay.skills

import android.content.Context

/**
 * JARVIS-style central command router.
 *
 * Every command source (chat, voice, Supabase, scheduled commands) can enter
 * through this router. The router decides which registered skill owns the
 * command and keeps execution/error handling in one place.
 */
object TommyCommandRouter {
    data class Route(
        val skillId: String,
        val skillName: String,
        val command: String
    )

    fun route(command: String): Route? {
        val normalized = command.trim().replace(Regex("\\s+"), " ")
        if (normalized.isBlank()) return null

        val skill = TommySkillRegistry.find(normalized) ?: return null
        return Route(
            skillId = skill.id,
            skillName = skill.name,
            command = normalized
        )
    }

    fun execute(context: Context, command: String): TommySkillResult {
        val route = route(command)
            ?: return TommySkillResult.failure(
                "system.router",
                "I received: \"${command.trim()}\". I don't have a skill for that yet."
            )

        val skill = TommySkillRegistry.all().firstOrNull { it.id == route.skillId }
            ?: return TommySkillResult.failure("system.router", "Tommy couldn't load ${route.skillName}.")

        return runCatching {
            skill.execute(context, route.command)
        }.getOrElse { error ->
            TommySkillResult.failure(
                skill.id,
                "${skill.name} couldn't complete that command: ${error.message ?: "unknown error"}"
            )
        }
    }
}
