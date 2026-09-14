package com.gokul.autoplay

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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

/** Unified AutoPlay hub for AI phone tasks, voice commands, scheduling and explicit phone access. */
class TaskActivity : ComponentActivity() {
    private var command by mutableStateOf("")
    private var status by mutableStateOf("Ready — tell AutoPlay what to do on your phone.")
    private var lastResult by mutableStateOf("")
    private var listening by mutableStateOf(false)
    private var speechRecognizer: SpeechRecognizer? = null

    private val callPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        status = if (granted) "Phone-call permission granted." else "Phone-call permission was not granted."
    }

    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startVoiceInput() else status = "Microphone permission is required for voice commands."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lastResult = TaskHistoryStore.last(this)
        setContent { Screen() }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
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
            status = if (TaskScheduler.schedule(this, task)) "${parsed.message} AutoPlay will execute it automatically." else "Could not schedule this task."
        } else {
            val result = TaskExecutor.execute(this, task)
            TaskHistoryStore.record(this, task, result)
            lastResult = TaskHistoryStore.last(this)
            status = result.message
        }
        command = ""
    }

    private fun startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status = "No speech recognition service is available on this phone."
            return
        }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).also { recognizer ->
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { listening = true; status = "Listening…" }
                override fun onBeginningOfSpeech() { status = "Listening…" }
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() { listening = false; status = "Processing command…" }
                override fun onError(error: Int) { listening = false; status = "Voice input failed (code $error). Try again." }
                override fun onResults(results: Bundle?) {
                    listening = false
                    val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (spoken.isBlank()) status = "I didn't catch that."
                    else { command = spoken; status = "Heard: $spoken"; submitTask() }
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a phone task")
            }
            recognizer.startListening(intent)
        }
    }

    private fun requestVoiceCommand() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVoiceInput()
        else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun exactAlarmReady(): Boolean = Build.VERSION.SDK_INT < 31 || getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= 31) startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
    }

    private fun requestCallPermission() { callPermission.launch(Manifest.permission.CALL_PHONE) }

    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        status = "In Android Settings, enable AutoPlay's Phone Automation service if it is shown."
    }

    private fun openSchedules() { startActivity(Intent(this, CloudMainActivity::class.java)) }

    @androidx.compose.runtime.Composable
    private fun Screen() {
        MaterialTheme {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("AutoPlay", style = MaterialTheme.typography.headlineLarge)
                    Text("Your AI phone automation hub", style = MaterialTheme.typography.titleMedium)
                    Text("Give one command. AutoPlay understands it, runs it now, or schedules it for later.")

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("AI Phone Task", style = MaterialTheme.typography.titleLarge)
                            OutlinedTextField(value = command, onValueChange = { command = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, label = { Text("What should your phone do?") }, placeholder = { Text("Open WhatsApp and send Praneesh Hi") })
                            Button(onClick = { submitTask() }, modifier = Modifier.fillMaxWidth()) { Text("Run / Schedule") }
                            Button(onClick = { requestVoiceCommand() }, modifier = Modifier.fillMaxWidth(), enabled = !listening) { Text(if (listening) "Listening…" else "🎙 Speak Task") }
                        }
                    }

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Phone Access", style = MaterialTheme.typography.titleLarge)
                            Text("AutoPlay uses Android capabilities only after you explicitly authorize them.")
                            OutlinedButton(onClick = { openAccessibilitySettings() }, modifier = Modifier.fillMaxWidth()) { Text("Enable Phone Automation") }
                            OutlinedButton(onClick = { requestCallPermission() }, modifier = Modifier.fillMaxWidth()) { Text("Allow phone calls") }
                            OutlinedButton(onClick = { requestExactAlarmPermission() }, modifier = Modifier.fillMaxWidth()) { Text("Allow exact-time automation") }
                        }
                    }

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Examples", style = MaterialTheme.typography.titleLarge)
                            Text("• Open WhatsApp and send Praneesh Hi")
                            Text("• Open Instagram")
                            Text("• Change my Instagram bio to: Agricultural Engineer 🚜")
                            Text("• Open YouTube at 8 PM")
                            Text("• Call +919876543210")
                        }
                    }

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Status", style = MaterialTheme.typography.titleLarge)
                            Text(status)
                            Text("Last result", style = MaterialTheme.typography.titleMedium)
                            Text(lastResult.ifBlank { "No task executed yet." })
                            Text("Updated ${SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date())}", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("Existing AutoPlay", style = MaterialTheme.typography.titleLarge)
                            Text("Your existing cloud/local music schedules remain available in the same app.")
                            OutlinedButton(onClick = { openSchedules() }, modifier = Modifier.fillMaxWidth()) { Text("Open schedules") }
                        }
                    }
                }
            }
        }
    }
}
