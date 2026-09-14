package com.gokul.autoplay

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
    private var exactAlarmReady by mutableStateOf(false)

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        exactAlarmReady = exactAlarmPermissionGranted()
        setContent { Home() }
        syncCloud()
    }

    override fun onResume() {
        super.onResume()
        exactAlarmReady = exactAlarmPermissionGranted()
    }

    private fun exactAlarmPermissionGranted(): Boolean =
        Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }
    }

    private fun syncCloud() {
        status = "Syncing from Supabase…"
        CloudScheduleSync.sync(this) { result ->
            runOnUiThread {
                result.onSuccess { count ->
                    scheduleCount = count
                    ExactCloudAlarmScheduler.sync(this)
                    status = "Synced $count schedule(s)."
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
                    Text(if (exactAlarmReady) "✓ Precise alarms enabled" else "⚠ Precise alarms permission required")
                    Text(status, style = MaterialTheme.typography.bodyMedium)
                    if (!exactAlarmReady) {
                        Button(onClick = { requestExactAlarmPermission() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Allow precise schedule alarms")
                        }
                    }
                    Button(onClick = { syncCloud() }, modifier = Modifier.fillMaxWidth()) {
                        Text("Sync from Supabase")
                    }
                    Text("Keep Spotify installed and signed in. At the scheduled time AutoPlay opens the saved playlist in Spotify.")
                }
            }
        }
    }
}
