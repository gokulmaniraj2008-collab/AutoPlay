package com.gokul.autoplay

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class TommyMessage(val fromTommy: Boolean, val text: String)

@Composable
fun TommyChatPage() {
    val context = LocalContext.current
    val messages = remember {
        mutableStateListOf(
            TommyMessage(true, "Hi! I'm Tommy. Try: Open Instagram, go to Reels, scroll, like, follow, or open comments.")
        )
    }
    var input by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var voiceStatus by remember { mutableStateOf("Tommy is OFF") }
    val listState = rememberLazyListState()
    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null
    }

    DisposableEffect(context) {
        var liveTommyMessageIndex = -1

        fun addTommyMessage(text: String) {
            if (text.isBlank()) return
            if (messages.lastOrNull()?.fromTommy == true && messages.lastOrNull()?.text == text) return
            messages.add(TommyMessage(true, text))
            liveTommyMessageIndex = -1
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != TommyStatusEvents.ACTION) return

                val status = intent.getStringExtra(TommyStatusEvents.EXTRA_STATUS).orEmpty()
                val text = intent.getStringExtra(TommyStatusEvents.EXTRA_TEXT).orEmpty()
                if (text.isBlank()) return

                when (status) {
                    TommyStatusEvents.LISTENING -> {
                        voiceStatus = "🎤 $text"
                        if (text.startsWith("Tommy heard:")) {
                            val index = liveTommyMessageIndex
                            if (index >= 0 && index < messages.size && messages[index].fromTommy) {
                                messages[index] = TommyMessage(true, "🎙️ $text")
                            } else {
                                messages.add(TommyMessage(true, "🎙️ $text"))
                                liveTommyMessageIndex = messages.lastIndex
                            }
                        } else {
                            addTommyMessage("🎤 $text")
                            liveTommyMessageIndex = messages.lastIndex
                        }
                    }
                    TommyStatusEvents.HEARD -> {
                        voiceStatus = "Command received"
                        addTommyMessage("🎙️ $text")
                    }
                    TommyStatusEvents.WORKING -> {
                        voiceStatus = "⚙️ $text"
                        addTommyMessage("⚙️ $text")
                    }
                    TommyStatusEvents.MESSAGE -> {
                        voiceStatus = "Tommy replied"
                        addTommyMessage("Tommy: $text")
                    }
                    TommyStatusEvents.ON -> {
                        voiceStatus = "◉ $text"
                        if (messages.lastOrNull()?.text != "◉ $text") addTommyMessage("◉ $text")
                    }
                    TommyStatusEvents.OFF -> {
                        voiceStatus = "○ $text"
                        if (messages.lastOrNull()?.text != "○ $text") addTommyMessage("○ $text")
                    }
                }
            }
        }

        val filter = IntentFilter(TommyStatusEvents.ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    DisposableEffect(speechRecognizer) {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) {
                isListening = true
                voiceStatus = "Listening… speak now"
            }
            override fun onBeginningOfSpeech() { voiceStatus = "Listening…" }
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { isListening = false; voiceStatus = "Processing…" }
            override fun onError(error: Int) {
                isListening = false
                voiceStatus = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Didn't catch that — tap 🎤 and try again"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required"
                    else -> "Voice input stopped — tap 🎤 to try again"
                }
            }
            override fun onResults(results: android.os.Bundle?) {
                isListening = false
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                if (spoken.isNotEmpty()) {
                    input = spoken
                    voiceStatus = "Command ready"
                    sendCommand(context, messages, spoken)
                    input = ""
                    voiceStatus = "Command executed"
                } else voiceStatus = "No speech detected"
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) {
                val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                if (partial.isNotBlank()) input = partial
            }
            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
        })
        onDispose { speechRecognizer?.destroy() }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun startVoiceInput() {
        if (speechRecognizer == null) {
            voiceStatus = "Speech recognition is unavailable on this phone"
            return
        }
        val activity = context as? Activity
        if (activity != null && activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 4101)
            voiceStatus = "Allow microphone access, then tap 🎤 again"
            return
        }
        input = ""
        voiceStatus = "Starting microphone…"
        speechRecognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        })
    }

    fun sendMessage() {
        val command = input.trim()
        if (command.isNotEmpty()) {
            sendCommand(context, messages, command)
            input = ""
            voiceStatus = "Tommy replied"
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Tommy Chat", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Speak commands naturally. Tommy keeps listening for the next action.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { ChatBubble(it) }
        }
        Text(voiceStatus, color = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message Tommy…") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = { if (isListening) speechRecognizer?.stopListening() else startVoiceInput() },
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.height(56.dp)
            ) { Text(if (isListening) "■" else "🎤") }
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = { sendMessage() },
                enabled = input.trim().isNotEmpty(),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.height(56.dp)
            ) { Text("Send") }
        }
    }
}

