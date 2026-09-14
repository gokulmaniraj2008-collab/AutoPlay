package com.gokul.autoplay

import android.content.Context
import android.util.Log
import com.spotify.android.appremote.api.ConnectionParams
import com.spotify.android.appremote.api.Connector
import com.spotify.android.appremote.api.SpotifyAppRemote
import java.util.concurrent.atomic.AtomicBoolean

object SpotifyPlayback {
    private const val TAG = "AutoPlaySpotify"

    fun connectForSetup(context: Context, onResult: (Result<Unit>) -> Unit) {
        connect(context, showAuthView = true, onConnected = { remote ->
            remote.disconnectAfter(500L) { onResult(Result.success(Unit)) }
        }, onResult = onResult)
    }

    fun play(context: Context, playlistUri: String, onResult: (Result<Unit>) -> Unit) {
        if (SpotifyConfig.clientId.isBlank()) {
            onResult(Result.failure(IllegalStateException("Spotify Client ID is not configured")))
            return
        }

        connect(context, showAuthView = false, onConnected = { remote ->
            Log.d(TAG, "Connected to Spotify App Remote; calling playerApi.play($playlistUri)")
            remote.playerApi
                .play(playlistUri)
                .setResultCallback {
                    Log.d(TAG, "Spotify playerApi.play succeeded")
                    remote.disconnectAfter(250L) { onResult(Result.success(Unit)) }
                }
                .setErrorCallback { error ->
                    Log.e(TAG, "Spotify playerApi.play failed", error)
                    remote.disconnectAfter(250L) {
                        onResult(Result.failure(error))
                    }
                }
        }, onResult = onResult)
    }

    private fun connect(
        context: Context,
        showAuthView: Boolean,
        onConnected: (SpotifyAppRemote) -> Unit,
        onResult: (Result<Unit>) -> Unit
    ) {
        val clientId = SpotifyConfig.clientId
        if (clientId.isBlank()) {
            onResult(Result.failure(IllegalStateException("Spotify Client ID is not configured")))
            return
        }

        val finished = AtomicBoolean(false)
        val params = ConnectionParams.Builder(clientId)
            .setRedirectUri(SpotifyConfig.REDIRECT_URI)
            .showAuthView(showAuthView)
            .build()

        SpotifyAppRemote.connect(context.applicationContext, params, object : Connector.ConnectionListener {
            override fun onConnected(appRemote: SpotifyAppRemote) {
                if (!finished.get()) onConnected(appRemote)
            }

            override fun onFailure(throwable: Throwable) {
                if (finished.compareAndSet(false, true)) {
                    Log.e(TAG, "Spotify App Remote connection failed", throwable)
                    onResult(Result.failure(throwable))
                }
            }
        })
    }

    private fun SpotifyAppRemote.disconnectAfter(delayMs: Long, after: () -> Unit) {
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            SpotifyAppRemote.disconnect(this)
            after()
        }, delayMs)
    }
}
