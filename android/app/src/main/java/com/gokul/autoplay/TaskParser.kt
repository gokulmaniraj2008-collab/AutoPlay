package com.gokul.autoplay

import java.util.Calendar
import java.util.Locale
import java.util.UUID

object TaskParser {
    data class ParseResult(val task: PhoneTask?, val message: String)

    fun parse(command: String, nowMillis: Long = System.currentTimeMillis()): ParseResult {
        val raw = command.trim()
        if (raw.isBlank()) return ParseResult(null, "Enter a task.")
        val lower = raw.lowercase(Locale.getDefault())

        val action = when {
            "spotify" in lower || "song" in lower || "music" in lower -> PhoneTask.Action.PLAY_SPOTIFY
            "call " in lower || lower.startsWith("call") -> PhoneTask.Action.CALL
            "open " in lower || "launch " in lower -> PhoneTask.Action.OPEN_APP
            lower.startsWith("http://") || lower.startsWith("https://") -> PhoneTask.Action.OPEN_URL
            else -> PhoneTask.Action.UNKNOWN
        }

        if (action == PhoneTask.Action.UNKNOWN) {
            return ParseResult(null, "I don't know that action yet. Try: play Spotify, call a number, open an app, or open a URL.")
        }

        val scheduledAt = parseTime(raw, nowMillis)
        val target = when (action) {
            PhoneTask.Action.CALL -> raw.replace(Regex("(?i)\\bcall\\b"), "").replace(Regex("(?i)\\bat\\b.*$"), "").trim()
            PhoneTask.Action.OPEN_APP -> raw.replace(Regex("(?i)\\b(open|launch)\\b"), "").replace(Regex("(?i)\\bat\\b.*$"), "").trim()
            else -> raw
        }

        val task = PhoneTask(
            id = UUID.randomUUID().toString(),
            rawCommand = raw,
            action = action,
            target = target,
            scheduledAtMillis = scheduledAt,
            enabled = true
        )
        return ParseResult(task, if (scheduledAt == null) "Ready to run now." else "Scheduled for ${java.text.SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault()).format(scheduledAt)}.")
    }

    private fun parseTime(raw: String, nowMillis: Long): Long? {
        val match = Regex("(?i)\\b(?:at|@)\\s*(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?\\b").find(raw) ?: return null
        val hourRaw = match.groupValues[1].toIntOrNull() ?: return null
        val minute = match.groupValues[2].ifBlank { "0" }.toIntOrNull() ?: 0
        val suffix = match.groupValues[3].lowercase(Locale.getDefault())
        var hour = hourRaw
        if (suffix == "pm" && hour < 12) hour += 12
        if (suffix == "am" && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null

        return Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= nowMillis) add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
    }
}
