package com.gokul.autoplay

import android.Manifest
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class CloudMainActivity : ComponentActivity() {
    private var status by mutableStateOf("Cloud schedules not synced")
    private var scheduleCount by mutableStateOf(0)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { Home() }
        syncCloud()
    }

    private fun syncCloud() {
        status = "Syncing from Supabase…"
        CloudScheduleSync.sync(this) { result ->
            runOnUiThread {
                result.onSuccess { count ->
                    scheduleCount = count
                    ExactCloudAlarmScheduler.sync(this)
                    status = "Synced $count schedule(s). Precise alarms are active for enabled schedules."
                }.onFailure { error ->
                    scheduleCount = CloudScheduleStore.loadAll(this).size
                    status = "Sync failed: ${error.message ?: "unknown error"}"
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Home() {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("AutoPlay", style = MaterialTheme.typography.headlineLarge)
                    Text("Cloud-controlled Spotify scheduling")
                    Text("Schedules on device: $scheduleCount")
                    Text(status, style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { syncCloud() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Sync from Supabase")
                    }
                    Text("Keep Spotify installed and signed in. At the scheduled time AutoPlay opens the saved playlist in Spotify.")
                }
            }
        }
    }
}
