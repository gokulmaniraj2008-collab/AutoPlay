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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
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
        setContent {
            AutoPlayApp(
                onTommyStart = ::activateFloatingTommy,
                onTommyStop = ::deactivateFloatingTommy
            )
        }
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
private fun AutoPlayApp(onTommyStart: () -> Unit, onTommyStop: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    var tommyEnabled by remember { mutableStateOf(false) }

    val startTommy = {
        tommyEnabled = true
        onTommyStart()
    }
    val stopTommy = {
        tommyEnabled = false
        onTommyStop()
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(page == 0, { page = 0 }, icon = { Text("⌂") }, label = { Text("Home") })
                        NavigationBarItem(page == 1, { page = 1 }, icon = { Text("⚡") }, label = { Text("Commands") })
                        NavigationBarItem(page == 5, { page = 5 }, icon = { Text("T") }, label = { Text("Tommy") })
                    }
                }
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (page) {
                        0 -> HomePage(onOpenPages = { page = 1 }, onTommy = startTommy)
                        1 -> PageIndex(onSelectPage = { page = it })
                        2 -> QuickCommandsPage()
                        3 -> AutomationPage()
                        4 -> BackgroundSearchPage()
                        5 -> VoiceAssistantPage()
                        6 -> FloatingAssistantPage()
                    }

                    when (page) {
                        4 -> BottomAction("Enable Tommy floating mode", startTommy)
                        5 -> BottomAction(if (tommyEnabled) "Tommy is ON — Turn OFF" else "Start Hey Tommy", if (tommyEnabled) stopTommy else startTommy)
                        6 -> BottomAction("Enable draggable Tommy bubble", startTommy)
                    }
                }
            }
        }
    }
}

@Composable
private fun HomePage(onOpenPages: () -> Unit, onTommy: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("AutoPlay", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Your music. Your commands. Automatically.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(20.dp)) {
                Text("READY TO PLAY", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Six functional app pages", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Commands, automation, background mode, floating Tommy and voice control.")
            }
        }
        Button(onClick = onOpenPages, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("Open Pages 2–6") }
        OutlinedButton(onClick = onTommy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) { Text("Start Tommy") }
    }
}

@Composable
private fun PageIndex(onSelectPage: (Int) -> Unit) {
    val pages = listOf(
        2 to "Quick Commands" to "Light, YouTube, Instagram and WhatsApp",
        3 to "Automations" to "Automation controls and routines",
        4 to "Background Search" to "Keep Tommy available over other apps",
        6 to "Floating Assistant" to "Draggable floating Tommy bubble",
        5 to "Hey Tommy" to "Wake phrase and voice commands"
    )
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Pages 2–6", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Each page now has a direct navigation path.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        pages.forEach { (pageAndTitle, description) ->
            val pageNumber = pageAndTitle.first
            val title = pageAndTitle.second
            Card(onClick = { onSelectPage(pageNumber) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("PAGE $pageNumber", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun BottomAction(label: String, action: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.BottomCenter) {
        Button(onClick = action, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Text(label) }
    }
}