private fun sendCommand(context: Context, messages: MutableList<TommyMessage>, command: String) {
    messages.add(TommyMessage(false, command))
    messages.add(TommyMessage(true, executeTommyChatCommand(context, command)))
}

@Composable
private fun ChatBubble(message: TommyMessage) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.fromTommy) Alignment.Start else Alignment.End
    ) {
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (message.fromTommy) Color(0xFFEFF5FF) else MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Text(message.text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp))
        }
    }
}

private fun executeTommyChatCommand(context: Context, rawCommand: String): String {
    val command = rawCommand.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
    return when {
        command.contains("instagram") && (command.contains("open") || command.contains("start") || command.contains("launch")) -> {
            launchApp(context, "com.instagram.android", "https://www.instagram.com")
            "OK, opening Instagram full screen. Say 'go reels page' next."
        }
        command.contains("instagram") -> {
            launchApp(context, "com.instagram.android", "https://www.instagram.com")
            "OK, Instagram opened."
        }
        (command.contains("reel") || command.contains("reels")) && (command.contains("go") || command.contains("open") || command.contains("show") || command.contains("page")) -> {
            if (tapAccessibilityCommand("OPEN_INSTAGRAM_REELS")) "OK, opening Instagram Reels." else accessibilityRequired()
        }
        (command.contains("scroll") || command.contains("swipe") || command.contains("next")) && (command.contains("reel") || command.contains("instagram") || command.contains("down")) -> {
            if (tapAccessibilityCommand("SCROLL_REEL")) "OK, scrolling to the next reel." else accessibilityRequired()
        }
        command.contains("like") && (command.contains("reel") || command.contains("instagram") || command.contains("this")) -> {
            if (tapAccessibilityCommand("LIKE_REEL")) "OK, liked this Reel." else accessibilityRequired()
        }
        command.contains("follow") && (command.contains("account") || command.contains("user") || command.contains("this") || command.contains("instagram")) -> {
            if (tapAccessibilityCommand("FOLLOW_ACCOUNT")) "OK, following this account." else accessibilityRequired()
        }
        (command.contains("comment") || command.contains("comments")) && (command.contains("open") || command.contains("show") || command.contains("view") || command.contains("go") || command.contains("this")) -> {
            if (tapAccessibilityCommand("OPEN_COMMENTS")) "OK, opening comments." else accessibilityRequired()
        }
        command.contains("youtube") -> { launchApp(context, "com.google.android.youtube", "https://www.youtube.com"); "OK, opening YouTube." }
        command.contains("google") -> { launchApp(context, "com.google.android.googlequicksearchbox", "https://www.google.com"); "OK, opening Google." }
        command.contains("whatsapp") -> { launchApp(context, "com.whatsapp", "https://www.whatsapp.com"); "OK, opening WhatsApp." }
        command == "hi" || command == "hello" || command.contains("hello tommy") -> "OK, I'm here."
        command.contains("help") -> "Try: Open Instagram, go to Reels, scroll, like, follow, or open comments."
        else -> "I received: \"$rawCommand\". I don't have an action for that yet."
    }
}

private fun accessibilityRequired(): String = "Command received. Enable Tommy Accessibility so I can control the Instagram screen."
private fun tapAccessibilityCommand(action: String): Boolean = TommyAccessibilityService.performTommyAction(action)

private fun launchApp(context: Context, packageName: String, fallbackUrl: String) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
    try {
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        } else {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
        }
    } catch (_: Exception) { }
}
