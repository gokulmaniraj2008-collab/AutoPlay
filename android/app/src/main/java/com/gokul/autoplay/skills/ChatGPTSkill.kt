package com.gokul.autoplay.skills

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.gokul.autoplay.TommyAccessibilityService

/** Opens ChatGPT and, when requested, asks the accessibility service to send a message. */
class ChatGPTSkill : TommySkill {
    override val id = "chatgpt.message"
    override val name = "ChatGPT Messenger"
    override val description = "Opens ChatGPT and sends a requested message using Android accessibility."

    override fun canHandle(command: String): Boolean {
        val n = normalize(command)
        return n.contains("chatgpt") && (n.contains("send") || n.contains("message") || n.contains("say"))
    }

    override fun execute(context: Context, command: String): TommySkillResult {
        val message = extractMessage(command)
        if (message.isBlank()) return TommySkillResult.failure(id, "Tell me what to send to ChatGPT.")

        val intent = context.packageManager.getLaunchIntentForPackage("com.openai.chatgpt")
            ?: Intent(Intent.ACTION_VIEW, Uri.parse("https://chatgpt.com"))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)

        Thread {
            Thread.sleep(1400L)
            TommyAccessibilityService.sendChatGPTMessage(message)
        }.start()

        return TommySkillResult.success(id, "Opening ChatGPT and sending $message.", "CHATGPT_SEND")
    }

    private fun extractMessage(command: String): String {
        val raw = command.trim()
        val patterns = listOf(
            Regex("(?i)\\bsend\\s+(?:a\\s+)?message\\s+(.+?)\\s+to\\s+chatgpt\\b"),
            Regex("(?i)\\bchatgpt\\b.*?\\bsend\\s+(.+)$"),
            Regex("(?i)\\bchatgpt\\b.*?\\bsay\\s+(.+)$"),
            Regex("(?i)\\bsend\\s+(.+?)\\s+to\\s+chatgpt\\b")
        )
        return patterns.firstNotNullOfOrNull { it.find(raw)?.groupValues?.getOrNull(1)?.trim() }
            ?.trim('"', '\'')
            .orEmpty()
    }

    private fun normalize(value: String) = value.lowercase().replace(Regex("\\s+"), " ").trim()
}
