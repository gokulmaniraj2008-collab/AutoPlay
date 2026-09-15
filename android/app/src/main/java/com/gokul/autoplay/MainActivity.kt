package com.gokul.autoplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

class MainActivity : ComponentActivity() {
    private var page by mutableStateOf(1)
    private var tommyReady by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            when (page) {
                1 -> Page1TommyHome(
                    tommyReady = tommyReady,
                    onStart = { tommyReady = true },
                    onChat = { page = 2 }
                )
                2 -> Page2TommyChat(onBack = { page = 1 })
            }
        }
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
                Text(
                    text = "TOMMY",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = "Your personal Android AI assistant",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Card(Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = if (tommyReady) "Tommy is ready" else "Hey, I'm Tommy",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (tommyReady) {
                                "Page 1 is ready. Feature 1 is now added separately."
                            } else {
                                "Clean starter build. Sensitive assistant features are disabled for now."
                            }
                        )
                        Button(
                            onClick = onStart,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (tommyReady) "Tommy Ready" else "Start Tommy")
                        }
                    }
                }

                Button(
                    onClick = onChat,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Open Tommy Chat")
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text("PAGE 1", fontWeight = FontWeight.Bold)
                        Text("Tommy Home")
                        Text(
                            "Features are added and verified one by one.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Page2TommyChat(onBack: () -> Unit) {
    val messages = androidx.compose.runtime.remember {
        mutableStateListOf("Tommy: Chat is ready. No sensitive permissions are required for this feature.")
    }
    var input by androidx.compose.runtime.remember { mutableStateOf("") }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Tommy Chat",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Button(onClick = onBack) { Text("Back") }
                }

                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { message ->
                        Card(Modifier.fillMaxWidth()) {
                            Text(message, Modifier.padding(14.dp))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type to Tommy…") },
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            val text = input.trim()
                            if (text.isNotEmpty()) {
                                messages.add("You: $text")
                                messages.add("Tommy: I received your message: $text")
                                input = ""
                            }
                        }
                    ) {
                        Text("Send")
                    }
                }
            }
        }
    }
}
