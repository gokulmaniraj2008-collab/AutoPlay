package com.gokul.autoplay.skills

import android.content.Context

/**
 * Small application-facing facade around the skill registry.
 * UI, voice and cloud command sources can all call the same engine.
 */
object TommySkillEngine {
    fun initialize() {
        TommySkillRegistry.initialize()
    }

    fun execute(context: Context, command: String): TommySkillResult {
        return TommySkillRegistry.execute(context, command)
    }

    fun availableSkills(): List<TommySkill> {
        TommySkillRegistry.initialize()
        return TommySkillRegistry.all()
    }
}
