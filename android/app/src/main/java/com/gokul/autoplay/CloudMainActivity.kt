package com.gokul.autoplay

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

class CloudMainActivity : ComponentActivity() {
    private var status by mutableStateOf("Ready")
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
        status = "Music ready: $name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        exactAlarmReady = exactAlarmPermissionGranted()
        setContent { AppRoot() }
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
            status = "Choose music first for ${schedule.name}."
            return
        }
        status = if (enabled) "Turning on ${schedule.name}…" else "Turning off ${schedule.name}…"
        CloudScheduleSync.setEnabled(this, schedule.id, enabled) { result ->
            runOnUiThread {
                result.onSuccess {
                    schedules = CloudScheduleStore.loadAll(this)
                    schedules.firstOrNull { it.id == schedule.id }?.let { updated ->
                        if (updated.enabled) ExactCloudAlarmScheduler.schedule(this, updated)
                        else ExactCloudAlarmScheduler.cancel(this, updated.id)
                    }
                    status = if (enabled) "${schedule.name} is ON. It will play at the scheduled time." else "${schedule.name} is OFF."
                }.onFailure { error ->
                    status = "Could not change ${schedule.name}: ${error.message ?: "unknown error"}"
                }
            }
        }
    }

    private fun syncCloud() {
        status = "Syncing schedules…"
        CloudScheduleSync.sync(this) { result ->
            runOnUiThread {
                result.onSuccess { count ->
                    schedules = CloudScheduleStore.loadAll(this)
                    scheduleCount = count
                    ExactCloudAlarmScheduler.sync(this)
                    status = if (count == 0) "No schedules found." else "Schedules updated."
                }.onFailure { error ->
                    schedules = CloudScheduleStore.loadAll(this)
                    scheduleCount = schedules.size
                    status = "Sync failed: ${error.message ?: "unknown error"}"
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun AppRoot() {
        val prefs = getSharedPreferences("autoplay_prefs", MODE_PRIVATE)
        var showSplash by androidx.compose.runtime.remember { mutableStateOf(true) }
        var showOnboarding by androidx.compose.runtime.remember { mutableStateOf(!prefs.getBoolean("onboarding_complete", false)) }

        if (showSplash) {
            SplashScreen()
            LaunchedEffect(Unit) {
                delay(1200)
                showSplash = false
            }
        } else if (showOnboarding) {
            GetStartedScreen {
                prefs.edit().putBoolean("onboarding_complete", true).apply()
                showOnboarding = false
            }
        } else {
            Home()
        }
    }

    @androidx.compose.runtime.Composable
    private fun BrandMark(size: Int = 88) {
        Box(
            modifier = Modifier.size(size.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {}
            Text("♫", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
        }
    }

    @androidx.compose.runtime.Composable
    private fun SplashScreen() {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                BrandMark(104)
                Spacer(Modifier.height(22.dp))
                Text("AutoPlay", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Your music. Your time.", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun GetStartedScreen(onStarted: () -> Unit) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                BrandMark(112)
                Spacer(Modifier.height(28.dp))
                Text("Welcome to AutoPlay", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text(
                    "Schedule music on your Android phone and let AutoPlay play it automatically when the time comes.",
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(24.dp))
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Simple & ₹0", fontWeight = FontWeight.Bold)
                        Text("• Use music stored on your phone")
                        Text("• Create daily playback schedules")
                        Text("• No Spotify subscription required")
                    }
                }
                Spacer(Modifier.height(28.dp))
                Button(onClick = onStarted, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Text("Get Started", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun Home() {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BrandMark(52)
                                Spacer(Modifier.size(12.dp))
                                Column {
                                    Text("AutoPlay", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                                    Text("Your music. Your time.")
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("QUICK SETUP", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text(if (exactAlarmReady) "✓ Exact-time alarms are ready" else "1. Allow exact-time alarms")
                                Text("2. Choose a song for each schedule")
                                Text("3. Turn the schedule ON")
                                if (!exactAlarmReady) {
                                    Button(onClick = { requestExactAlarmPermission() }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Allow exact-time alarms")
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Schedules", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        Text("$scheduleCount schedule(s) • ${schedules.count { it.enabled }} ON")
                                    }
                                    OutlinedButton(onClick = { syncCloud() }) { Text("Sync") }
                                }
                                Divider()
                                Text(status, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    if (schedules.isEmpty()) {
                        item {
                            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("No schedules yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                    Text("Create a schedule on the website, then tap Sync here.")
                                }
                            }
                        }
                    }

                    items(schedules, key = { it.id }) { schedule ->
                        val track = LocalTrackStore.get(this@CloudMainActivity, schedule.id)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (schedule.enabled) MaterialTheme.colorScheme.secondaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(schedule.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(schedule.time.take(5), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                                        Text(if (schedule.enabled) "ON • will play automatically" else "OFF • not scheduled")
                                    }
                                    Switch(
                                        checked = schedule.enabled,
                                        onCheckedChange = { setScheduleEnabled(schedule, it) },
                                        enabled = schedule.enabled || track != null
                                    )
                                }
                                Divider()
                                Text("Music", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                Text(track?.name ?: "No music selected yet")
                                Button(onClick = { chooseMusic(schedule.id) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(if (track == null) "Choose music" else "Change music")
                                }
                                if (track == null) {
                                    Text("Choose a song first, then turn ON the switch.", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("₹0 local playback • Music stays on your phone • No Spotify required", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
