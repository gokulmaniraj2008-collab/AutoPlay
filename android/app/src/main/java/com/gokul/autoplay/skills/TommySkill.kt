package com.gokul.autoplay.skills

import android.content.Context

/**
 * A single capability Tommy can execute.
 *
 * Skills own app-specific automation. The Tommy brain should decide WHAT to do;
 * a skill decides HOW to perform that capability on Android.
 */
interface TommySkill {
    /** Stable identifier used in logs, tests and future analytics. */
    val id: String

    /** Human-readable name shown in diagnostics/UI. */
    val name: String

    /** Short description of the capability. */
    val description: String

    /**
     * Returns true when this skill understands the user's natural-language command.
     * Keep matching lightweight; Gemini/ActionPlanner can provide structured intents later.
     */
    fun canHandle(command: String): Boolean

    /** Execute the command. Never throw for an expected user/action failure. */
    fun execute(context: Context, command: String): TommySkillResult
}

data class TommySkillResult(
    val success: Boolean,
    val message: String,
    val skillId: String,
    val action: String? = null,
    val shouldSpeak: Boolean = true,
    val shouldAddToChat: Boolean = true
) {
    companion object {
        fun success(
            skillId: String,
            message: String,
            action: String? = null
        ) = TommySkillResult(
            success = true,
            message = message,
            skillId = skillId,
            action = action
        )

        fun failure(
            skillId: String,
            message: String,
            action: String? = null
        ) = TommySkillResult(
            success = false,
            message = message,
            skillId = skillId,
            action = action
        )
    }
}
