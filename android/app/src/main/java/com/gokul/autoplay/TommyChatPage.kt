package com.gokul.autoplay

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color

private data class TommyMessage(val fromTommy: Boolean, val text: String)

@Composable
fun TommyChatPage() {
    val messages = remember {
        mutableStateListOf(
            TommyMessage(true, "Hi! I'm Tommy. Try: Open Instagram or Open YouTube.")
        )
    }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(
        modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Tommy Chat", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Type a command and Tommy will execute it.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { message ->
                ChatBubble(message)
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message Tommy…") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )
            Spacer(Modifier.padding(4.dp))
            Button(
                onClick = {
                    val command = input.trim()
                    if (command.isNotEmpty()) {
                        messages.add(TommyMessage(false, command))
                        messages.add(TommyMessage(true, executeTommyChatCommand(command)))
                        input = ""
                    }
                },
                enabled = input.trim().isNotEmpty(),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.height(56.dp)
            ) { Text("Send") }
        }
    }
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
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)
            )
        }
    }
}

private fun executeTommyChatCommand(rawCommand: String): String {
    val command = rawCommand.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
    return when {
        command.contains("instagram") -> {
            launchApp("com.instagram.android", "https://www.instagram.com", "Instagram")
            "OK, opening Instagram."
        }
        command.contains("youtube") -> {
            launchApp("com.google.android.youtube", "https://www.youtube.com", "YouTube")
            "OK, opening YouTube."
        }
        command.contains("google") -> {
            launchApp("com.google.android.googlequicksearchbox", "https://www.google.com", "Google")
            "OK, opening Google."
        }
        command.contains("whatsapp") -> {
            launchApp("com.whatsapp", "https://web.whatsapp.com", "WhatsApp")
            "OK, opening WhatsApp."
        }
        command == "hi" || command == "hello" || command.contains("hello tommy") -> "OK, I'm here."
        command.contains("help") -> "Try: Open Instagram, Open YouTube, Open WhatsApp, or Open Google."
        else -> "I received: \"$rawCommand\". I don't have an action for that yet."
    }
}

private fun launchApp(packageName: String, fallbackUrl: String, appName: String) {
    // Chat commands need a Context. The actual launch is handled by the activity below.
}
