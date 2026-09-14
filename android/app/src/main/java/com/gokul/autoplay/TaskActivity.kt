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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Unified AutoPlay hub: AI phone tasks, scheduled automation, permissions,
 * Spotify entry point, calls, app launching, and the existing cloud schedules
 * all remain inside the same AutoPlay application.
 */
class TaskActivity : ComponentActivity() {
    private var command by mutableStateOf("")
    private var status by mutableStateOf("Ready — tell AutoPlay what to do on your phone.")
    private var lastResult by mutableStateOf("")

    private val callPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status = if (granted) "Phone-call permission granted." else "Phone-call permission was not granted."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lastResult = TaskHistoryStore.last(this)
        setContent { Screen() }
    }

    private fun submitTask() {
        val parsed = TaskParser.parse(command)
        val task = parsed.task ?: run { status = parsed.message; return }

        TaskStore.upsert(this, task)
        val scheduled = task.scheduledAtMillis != null
        if (scheduled) {
            if (!exactAlarmReady()) {
                status = "${parsed.message} Allow exact alarms first, then submit the task again."
                requestExactAlarmPermission()
                return
            }
            status = if (TaskScheduler.schedule(this, task)) {
                "${parsed.message} AutoPlay will execute it automatically."
            } else {
                "Could not schedule this task."
            }
        } else {
            val result = TaskExecutor.execute(this, task)
            TaskHistoryStore.record(this, task, result)
            lastResult = TaskHistoryStore.last(this)
            status = result.message
        }
        command = ""
    }

    private fun exactAlarmReady(): Boolean =
        Build.VERSION.SDK_INT < 31 ||
            getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:$packageName")
                )
            )
        }
    }

    private fun requestCallPermission() {
        callPermission.launch(Manifest.permission.CALL_PHONE)
    }

    private fun openSchedules() {
        startActivity(Intent(this, CloudMainActivity::class.java))
    }

    @androidx.compose.runtime.Composable
    private fun Screen() {
        MaterialTheme {
            Surface(Modifier.fillMaxSize()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("AutoPlay", style = MaterialTheme.typography.headlineLarge)
                    Text(
                        "Your AI phone automation hub",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "Give one command. AutoPlay understands it, runs it now, or schedules it for later."
                    )

                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("AI Phone Task", style = MaterialTheme.typography.titleLarge)
                            OutlinedTextField(
                                value = command,
                                onValueChange = { command = it },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                label = { Text("What should your phone do?") },
                                placeholder = { Text("At 8 PM play a Tamil song on Spotify") }
                            )
                            Button(
                                onClick = { submitTask() },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Run / Schedule")
                            }
                        }
                    }

                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Phone Access", style = MaterialTheme.typography.titleLarge)
                            Text("AutoPlay only uses Android capabilities you explicitly authorize.")
                            OutlinedButton(
                                onClick = { requestCallPermission() },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Allow phone calls") }
                            OutlinedButton(
                                onClick = { requestExactAlarmPermission() },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Allow exact-time automation") }
                        }
                    }

                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("Existing AutoPlay", style = MaterialTheme.typography.titleLarge)
                            Text("Your existing cloud/local music schedules remain available in the same app.")
                            OutlinedButton(
                                onClick = { openSchedules() },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Open schedules") }
                        }
                    }

                    Card(Modifier.fillMaxWidth()) {
                        Column(
                            Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Status", style = MaterialTheme.typography.titleLarge)
                            Text(status)
                            Text("Last result", style = MaterialTheme.typography.titleMedium)
                            Text(lastResult.ifBlank { "No task executed yet." })
                            Text(
                                "Examples: “Open Spotify at 8 PM” • “Call +919876543210 at 6 PM” • “Open YouTube”",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Updated ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}
