package com.gokul.autoplay

object SpotifyConfig {
    const val REDIRECT_URI = "autoplay://spotify/callback"
    val clientId: String
        get() = BuildConfig.SPOTIFY_CLIENT_ID
}
