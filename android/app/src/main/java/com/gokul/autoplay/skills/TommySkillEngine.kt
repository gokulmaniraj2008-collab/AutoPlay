package com.gokul.autoplay.skills

import android.content.Context

/**
 * Single application-facing entry point for Tommy commands.
 * UI, voice, cloud and scheduled sources all use the same JARVIS-style router.
 */
object TommySkillEngine {
    fun initialize() {
        TommySkillRegistry.initialize()
    }

    fun execute(context: Context, command: String): TommySkillResult {
        TommySkillRegistry.initialize()
        return TommyCommandRouter.execute(context, command)
    }

    fun route(command: String): TommyCommandRouter.Route? {
        TommySkillRegistry.initialize()
        return TommyCommandRouter.route(command)
    }

    fun availableSkills(): List<TommySkill> {
        TommySkillRegistry.initialize()
        return TommySkillRegistry.all()
    }
}
