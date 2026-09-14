package com.gokul.autoplay

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class CloudMainActivity : ComponentActivity() {
    private var status by mutableStateOf("Ready")
    private var scheduleCount by mutableStateOf(0)
    private var exactAlarmReady by mutableStateOf(false)
    private var schedules by mutableStateOf<List<CloudScheduleStore.Schedule>>(emptyList())
    private var pendingScheduleId: String? = null

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private val audioPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val id = pendingScheduleId
        pendingScheduleId = null
        if (uri == null || id == null) return@registerForActivityResult
        runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        LocalTrackStore.put(this, id, uri, displayName(uri))
        schedules = CloudScheduleStore.loadAll(this)
        schedules.firstOrNull { it.id == id }?.let { ExactCloudAlarmScheduler.schedule(this, it) }
        status = "Music ready for ${schedules.firstOrNull { it.id == id }?.name ?: "schedule"}."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        exactAlarmReady = exactAlarmPermissionGranted()
        setContent { AppRoot() }
        syncCloud()
    }

    override fun onResume() { super.onResume(); exactAlarmReady = exactAlarmPermissionGranted() }

    private fun exactAlarmPermissionGranted() = Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    private fun requestExactAlarmPermission() { if (Build.VERSION.SDK_INT >= 31) startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))) }
    private fun chooseMusic(id: String) { pendingScheduleId = id; audioPicker.launch(arrayOf("audio/*")) }
    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) return it.getString(0) }
        return uri.lastPathSegment ?: "Local music"
    }

    private fun syncCloud() {
        status = "Syncing schedules…"
        CloudScheduleSync.sync(this) { result -> runOnUiThread {
            result.onSuccess { count -> schedules = CloudScheduleStore.loadAll(this); scheduleCount = count; ExactCloudAlarmScheduler.sync(this); status = if (count == 0) "No schedules yet." else "Schedules synced." }
                .onFailure { error -> schedules = CloudScheduleStore.loadAll(this); scheduleCount = schedules.size; status = "Offline: ${error.message ?: "sync failed"}" }
        }}
    }

    private fun createSchedule(name: String, date: String, time: String) {
        CloudScheduleSync.create(this, name, date, time, false, java.util.TimeZone.getDefault().id) { result -> runOnUiThread {
            result.onSuccess { syncCloud(); status = "Schedule created for ${formatDate(date)} at ${formatTime(time)}. Choose music to finish setup." }
                .onFailure { status = "Could not create schedule: ${it.message ?: "unknown error"}" }
        }}
    }

    private fun updateSchedule(schedule: CloudScheduleStore.Schedule, name: String, date: String, time: String, enabled: Boolean) {
        CloudScheduleSync.update(this, schedule.id, name, date, time, enabled, schedule.timezone) { result -> runOnUiThread {
            result.onSuccess {
                val updated = schedule.copy(name = name, scheduledDate = date, time = time, enabled = enabled)
                CloudScheduleStore.saveAll(this, CloudScheduleStore.loadAll(this).map { if (it.id == schedule.id) updated else it })
                if (enabled && LocalTrackStore.get(this, schedule.id) != null) ExactCloudAlarmScheduler.schedule(this, updated) else ExactCloudAlarmScheduler.cancel(this, schedule.id)
                schedules = CloudScheduleStore.loadAll(this); status = "Schedule updated for ${formatDate(date)} at ${formatTime(time)}."
            }.onFailure { status = "Could not update schedule: ${it.message ?: "unknown error"}" }
        }}
    }

    private fun setScheduleEnabled(schedule: CloudScheduleStore.Schedule, enabled: Boolean) {
        if (enabled && LocalTrackStore.get(this, schedule.id) == null) { status = "Choose music first for ${schedule.name}."; return }
        CloudScheduleSync.setEnabled(this, schedule.id, enabled) { result -> runOnUiThread {
            result.onSuccess { schedules = CloudScheduleStore.loadAll(this); schedules.firstOrNull { it.id == schedule.id }?.let { if (enabled) ExactCloudAlarmScheduler.schedule(this, it) else ExactCloudAlarmScheduler.cancel(this, it.id) }; status = if (enabled) "${schedule.name} is ON." else "${schedule.name} is OFF." }
                .onFailure { status = "Could not change schedule: ${it.message ?: "unknown error"}" }
        }}
    }

    private fun deleteSchedule(schedule: CloudScheduleStore.Schedule) {
        CloudScheduleSync.delete(this, schedule.id) { result -> runOnUiThread {
            result.onSuccess { schedules = CloudScheduleStore.loadAll(this); scheduleCount = schedules.size; status = "${schedule.name} deleted." }
                .onFailure { status = "Could not delete schedule: ${it.message ?: "unknown error"}" }
        }}
    }

    private fun formatDate(value: String?): String {
        if (value.isNullOrBlank()) return "Every day"
        return runCatching { LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd MMM yyyy")) }.getOrDefault(value)
    }

    private fun formatTime(value: String): String {
        val parts = value.take(5).split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return value.take(5)
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val suffix = if (hour >= 12) "PM" else "AM"
        return "%d:%02d %s".format(hour % 12, minute, suffix).replace("0:", "12:")
    }

    @Composable private fun AppRoot() {
        val prefs = getSharedPreferences("autoplay_prefs", MODE_PRIVATE)
        var showSplash by remember { mutableStateOf(true) }
        var showOnboarding by remember { mutableStateOf(!prefs.getBoolean("onboarding_complete", false)) }
        if (showSplash) { SplashScreen(); LaunchedEffect(Unit) { delay(1200); showSplash = false } }
        else if (showOnboarding) GetStartedScreen { prefs.edit().putBoolean("onboarding_complete", true).apply(); showOnboarding = false }
        else Home()
    }

    @Composable private fun BrandMark(size: Int = 88) { Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) { Surface(Modifier.fillMaxSize(), CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {}; Text("♫", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold) } }

    @Composable private fun SplashScreen() { Surface(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { BrandMark(104); Spacer(Modifier.height(22.dp)); Text("AutoPlay", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Spacer(Modifier.height(6.dp)); Text("Your music. Your time.") } } }

    @Composable private fun GetStartedScreen(onStarted: () -> Unit) { Surface(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { BrandMark(112); Spacer(Modifier.height(28.dp)); Text("Welcome to AutoPlay", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(10.dp)); Text("Create schedules on your phone or website, then let AutoPlay play your local music automatically."); Spacer(Modifier.height(24.dp)); Card(Modifier.fillMaxWidth(), RoundedCornerShape(20.dp)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { Text("Simple & ₹0", fontWeight = FontWeight.Bold); Text("• Shared CRUD between website and app"); Text("• Choose a date + exact time"); Text("• Music stored on your Android phone"); Text("• No Spotify subscription required") } }; Spacer(Modifier.height(28.dp)); Button(onStarted, Modifier.fillMaxWidth().height(54.dp)) { Text("Get Started", fontWeight = FontWeight.Bold) } } } }

    @Composable private fun Home() {
        var showCreate by remember { mutableStateOf(false) }
        var editing by remember { mutableStateOf<CloudScheduleStore.Schedule?>(null) }
        MaterialTheme { Surface(Modifier.fillMaxSize()) { LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Row(verticalAlignment = Alignment.CenterVertically) { BrandMark(52); Spacer(Modifier.size(12.dp)); Column { Text("AutoPlay", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text("Your music. Your time.") } } }
            item { Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("QUICK SETUP", fontWeight = FontWeight.Bold); Text(if (exactAlarmReady) "✓ Exact-time alarms are ready" else "1. Allow exact-time alarms"); Text("2. Create or sync a schedule"); Text("3. Choose local music and turn it ON"); if (!exactAlarmReady) Button({ requestExactAlarmPermission() }, Modifier.fillMaxWidth()) { Text("Allow exact-time alarms") } } } }
            item { Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Schedules", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("$scheduleCount schedule(s) • ${schedules.count { it.enabled }} ON") }; OutlinedButton({ showCreate = true }) { Text("+ Add") }; Spacer(Modifier.size(6.dp)); OutlinedButton({ syncCloud() }) { Text("Sync") } }; Divider(); Text(status) } } }
            if (schedules.isEmpty()) item { Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp)) { Column(Modifier.padding(20.dp)) { Text("No schedules yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("Tap + Add or create one on the website.") } } }
            items(schedules, key = { it.id }) { schedule ->
                val track = LocalTrackStore.get(this@CloudMainActivity, schedule.id)
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (schedule.enabled) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(schedule.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("📅 ${formatDate(schedule.scheduledDate)}", style = MaterialTheme.typography.titleMedium); Text("⏰ ${formatTime(schedule.time)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(if (schedule.enabled) "ON • will play automatically" else "OFF • not scheduled") }; Switch(checked = schedule.enabled, onCheckedChange = { setScheduleEnabled(schedule, it) }, enabled = schedule.enabled || track != null) }
                    Divider(); Text("Music", fontWeight = FontWeight.Bold); Text(track?.name ?: "No local music selected"); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button({ chooseMusic(schedule.id) }, Modifier.weight(1f)) { Text(if (track == null) "Choose music" else "Change music") }; OutlinedButton({ editing = schedule }, Modifier.weight(1f)) { Text("Edit") } }
                    OutlinedButton({ deleteSchedule(schedule) }, Modifier.fillMaxWidth()) { Text("Delete schedule") }
                } }
            }
            item { Spacer(Modifier.height(8.dp)); Text("₹0 local playback • Music stays on your phone • Website + app share date, time and schedules") }
        } } }

        if (showCreate) ScheduleDialog(title = "Create schedule", initial = null, onDismiss = { showCreate = false }) { name, date, time, _ -> showCreate = false; createSchedule(name, date, time) }
        editing?.let { schedule -> ScheduleDialog(title = "Edit schedule", initial = schedule, onDismiss = { editing = null }) { name, date, time, enabled -> editing = null; updateSchedule(schedule, name, date, time, enabled) } }
    }

    @Composable private fun ScheduleDialog(title: String, initial: CloudScheduleStore.Schedule?, onDismiss: () -> Unit, onSave: (String, String, String, Boolean) -> Unit) {
        var name by remember(initial) { mutableStateOf(initial?.name ?: "New schedule") }
        var date by remember(initial) { mutableStateOf(initial?.scheduledDate ?: LocalDate.now().toString()) }
        var time by remember(initial) { mutableStateOf(initial?.time?.take(5) ?: "07:00") }
        var enabled by remember(initial) { mutableStateOf(initial?.enabled ?: false) }
        AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
            OutlinedButton({
                val parsed = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
                DatePickerDialog(this@CloudMainActivity, { _, y, m, d -> date = "%04d-%02d-%02d".format(y, m + 1, d) }, parsed.year, parsed.monthValue - 1, parsed.dayOfMonth).show()
            }, Modifier.fillMaxWidth()) { Text("Date: ${formatDate(date)}") }
            OutlinedButton({
                val parts = time.split(":"); val hour = parts.getOrNull(0)?.toIntOrNull() ?: 7; val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                TimePickerDialog(this@CloudMainActivity, { _, h, m -> time = "%02d:%02d".format(h, m) }, hour, minute, false).show()
            }, Modifier.fillMaxWidth()) { Text("Time: ${formatTime(time)}") }
            if (initial != null) Row(verticalAlignment = Alignment.CenterVertically) { Text("Enabled", Modifier.weight(1f)); Switch(enabled, { enabled = it }) }
        } }, confirmButton = { Button(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), date, time, enabled) }) { Text("Save") } }, dismissButton = { OutlinedButton(onDismiss) { Text("Cancel") } })
    }
}
