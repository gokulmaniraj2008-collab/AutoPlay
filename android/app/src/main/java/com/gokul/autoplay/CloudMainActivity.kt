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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    override fun onResume() {
        super.onResume()
        exactAlarmReady = exactAlarmPermissionGranted()
    }

    private fun exactAlarmPermissionGranted() = Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
        }
    }

    private fun chooseMusic(id: String) {
        pendingScheduleId = id
        audioPicker.launch(arrayOf("audio/*"))
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return uri.lastPathSegment ?: "Local music"
    }

    private fun syncCloud() {
        status = "Syncing schedules…"
        CloudScheduleSync.sync(this) { result ->
            runOnUiThread {
                result.onSuccess { count ->
                    schedules = CloudScheduleStore.loadAll(this)
                    scheduleCount = count
                    ExactCloudAlarmScheduler.sync(this)
                    status = if (count == 0) "No schedules yet." else "Schedules synced."
                }.onFailure { error ->
                    schedules = CloudScheduleStore.loadAll(this)
                    scheduleCount = schedules.size
                    status = "Offline: ${error.message ?: "sync failed"}"
                }
            }
        }
    }

    private fun createSchedule(name: String, date: String, time: String) {
        CloudScheduleSync.create(this, name, date, time, false, java.util.TimeZone.getDefault().id) { result ->
            runOnUiThread {
                result.onSuccess {
                    syncCloud()
                    status = "Schedule created for ${formatDate(date)} at ${formatTime(time)}. Choose music to finish setup."
                }.onFailure {
                    status = "Could not create schedule: ${it.message ?: "unknown error"}"
                }
            }
        }
    }

    private fun updateSchedule(schedule: CloudScheduleStore.Schedule, name: String, date: String, time: String, enabled: Boolean) {
        CloudScheduleSync.update(this, schedule.id, name, date, time, enabled, schedule.timezone) { result ->
            runOnUiThread {
                result.onSuccess {
                    val updated = schedule.copy(name = name, scheduledDate = date, time = time, enabled = enabled)
                    CloudScheduleStore.saveAll(
                        this,
                        CloudScheduleStore.loadAll(this).map { if (it.id == schedule.id) updated else it }
                    )
                    if (enabled && LocalTrackStore.get(this, schedule.id) != null) {
                        ExactCloudAlarmScheduler.schedule(this, updated)
                    } else {
                        ExactCloudAlarmScheduler.cancel(this, schedule.id)
                    }
                    schedules = CloudScheduleStore.loadAll(this)
                    status = "Schedule updated for ${formatDate(date)} at ${formatTime(time)}."
                }.onFailure {
                    status = "Could not update schedule: ${it.message ?: "unknown error"}"
                }
            }
        }
    }

    private fun setScheduleEnabled(schedule: CloudScheduleStore.Schedule, enabled: Boolean) {
        if (enabled && LocalTrackStore.get(this, schedule.id) == null) {
            status = "Choose music first for ${schedule.name}."
            return
        }
        CloudScheduleSync.setEnabled(this, schedule.id, enabled) { result ->
            runOnUiThread {
                result.onSuccess {
                    schedules = CloudScheduleStore.loadAll(this)
                    schedules.firstOrNull { it.id == schedule.id }?.let {
                        if (enabled) ExactCloudAlarmScheduler.schedule(this, it)
                        else ExactCloudAlarmScheduler.cancel(this, it.id)
                    }
                    status = if (enabled) "${schedule.name} is ON." else "${schedule.name} is OFF."
                }.onFailure {
                    status = "Could not change schedule: ${it.message ?: "unknown error"}"
                }
            }
        }
    }

    private fun deleteSchedule(schedule: CloudScheduleStore.Schedule) {
        CloudScheduleSync.delete(this, schedule.id) { result ->
            runOnUiThread {
                result.onSuccess {
                    schedules = CloudScheduleStore.loadAll(this)
                    scheduleCount = schedules.size
                    status = "${schedule.name} deleted."
                }.onFailure {
                    status = "Could not delete schedule: ${it.message ?: "unknown error"}"
                }
            }
        }
    }

    private fun formatDate(value: String?): String {
        if (value.isNullOrBlank()) return "Every day"
        return runCatching {
            LocalDate.parse(value).format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
        }.getOrDefault(value)
    }

    private fun formatTime(value: String): String {
        val parts = value.take(5).split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return value.take(5)
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val suffix = if (hour >= 12) "PM" else "AM"
        val displayHour = when (hour % 12) { 0 -> 12; else -> hour % 12 }
        return "%d:%02d %s".format(displayHour, minute, suffix)
    }

    @Composable
    private fun AppRoot() {
        val prefs = getSharedPreferences("autoplay_prefs", MODE_PRIVATE)
        var showSplash by remember { mutableStateOf(true) }
        var showOnboarding by remember { mutableStateOf(!prefs.getBoolean("onboarding_complete", false)) }
        if (showSplash) {
            SplashScreen()
            LaunchedEffect(Unit) {
                delay(900)
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

    @Composable
    private fun BrandMark(size: Int = 88) {
        Box(Modifier.size(size.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.fillMaxSize(), CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {}
            Text("♫", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
        }
    }

    @Composable
    private fun SplashScreen() {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                BrandMark(96)
                Spacer(Modifier.height(18.dp))
                Text("AutoPlay", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Your music. Your time.")
            }
        }
    }

    @Composable
    private fun GetStartedScreen(onStarted: () -> Unit) {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                BrandMark(104)
                Spacer(Modifier.height(22.dp))
                Text("Welcome to AutoPlay", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Create schedules on your phone or website, then let AutoPlay play your local music automatically.",
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(20.dp))
                Card(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("Simple & ₹0", fontWeight = FontWeight.Bold)
                        Text("• Shared schedules between website and app")
                        Text("• Choose a date + exact time")
                        Text("• Music stays on your Android phone")
                        Text("• No Spotify subscription required")
                    }
                }
                Spacer(Modifier.height(22.dp))
                Button(onStarted, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(26.dp)) {
                    Text("Get Started", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    @Composable
    private fun Home() {
        var showCreate by remember { mutableStateOf(false) }
        var editing by remember { mutableStateOf<CloudScheduleStore.Schedule?>(null) }

        MaterialTheme {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp).padding(top = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BrandMark(46)
                            Spacer(Modifier.size(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                Text("AutoPlay", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Text("Your music. Your time.", fontSize = 12.sp)
                            }
                            Text("♫", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    item {
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("QUICK SETUP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                SetupRow("1", if (exactAlarmReady) "Exact-time alarms are ready" else "Allow exact-time alarms")
                                SetupRow("2", "Create or sync a schedule")
                                SetupRow("3", "Choose local music and turn it ON")
                                if (!exactAlarmReady) {
                                    Button(
                                        onClick = { requestExactAlarmPermission() },
                                        Modifier.fillMaxWidth().height(46.dp),
                                        shape = RoundedCornerShape(23.dp)
                                    ) { Text("Allow exact-time alarms", fontWeight = FontWeight.SemiBold) }
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("Schedules", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                        Text("$scheduleCount schedules  •  ${schedules.count { it.enabled }} ON", fontSize = 12.sp)
                                    }
                                    Text("${schedules.count { it.enabled }}/$scheduleCount", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { showCreate = true },
                                        Modifier.weight(1f).height(42.dp),
                                        shape = RoundedCornerShape(21.dp)
                                    ) { Text("+ Add", fontSize = 13.sp) }
                                    OutlinedButton(
                                        onClick = { syncCloud() },
                                        Modifier.weight(1f).height(42.dp),
                                        shape = RoundedCornerShape(21.dp)
                                    ) { Text("Sync", fontSize = 13.sp) }
                                }
                                Divider()
                                Text(status, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    if (schedules.isEmpty()) {
                        item {
                            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
                                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text("No schedules yet", fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                    Text("Tap + Add or create one on the website.", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    items(schedules, key = { it.id }) { schedule ->
                        ScheduleCard(schedule) { editing = schedule }
                    }

                    item {
                        Text(
                            "₹0 local playback  •  Music stays on your phone",
                            Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        if (showCreate) {
            ScheduleDialog("Create schedule", null, { showCreate = false }) { name, date, time, _ ->
                showCreate = false
                createSchedule(name, date, time)
            }
        }
        editing?.let { schedule ->
            ScheduleDialog("Edit schedule", schedule, { editing = null }) { name, date, time, enabled ->
                editing = null
                updateSchedule(schedule, name, date, time, enabled)
            }
        }
    }

    @Composable
    private fun SetupRow(number: String, text: String) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(24.dp), CircleShape, color = MaterialTheme.colorScheme.surface) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(number, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.size(10.dp))
            Text(text, fontSize = 13.sp)
        }
    }

    @Composable
    private fun ScheduleCard(schedule: CloudScheduleStore.Schedule, onEdit: () -> Unit) {
        val track = LocalTrackStore.get(this@CloudMainActivity, schedule.id)
        Card(
            Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(schedule.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatTime(schedule.time), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Text(formatDate(schedule.scheduledDate), fontSize = 12.sp)
                        Text(
                            if (schedule.enabled) "ON  •  Will play automatically" else "OFF  •  Not scheduled",
                            fontSize = 11.sp
                        )
                    }
                    Switch(
                        checked = schedule.enabled,
                        onCheckedChange = { setScheduleEnabled(schedule, it) },
                        enabled = schedule.enabled || track != null
                    )
                }

                Divider()

                Surface(
                    Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("♫", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("Music", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(track?.name ?: "No local music selected", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { chooseMusic(schedule.id) },
                        Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(21.dp)
                    ) {
                        Text(if (track == null) "Choose music" else "Change music", fontSize = 12.sp, textAlign = TextAlign.Center)
                    }
                    OutlinedButton(
                        onClick = onEdit,
                        Modifier.weight(1f).height(42.dp),
                        shape = RoundedCornerShape(21.dp)
                    ) { Text("Edit", fontSize = 12.sp) }
                }

                OutlinedButton(
                    onClick = { deleteSchedule(schedule) },
                    Modifier.fillMaxWidth().height(38.dp),
                    shape = RoundedCornerShape(19.dp)
                ) { Text("Delete schedule", fontSize = 11.sp) }
            }
        }
    }

    @Composable
    private fun ScheduleDialog(
        title: String,
        initial: CloudScheduleStore.Schedule?,
        onDismiss: () -> Unit,
        onSave: (String, String, String, Boolean) -> Unit
    ) {
        var name by remember(initial) { mutableStateOf(initial?.name ?: "New schedule") }
        var date by remember(initial) { mutableStateOf(initial?.scheduledDate ?: LocalDate.now().toString()) }
        var time by remember(initial) { mutableStateOf(initial?.time?.take(5) ?: "07:00") }
        var enabled by remember(initial) { mutableStateOf(initial?.enabled ?: false) }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        Modifier.fillMaxWidth(),
                        label = { Text("Schedule name") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    OutlinedButton(
                        onClick = {
                            val parsed = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
                            DatePickerDialog(this@CloudMainActivity, { _, y, m, d -> date = "%04d-%02d-%02d".format(y, m + 1, d) }, parsed.year, parsed.monthValue - 1, parsed.dayOfMonth).show()
                        },
                        Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Date   ${formatDate(date)}", Modifier.fillMaxWidth(), textAlign = TextAlign.Start, fontSize = 13.sp) }
                    OutlinedButton(
                        onClick = {
                            val parts = time.split(":")
                            val hour = parts.getOrNull(0)?.toIntOrNull() ?: 7
                            val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                            TimePickerDialog(this@CloudMainActivity, { _, h, m -> time = "%02d:%02d".format(h, m) }, hour, minute, false).show()
                        },
                        Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Time   ${formatTime(time)}", Modifier.fillMaxWidth(), textAlign = TextAlign.Start, fontSize = 13.sp) }
                    if (initial != null) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Enable schedule", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (enabled) "Will play automatically" else "Will stay off", fontSize = 11.sp)
                            }
                            Switch(checked = enabled, onCheckedChange = { enabled = it })
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = name.isNotBlank(),
                    onClick = { onSave(name.trim(), date, time.take(5), enabled) },
                    shape = RoundedCornerShape(20.dp)
                ) { Text("Save", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(20.dp)) { Text("Cancel") }
            }
        )
    }
}
