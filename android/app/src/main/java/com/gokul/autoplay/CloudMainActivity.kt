package com.gokul.autoplay

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
    private var schedules by mutableStateOf<List<CloudScheduleStore.Schedule>>(emptyList())
    private var pendingScheduleId: String? = null

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val audioPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val scheduleId = pendingScheduleId
        pendingScheduleId = null
        if (uri == null || scheduleId == null) return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val name = displayName(uri)
        LocalTrackStore.put(this, scheduleId, uri, name)
        schedules = CloudScheduleStore.loadAll(this)
        schedules.firstOrNull { it.id == scheduleId }?.let { ExactCloudAlarmScheduler.schedule(this, it) }
        status = "Music selected: $name"
    }

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

    private fun chooseMusic(scheduleId: String) {
        pendingScheduleId = scheduleId
        audioPicker.launch(arrayOf("audio/*"))
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return uri.lastPathSegment ?: "Local music"
    }

    private fun setScheduleEnabled(schedule: CloudScheduleStore.Schedule, enabled: Boolean) {
        if (enabled && LocalTrackStore.get(this, schedule.id) == null) {
            status = "Choose local music before enabling this schedule."
            return
        }
        status = if (enabled) "Enabling ${schedule.name}…" else "Disabling ${schedule.name}…"
        CloudScheduleSync.setEnabled(this, schedule.id, enabled) { result ->
            runOnUiThread {
                result.onSuccess {
                    schedules = CloudScheduleStore.loadAll(this)
                    schedules.firstOrNull { it.id == schedule.id }?.let { updated ->
                        if (updated.enabled) {
                            ExactCloudAlarmScheduler.schedule(this, updated)
                        } else {
                            ExactCloudAlarmScheduler.cancel(this, updated.id)
                        }
                    }
                    status = if (enabled) "${schedule.name} enabled and scheduled." else "${schedule.name} disabled and alarm cancelled."
                }.onFailure { error ->
                    status = "Could not change schedule: ${error.message ?: "unknown error"}"
                }
            }
        }
    }

    private fun syncCloud() {
        status = "Syncing from Supabase…"
        CloudScheduleSync.sync(this) { result ->
            runOnUiThread {
                result.onSuccess { count ->
                    schedules = CloudScheduleStore.loadAll(this)
                    scheduleCount = count
                    ExactCloudAlarmScheduler.sync(this)
                    status = "Synced $count schedule(s). Choose music for each schedule."
                }.onFailure { error ->
                    schedules = CloudScheduleStore.loadAll(this)
                    scheduleCount = schedules.size
                    status = "Sync failed: ${error.message ?: "unknown error"}"
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Home() {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text("AutoPlay", style = MaterialTheme.typography.headlineLarge)
                        Text("₹0 scheduled music player — plays audio stored on this phone.")
                        Text("Schedules: $scheduleCount")
                        Text(if (exactAlarmReady) "✓ Precise alarms enabled" else "⚠ Allow precise alarms for exact times")
                        Text(status, style = MaterialTheme.typography.bodyMedium)
                        if (!exactAlarmReady) {
                            Button(onClick = { requestExactAlarmPermission() }, modifier = Modifier.fillMaxWidth()) {
                                Text("Allow precise schedule alarms")
                            }
                        }
                        Button(onClick = { syncCloud() }, modifier = Modifier.fillMaxWidth()) {
                            Text("Sync from Supabase")
                        }
                    }
                    items(schedules, key = { it.id }) { schedule ->
                        val track = LocalTrackStore.get(this@CloudMainActivity, schedule.id)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(schedule.name, style = MaterialTheme.typography.titleLarge)
                                Text("Time: ${schedule.time.take(5)}")
                                Text(if (schedule.enabled) "Enabled" else "Disabled")
                                Text(track?.name ?: "No local music selected")
                                Button(
                                    onClick = { chooseMusic(schedule.id) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (track == null) "Choose music" else "Change music")
                                }
                                Button(
                                    onClick = { setScheduleEnabled(schedule, !schedule.enabled) },
                                    enabled = schedule.enabled || track != null,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (schedule.enabled) "Disable schedule" else "Enable schedule")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
