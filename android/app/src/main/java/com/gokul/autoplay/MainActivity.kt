package com.gokul.autoplay

import android.Manifest
import android.content.Intent
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AutoPlayApp(::activateFloatingTommy, ::deactivateFloatingTommy) }
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
        Toast.makeText(this, "Tommy is ready", Toast.LENGTH_SHORT).show()
    }

    private fun deactivateFloatingTommy() {
        stopService(Intent(this, FloatingTommyService::class.java))
        Toast.makeText(this, "Tommy is off", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun AutoPlayApp(onStart: () -> Unit, onStop: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    var tommyEnabled by remember { mutableStateOf(false) }
    val start = { tommyEnabled = true; onStart() }
    val stop = { tommyEnabled = false; onStop() }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Scaffold(bottomBar = {
                NavigationBar {
                    NavigationBarItem(page == 0, { page = 0 }, icon = { Text("⌂") }, label = { Text("Home") })
                    NavigationBarItem(page == 1, { page = 1 }, icon = { Text("⚡") }, label = { Text("Pages") })
                    NavigationBarItem(page == 5, { page = 5 }, icon = { Text("T") }, label = { Text("Tommy") })
                }
            }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (page) {
                        0 -> HomePage({ page = 1 }, start)
                        1 -> PageIndex { page = it }
                        2 -> QuickCommandsPage()
                        3 -> AutomationPage()
                        4 -> BackgroundSearchPage(tommyEnabled, { if (it) start() else stop() })
                        5 -> VoiceAssistantPage(tommyEnabled, { if (it) start() else stop() })
                        6 -> FloatingAssistantPage()
                    }
                }
            }
        }
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
                Text("Six app pages", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Commands, automation, background mode, voice and floating Tommy.")
            }
        }
        Button(onClick = onPages, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("Open Pages 2–6") }
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
        Triple(6, "Floating Assistant", "Draggable floating Tommy bubble")
    )
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pages 2–6", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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
