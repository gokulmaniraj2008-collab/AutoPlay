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
            isFlashlightOnCommand(lower) -> PhoneTask.Action.FLASHLIGHT_ON
            isFlashlightOffCommand(lower) -> PhoneTask.Action.FLASHLIGHT_OFF
            isInstagramBioCommand(lower) -> PhoneTask.Action.EDIT_INSTAGRAM_BIO
            isWhatsAppMessageCommand(lower) -> PhoneTask.Action.SEND_WHATSAPP
            "spotify" in lower || "song" in lower || "music" in lower -> PhoneTask.Action.PLAY_SPOTIFY
            "call " in lower || lower.startsWith("call") -> PhoneTask.Action.CALL
            "open " in lower || "launch " in lower -> PhoneTask.Action.OPEN_APP
            lower.startsWith("http://") || lower.startsWith("https://") -> PhoneTask.Action.OPEN_URL
            else -> PhoneTask.Action.UNKNOWN
        }

        if (action == PhoneTask.Action.UNKNOWN) {
            return ParseResult(null, "Try: turn on the flashlight, turn off the flashlight, open WhatsApp and send Praneesh Hi, change Instagram bio to ..., or open another app.")
        }

        val scheduledAt = parseTime(raw, nowMillis)
        val target = when (action) {
            PhoneTask.Action.CALL -> raw.replace(Regex("(?i)\\bcall\\b"), "").replace(Regex("(?i)\\bat\\b.*$"), "").trim()
            PhoneTask.Action.OPEN_APP -> raw.replace(Regex("(?i)\\b(open|launch)\\b"), "").replace(Regex("(?i)\\bat\\b.*$"), "").trim()
            PhoneTask.Action.SEND_WHATSAPP -> parseWhatsAppTarget(raw)
            PhoneTask.Action.EDIT_INSTAGRAM_BIO -> parseInstagramBio(raw)
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

    private fun isFlashlightOnCommand(lower: String): Boolean {
        val light = "flashlight" in lower || "torch" in lower || "phone light" in lower || "phone torch" in lower
        val on = "turn on" in lower || "switch on" in lower || "switch on" in lower || "light on" in lower || lower.endsWith(" on") || lower == "light"
        return light && on || lower.contains("light on") && !lower.contains("screen light")
    }

    private fun isFlashlightOffCommand(lower: String): Boolean {
        val light = "flashlight" in lower || "torch" in lower || "phone light" in lower || "phone torch" in lower
        val off = "turn off" in lower || "switch off" in lower || "light off" in lower || lower.endsWith(" off")
        return light && off
    }

    private fun isWhatsAppMessageCommand(lower: String): Boolean =
        "whatsapp" in lower && ("send" in lower || "message" in lower || "text" in lower)

    private fun isInstagramBioCommand(lower: String): Boolean =
        "instagram" in lower && "bio" in lower && ("change" in lower || "edit" in lower || "set" in lower || "update" in lower)

    /** Stores recipient and message as: recipient|||message */
    private fun parseWhatsAppTarget(raw: String): String {
        val match = Regex("(?is)(?:whatsapp.*?)(?:friend|contact)?\\s*[(:]?\\s*([^:)]+?)\\s*\\)?\\s+(?:and\\s+)?(?:send|message|text)\\s+(.+)$").find(raw)
            ?: Regex("(?is)(?:send|message|text)\\s+(?:to\\s+)?(?:my\\s+friend\\s+)?[(:]?\\s*([^:)]+?)\\s*\\)?\\s+(.+)$").find(raw)
        if (match != null) {
            return "${match.groupValues[1].trim()}|||${match.groupValues[2].trim()}"
        }
        return "|||"
    }

    private fun parseInstagramBio(raw: String): String {
        return Regex("(?is)(?:change|edit|set|update).*?instagram.*?bio\\s*(?:to|as|:)?\\s*(.+)$").find(raw)?.groupValues?.get(1)?.trim()
            ?: Regex("(?is)bio\\s*(?:to|as|:)?\\s*(.+)$").find(raw)?.groupValues?.get(1)?.trim()
            ?: ""
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
