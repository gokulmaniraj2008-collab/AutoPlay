package com.gokul.autoplay.skills

import android.content.Context

/**
 * Central registry for Tommy capabilities.
 *
 * New features should be added as skills and registered here instead of growing
 * one large when/else command handler in the UI or floating service.
 */
object TommySkillRegistry {
    private val skills = linkedMapOf<String, TommySkill>()
    private var initialized = false

    @Synchronized
    fun initialize() {
        if (initialized) return

        register(HelpSkill())
        register(InstagramSkill())
        register(AppLaunchSkill())
        register(WebSearchSkill())
        register(SpotifySkill())
        register(DeviceControlSkill())
        register(ChatGPTSkill())

        initialized = true
    }

    @Synchronized
    fun register(skill: TommySkill) {
        require(skill.id.isNotBlank()) { "Tommy skill id cannot be blank" }
        skills[skill.id] = skill
    }

    @Synchronized
    fun unregister(skillId: String) { skills.remove(skillId) }

    @Synchronized
    fun all(): List<TommySkill> = skills.values.toList()

    @Synchronized
    fun find(command: String): TommySkill? {
        val normalized = command.trim()
        if (normalized.isBlank()) return null
        return skills.values.firstOrNull { skill -> runCatching { skill.canHandle(normalized) }.getOrDefault(false) }
    }

    fun execute(context: Context, command: String): TommySkillResult {
        initialize()
        val skill = find(command) ?: return TommySkillResult.failure("system", "I received: \"$command\". I don't have a skill for that yet.")
        return runCatching { skill.execute(context, command) }.getOrElse { error ->
            TommySkillResult.failure(skill.id, "${skill.name} couldn't complete that command: ${error.message ?: "unknown error"}")
        }
    }
}

private class HelpSkill : TommySkill {
    override val id = "system.help"
    override val name = "Tommy Help"
    override val description = "Explains the commands Tommy currently supports."
    override fun canHandle(command: String): Boolean {
        val normalized = command.lowercase().trim()
        return normalized == "help" || normalized == "what can you do" || normalized == "commands"
    }
    override fun execute(context: Context, command: String): TommySkillResult = TommySkillResult.success(
        skillId = id,
        action = "SHOW_HELP",
        message = "Try: Open Instagram, go to Reels, scroll, like, follow, open comments, open YouTube, Google, WhatsApp, search Google, search Spotify, or send a message to ChatGPT."
    )
}
