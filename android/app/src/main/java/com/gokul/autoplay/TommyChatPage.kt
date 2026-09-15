package com.gokul.autoplay

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
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
import com.gokul.autoplay.skills.TommySkillEngine

private data class TommyMessage(val fromTommy: Boolean, val text: String)

@Composable
fun TommyChatPage() {
    val context = LocalContext.current
    val messages = remember {
        mutableStateListOf(
            TommyMessage(
                true,
                "Hi! I'm Tommy. Try: Open Instagram, go to Reels, scroll, like, follow, open comments, YouTube, Google, or WhatsApp."
            )
        )
    }
    var input by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var voiceStatus by remember { mutableStateOf("Tommy is OFF") }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        TommySkillEngine.initialize()
    }

    DisposableEffect(context) {
        fun addTommyMessage(text: String) {
            if (text.isBlank()) return
            if (messages.lastOrNull()?.fromTommy == true && messages.lastOrNull()?.text == text) return
            messages.add(TommyMessage(true, text))
        }

        val statusReceiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != TommyStatusEvents.ACTION) return
                val status = intent.getStringExtra(TommyStatusEvents.EXTRA_STATUS).orEmpty()
                val text = intent.getStringExtra(TommyStatusEvents.EXTRA_TEXT).orEmpty()
                if (text.isBlank()) return
                when (status) {
                    TommyStatusEvents.LISTENING -> {
                        voiceStatus = "🎤 $text"
                        addTommyMessage("🎤 $text")
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
                        addTommyMessage("◉ $text")
                    }
                    TommyStatusEvents.OFF -> {
                        voiceStatus = "○ $text"
                        addTommyMessage("○ $text")
                    }
                }
            }
        }

        val voiceListener = object : TommyVoiceController.Listener {
            override fun onStateChanged(state: TommyVoiceController.State, message: String) {
                when (state) {
                    TommyVoiceController.State.LISTENING -> {
                        isListening = true
                        voiceStatus = "🎤 $message"
                    }
                    TommyVoiceController.State.PROCESSING -> {
                        isListening = false
                        voiceStatus = "⚙️ $message"
                    }
                    TommyVoiceController.State.ERROR -> {
                        isListening = false
                        voiceStatus = message
                    }
                    TommyVoiceController.State.IDLE -> {
                        isListening = false
                        voiceStatus = message
                    }
                }
            }

            override fun onPartialText(text: String) {
                input = text
                voiceStatus = "🎙️ Tommy heard: $text"
            }

            override fun onFinalText(text: String, source: TommyVoiceController.Source) {
                isListening = false
                input = text
                voiceStatus = "Command received"
                // The floating service remains the single executor when it is active.
                if (!FloatingTommyService.isRunning) {
                    sendCommand(context, messages, text)
                }
                input = ""
            }
        }

        val filter = IntentFilter(TommyStatusEvents.ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(statusReceiver, filter)
        }
        TommyVoiceController.addListener(voiceListener)

        onDispose {
            TommyVoiceController.removeListener(voiceListener)
            context.unregisterReceiver(statusReceiver)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun startVoiceInput() {
        val activity = context as? Activity
        if (activity != null && activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 4101)
            voiceStatus = "Allow microphone access, then tap 🎤 again"
            return
        }
        input = ""
        if (!TommyVoiceController.start(context, TommyVoiceController.Source.MIC)) {
            voiceStatus = "Tommy voice could not start"
        }
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
        Text("Speak commands naturally. Tommy uses the same skill engine for chat and voice.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) { items(messages) { ChatBubble(it) } }
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
                onClick = { if (isListening) TommyVoiceController.stop() else startVoiceInput() },
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

    val result = TommySkillEngine.execute(context, command)
    val prefix = if (result.success) "Tommy: " else "Tommy: "
    messages.add(TommyMessage(true, prefix + result.message))
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
        ) { Text(message.text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) }
    }
}
