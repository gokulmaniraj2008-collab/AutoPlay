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

class TaskActivity : ComponentActivity() {
    private var command by mutableStateOf("")
    private var status by mutableStateOf("Ready — give AutoPlay a phone task.")
    private var lastResult by mutableStateOf("")

    private val callPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status = if (granted) "Phone permission granted." else "Phone permission was not granted."
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
                status = "${parsed.message} Allow exact alarms first, then save the task again."
                requestExactAlarmPermission()
                return
            }
            if (TaskScheduler.schedule(this, task)) {
                status = "${parsed.message} AutoPlay will execute it automatically."
            } else status = "Could not schedule this task."
        } else {
            val result = TaskExecutor.execute(this, task)
            TaskHistoryStore.record(this, task, result)
            lastResult = TaskHistoryStore.last(this)
            status = result.message
        }
    }

    private fun exactAlarmReady(): Boolean = Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= 31) {
            startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
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
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("AutoPlay AI", style = MaterialTheme.typography.headlineLarge)
                    Text("Give your phone a task in plain English.")

                    OutlinedTextField(
                        value = command,
                        onValueChange = { command = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        label = { Text("Your task") },
                        placeholder = { Text("At 8 PM play a Tamil song on Spotify") }
                    )

                    Button(onClick = { submitTask() }, Modifier.fillMaxWidth()) {
                        Text("Run / Schedule Task")
                    }
                    OutlinedButton(onClick = { requestCallPermission() }, Modifier.fillMaxWidth()) {
                        Text("Allow phone calls")
                    }
                    OutlinedButton(onClick = { requestExactAlarmPermission() }, Modifier.fillMaxWidth()) {
                        Text("Allow exact-time automation")
                    }
                    OutlinedButton(onClick = { openSchedules() }, Modifier.fillMaxWidth()) {
                        Text("Open existing schedules")
                    }

                    Text(status, style = MaterialTheme.typography.bodyMedium)
                    Text("Last result", style = MaterialTheme.typography.titleMedium)
                    Text(lastResult.ifBlank { "No task executed yet." })
                    Text(
                        "Examples: “Open Spotify at 8 PM” • “Call +919876543210 at 6 PM” • “Play a Tamil song on Spotify now”",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "Last updated: ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}
