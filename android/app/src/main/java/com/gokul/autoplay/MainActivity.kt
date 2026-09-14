package com.gokul.autoplay

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    private var playlistUrl by mutableStateOf("")
    private var enabled by mutableStateOf(false)
    private var status by mutableStateOf("No schedule saved")

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val saved = ScheduleStore.load(this)
        playlistUrl = saved.playlistUrl
        enabled = saved.enabled
        status = if (enabled) "Daily 3:00 PM schedule is active" else "No schedule saved"

        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent { AutoPlayHome() }
    }

    private fun saveSchedule() {
        if (playlistUrl.isBlank()) {
            status = "Enter a playlist URL first"
            return
        }
        ScheduleStore.save(
            this,
            ScheduleStore.Schedule(enabled = true, time = "15:00", playlistUrl = playlistUrl.trim())
        )
        AutoPlayScheduler.schedule3Pm(this)
        enabled = true
        status = "Scheduled every day at 3:00 PM"
    }

    private fun disableSchedule() {
        ScheduleStore.save(
            this,
            ScheduleStore.Schedule(enabled = false, time = "15:00", playlistUrl = playlistUrl)
        )
        AutoPlayScheduler.cancel(this)
        enabled = false
        status = "Schedule disabled"
    }

    private fun testNow() {
        val url = playlistUrl.trim()
        if (url.isBlank()) {
            status = "Enter a playlist URL first"
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
            status = "Opened playlist link. Playback is controlled by the music app."
        } else {
            status = "No app can open this playlist URL"
        }
    }

    private fun openSpotify() {
        val intent = packageManager.getLaunchIntentForPackage("com.spotify.music")
        if (intent != null) startActivity(intent) else status = "Spotify is not installed"
    }

    @androidx.compose.runtime.Composable
    private fun AutoPlayHome() {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("AutoPlay", style = MaterialTheme.typography.headlineLarge)
                    Text("Automate your daily music routine.")

                    OutlinedTextField(
                        value = playlistUrl,
                        onValueChange = { playlistUrl = it },
                        label = { Text("Spotify playlist URL") },
                        placeholder = { Text("https://open.spotify.com/playlist/...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text("Daily schedule: 3:00 PM")
                    Text(status, style = MaterialTheme.typography.bodyMedium)

                    Button(onClick = { saveSchedule() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Save 3:00 PM schedule")
                    }
                    Button(onClick = { testNow() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Test Now")
                    }
                    Button(onClick = { openSpotify() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Open Spotify")
                    }
                    Button(onClick = { disableSchedule() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Disable schedule")
                    }

                    if (enabled) Text("✓ AutoPlay is enabled", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
