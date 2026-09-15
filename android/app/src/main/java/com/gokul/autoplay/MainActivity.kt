package com.gokul.autoplay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var page by mutableStateOf(1)
    private var tommyReady by mutableStateOf(false)
    private var voiceListening by mutableStateOf(false)
    private var voiceStatus by mutableStateOf("Tap the mic to speak")
    private var input by mutableStateOf("")
    private var geminiBusy by mutableStateOf(false)
    private var speechRecognizer: SpeechRecognizer? = null

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startVoiceRecognition()
        else voiceStatus = "Microphone permission is required for voice input"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            when (page) {
                1 -> Page1TommyHome(
                    tommyReady = tommyReady,
                    onStart = { tommyReady = true },
                    onChat = { page = 2 }
                )
                2 -> Page2TommyChat(
                    onBack = { stopVoiceRecognition(); page = 1 },
                    voiceListening = voiceListening,
                    voiceStatus = voiceStatus,
                    input = input,
                    geminiBusy = geminiBusy,
                    onInputChange = { input = it },
                    onMic = ::toggleVoiceRecognition,
                    onSend = ::sendToGemini
                )
            }
        }
    }

    private fun toggleVoiceRecognition() {
        if (voiceListening) {
            stopVoiceRecognition()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            voiceStatus = "Voice recognition is not available on this phone"
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            startVoiceRecognition()
        }
    }

    private fun startVoiceRecognition() {
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).also { recognizer ->
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    voiceListening = true
                    voiceStatus = "Listening…"
                }
                override fun onBeginningOfSpeech() { voiceStatus = "Listening…" }
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() {
                    voiceListening = false
                    voiceStatus = "Processing…"
                }
                override fun onError(error: Int) {
                    voiceListening = false
                    voiceStatus = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> "I didn't catch that. Tap the mic and try again."
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Tap the mic and try again."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required"
                        else -> "Voice input stopped. Tap the mic to try again."
                    }
                }
                override fun onResults(results: Bundle?) {
                    voiceListening = false
                    val spoken = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (spoken.isNotEmpty()) {
                        input = spoken
                        voiceStatus = "Voice text ready"
                    } else {
                        voiceStatus = "No words detected"
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val spoken = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (spoken.isNotEmpty()) input = spoken
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            }
            recognizer.startListening(intent)
        }
    }

    private fun stopVoiceRecognition() {
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
        speechRecognizer = null
        voiceListening = false
        if (voiceStatus == "Listening…" || voiceStatus == "Processing…") {
            voiceStatus = "Tap the mic to speak"
        }
    }

    private fun sendToGemini(messages: MutableList<String>) {
        val text = input.trim()
        if (text.isEmpty() || geminiBusy) return

        messages.add("You: $text")
        input = ""
        voiceStatus = "Tommy is thinking…"
        geminiBusy = true

        Thread {
            try {
                val connection = (URL("https://auto-play-4qkv.vercel.app/api/tommy-gemini").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 10000
                    readTimeout = 20000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                val body = JSONObject().put("text", text).toString()
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val responseText = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()
                val responseCode = connection.responseCode
                connection.disconnect()

                runOnUiThread {
                    geminiBusy = false
                    if (responseCode in 200..299) {
                        val json = JSONObject(responseText)
                        val reply = json.optString("reply").ifBlank { "I understood: $text" }
                        val action = json.optString("action").ifBlank { "none" }
                        messages.add("Tommy: $reply")
                        messages.add("Action: $action")
                        voiceStatus = "Gemini ready"
                    } else {
                        messages.add("Tommy: Gemini request failed. Check the server and internet connection.")
                        voiceStatus = "Gemini unavailable"
                    }
                }
            } catch (error: Exception) {
                runOnUiThread {
                    geminiBusy = false
                    messages.add("Tommy: I couldn't reach Gemini right now.")
                    voiceStatus = "Gemini unavailable"
                }
            }
        }.start()
    }

    override fun onDestroy() {
        stopVoiceRecognition()
        super.onDestroy()
    }
}

@Composable
private fun Page1TommyHome(
    tommyReady: Boolean,
    onStart: () -> Unit,
    onChat: () -> Unit
) {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 18.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text("TOMMY", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                Text("Your personal Android AI assistant", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Card(Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = if (tommyReady) "Tommy is ready" else "Hey, I'm Tommy",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (tommyReady) {
                                "Page 1 is ready. Feature 3 Gemini command understanding is now added."
                            } else {
                                "Clean starter build. Tommy Chat now includes voice input and Gemini understanding."
                            }
                        )
                        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                            Text(if (tommyReady) "Tommy Ready" else "Start Tommy")
                        }
                    }
                }
                Button(onClick = onChat, modifier = Modifier.fillMaxWidth()) { Text("Open Tommy Chat") }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("PAGE 1", fontWeight = FontWeight.Bold)
                        Text("Tommy Home")
                        Text("Features are added and verified one by one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun Page2TommyChat(
    onBack: () -> Unit,
    voiceListening: Boolean,
    voiceStatus: String,
    input: String,
    geminiBusy: Boolean,
    onInputChange: (String) -> Unit,
    onMic: () -> Unit,
    onSend: (MutableList<String>) -> Unit
) {
    val messages = androidx.compose.runtime.remember {
        mutableStateListOf("Tommy: Chat is ready. Feature 3 adds Gemini command understanding.")
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Tommy Chat", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Button(onClick = onBack) { Text("Back") }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { message ->
                        Card(Modifier.fillMaxWidth()) { Text(message, Modifier.padding(14.dp)) }
                    }
                }
                Text(
                    text = if (geminiBusy) "Tommy is thinking with Gemini…" else voiceStatus,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = onInputChange,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type to Tommy…") },
                        singleLine = true
                    )
                    Button(onClick = onMic, enabled = !geminiBusy) { Text(if (voiceListening) "Stop" else "🎤") }
                    Button(onClick = { onSend(messages) }, enabled = !geminiBusy) { Text("Send") }
                }
            }
        }
    }
}
