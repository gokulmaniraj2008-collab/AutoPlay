package com.gokul.autoplay

/** A normalized user task that AutoPlay can execute through Android capabilities. */
data class PhoneTask(
    val id: String,
    val rawCommand: String,
    val action: Action,
    val target: String = "",
    val scheduledAtMillis: Long? = null,
    val enabled: Boolean = true
) {
    enum class Action {
        PLAY_SPOTIFY,
        CALL,
        OPEN_APP,
        OPEN_URL,
        YOUTUBE_SEARCH,
        SEND_WHATSAPP,
        EDIT_INSTAGRAM_BIO,
        FLASHLIGHT_ON,
        FLASHLIGHT_OFF,
        UNKNOWN
    }
}
