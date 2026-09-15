package com.gokul.autoplay

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gokul.autoplay.skills.TommySkillEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

private data class SpotifyResult(
    val type: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val spotifyUrl: String
)

private data class TommyMessage(
    val fromTommy: Boolean,
    val text: String,
    val spotifyResults: List<SpotifyResult> = emptyList()
)

private const val TOMMY_WEB_BASE_URL = "https://auto-play-4qkv.vercel.app"

@Composable
fun TommyChatPage() {
    val context = LocalContext.current
    val messages = remember {
        mutableStateListOf(
            TommyMessage(
                true,
                "Hi! I'm Tommy. Try: Open Instagram, go to Reels, scroll, like, follow, open comments, YouTube, Google, or search Spotify."
            )
        )
    }
    var input by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var voiceStatus by remember { mutableStateOf("Tommy is OFF") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

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
                if (!FloatingTommyService.isRunning) {
                    scope.launch { runTommyCommand(context, messages, text) }
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
            scope.launch { runTommyCommand(context, messages, command) }
            input = ""
            voiceStatus = "Tommy is working…"
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Tommy Chat", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Speak commands naturally. Spotify searches appear directly inside Tommy Chat.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages) { message ->
                ChatBubble(message)
                message.spotifyResults.forEach { result ->
                    SpotifyResultCard(context, result)
                }
            }
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

private suspend fun runTommyCommand(
    context: Context,
    messages: MutableList<TommyMessage>,
    command: String
) {
    messages.add(TommyMessage(false, command))

    if (isSpotifySearch(command)) {
        val query = extractSpotifyQuery(command)
        if (query.isNotBlank()) {
            messages.add(TommyMessage(true, "Searching Spotify for $query…"))
            val results = searchSpotify(query)
            if (results.isNotEmpty()) {
                messages.add(
                    TommyMessage(
                        fromTommy = true,
                        text = "I found these Spotify results.",
                        spotifyResults = results
                    )
                )
                return
            }
            messages.add(TommyMessage(true, "I couldn't load live Spotify results. I'll open Spotify search instead."))
        }
    }

    val result = TommySkillEngine.execute(context, command)
    messages.add(TommyMessage(true, "Tommy: ${result.message}"))
}

private fun isSpotifySearch(command: String): Boolean {
    val normalized = command.lowercase()
    return normalized.contains("spotify") &&
        (normalized.contains("search") || normalized.contains("find") || normalized.contains("song"))
}

private fun extractSpotifyQuery(command: String): String {
    return command
        .replace(Regex("(?i)open\\s+spotify\\s+search"), "")
        .replace(Regex("(?i)search\\s+spotify"), "")
        .replace(Regex("(?i)find\\s+(on\\s+)?spotify"), "")
        .replace(Regex("(?i)spotify"), "")
        .replace(Regex("(?i)search"), "")
        .trim()
}

private suspend fun searchSpotify(query: String): List<SpotifyResult> = withContext(Dispatchers.IO) {
    try {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val connection = URL("$TOMMY_WEB_BASE_URL/api/spotify-search?q=$encoded").openConnection()
        connection.connectTimeout = 8000
        connection.readTimeout = 10000
        val body = connection.getInputStream().bufferedReader().use { it.readText() }
        val json = JSONObject(body)
        val items = json.optJSONArray("results") ?: JSONArray()
        buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val spotifyUrl = item.optString("spotifyUrl")
                if (spotifyUrl.isBlank()) continue
                add(
                    SpotifyResult(
                        type = item.optString("type", "track"),
                        title = item.optString("title", "Unknown"),
                        subtitle = item.optString("subtitle", "Spotify"),
                        imageUrl = item.optString("imageUrl").takeIf { it.isNotBlank() },
                        spotifyUrl = spotifyUrl
                    )
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}

@Composable
private fun SpotifyResultCard(context: Context, result: SpotifyResult) {
    var bitmap by remember(result.imageUrl) { mutableStateOf<Bitmap?>(null) }
    var imageLoading by remember(result.imageUrl) { mutableStateOf(result.imageUrl != null) }

    LaunchedEffect(result.imageUrl) {
        val url = result.imageUrl ?: return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeStream(URL(url).openStream()) }.getOrNull()
        }
        imageLoading = false
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("●", color = androidx.compose.ui.graphics.Color(0xFF1DB954), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(8.dp))
                Text("Spotify", fontWeight = FontWeight.SemiBold)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    bitmap != null -> Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = result.title,
                        modifier = Modifier.size(72.dp),
                        contentScale = ContentScale.Fit
                    )
                    imageLoading -> CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(result.title, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    Text(result.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    Text(
                        if (result.type == "playlist") "Playlist" else "Song",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(result.spotifyUrl)).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                        )
                    },
                    shape = RoundedCornerShape(18.dp)
                ) { Text("▶ Play") }
            }
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
                containerColor = if (message.fromTommy) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer
            )
        ) { Text(message.text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) }
    }
}
