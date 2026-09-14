package com.gokul.autoplay

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    private var tommyStatus by mutableStateOf(TommyStatusEvents.OFF)
    private var tommyStatusText by mutableStateOf("Tommy is OFF")

    private val tommyStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != TommyStatusEvents.ACTION) return
            tommyStatus = intent.getStringExtra(TommyStatusEvents.EXTRA_STATUS) ?: TommyStatusEvents.OFF
            tommyStatusText = intent.getStringExtra(TommyStatusEvents.EXTRA_TEXT)
                ?: defaultTommyStatusText(tommyStatus)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoPlayApp(
                tommyStatus = tommyStatus,
                tommyStatusText = tommyStatusText,
                onStart = ::activateFloatingTommy,
                onStop = ::deactivateFloatingTommy
            )
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            tommyStatusReceiver,
            IntentFilter(TommyStatusEvents.ACTION),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        unregisterReceiver(tommyStatusReceiver)
        super.onStop()
    }

    private fun activateFloatingTommy() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.parse("package:$packageName") })
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, FloatingTommyService::class.java))
    }

    private fun deactivateFloatingTommy() {
        stopService(Intent(this, FloatingTommyService::class.java))
        tommyStatus = TommyStatusEvents.OFF
        tommyStatusText = "Tommy is OFF"
        Toast.makeText(this, "Tommy is off", Toast.LENGTH_SHORT).show()
    }

    private fun defaultTommyStatusText(status: String): String = when (status) {
        TommyStatusEvents.ON -> "Tommy is ON"
        TommyStatusEvents.LISTENING -> "Tommy is listening…"
        TommyStatusEvents.HEARD -> "Tommy heard you"
        TommyStatusEvents.WORKING -> "Tommy is working…"
        else -> "Tommy is OFF"
    }
}

@Composable
private fun AutoPlayApp(
    tommyStatus: String,
    tommyStatusText: String,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    var page by remember { mutableIntStateOf(0) }
    val tommyEnabled = tommyStatus != TommyStatusEvents.OFF

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(bottomBar = {
                Column {
                    TommyBottomStatus(tommyStatus, tommyStatusText)
                    NavigationBar {
                        NavigationBarItem(page == 0, { page = 0 }, icon = { Text("⌂") }, label = { Text("Home") })
                        NavigationBarItem(page == 1, { page = 1 }, icon = { Text("⚡") }, label = { Text("Pages") })
                        NavigationBarItem(page == 7, { page = 7 }, icon = { Text("💬") }, label = { Text("Chat") })
                    }
                }
            }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (page) {
                        0 -> HomePage({ page = 1 }, onStart)
                        1 -> PageIndex { page = it }
                        2 -> QuickCommandsPage()
                        3 -> AutomationPage()
                        4 -> BackgroundSearchPage(tommyEnabled, { if (it) onStart() else onStop() })
                        5 -> VoiceAssistantPage(tommyEnabled, { if (it) onStart() else onStop() })
                        6 -> FloatingAssistantPage()
                        7 -> TommyChatPage()
                    }
                }
            }
        }
    }
}

@Composable
private fun TommyBottomStatus(status: String, text: String) {
    val indicator = when (status) {
        TommyStatusEvents.OFF -> "○"
        TommyStatusEvents.LISTENING -> "◉"
        TommyStatusEvents.HEARD -> "✓"
        TommyStatusEvents.WORKING -> "◉"
        else -> "●"
    }
    val label = when (status) {
        TommyStatusEvents.OFF -> "Tommy is OFF"
        TommyStatusEvents.ON -> "Tommy is ON"
        TommyStatusEvents.LISTENING -> "Tommy is listening…"
        TommyStatusEvents.HEARD -> text
        TommyStatusEvents.WORKING -> "Tommy is working…"
        else -> text
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Text(
            text = "$indicator  $label",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun HomePage(onPages: () -> Unit, onTommy: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("AutoPlay", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Your music. Your commands. Automatically.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(20.dp)) {
                Text("READY TO PLAY", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Seven app pages", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Commands, automation, background mode, voice, floating Tommy and chat.")
            }
        }
        Button(onClick = onPages, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("Open Pages 2–7") }
        OutlinedButton(onClick = onTommy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("Start Tommy") }
    }
}

@Composable
private fun PageIndex(onSelect: (Int) -> Unit) {
    val pages = listOf(
        Triple(2, "Quick Commands", "Flashlight, YouTube, Instagram and WhatsApp"),
        Triple(3, "Automations", "Automation controls and routines"),
        Triple(4, "Background Search", "Keep Tommy available over other apps"),
        Triple(5, "Hey Tommy", "Wake phrase and voice commands"),
        Triple(6, "Floating Assistant", "Draggable floating Tommy bubble"),
        Triple(7, "Tommy Chat", "Type a message and execute app commands")
    )
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pages 2–7", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Direct access to every page.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        pages.forEach { (number, title, description) ->
            Card(onClick = { onSelect(number) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("PAGE $number", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
