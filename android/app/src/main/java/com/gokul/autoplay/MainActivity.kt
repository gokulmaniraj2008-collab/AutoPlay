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
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Switch
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
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
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
private fun AutoPlayApp(
    onTommyStart: () -> Unit,
    onTommyStop: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var tommyEnabled by remember { mutableStateOf(false) }

    val startTommy: () -> Unit = {
        tommyEnabled = true
        onTommyStart()
    }

    val stopTommy: () -> Unit = {
        tommyEnabled = false
        onTommyStop()
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            icon = { Text("⌂") },
                            label = { Text("Home") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            icon = { Text("⚡") },
                            label = { Text("Commands") }
                        )
                        NavigationBarItem(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            icon = { Text("⚙") },
                            label = { Text("Settings") }
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (selectedTab) {
                        0 -> AutoPlayHome(
                            onAddSchedule = { selectedTab = 3 },
                            onTestNow = startTommy,
                            onTommyClick = startTommy
                        )
                        1 -> QuickCommandsPage()
                        2 -> AutoPlaySettings(
                            tommyEnabled = tommyEnabled,
                            onTommyEnabledChange = { enabled ->
                                if (enabled) startTommy() else stopTommy()
                            }
                        )
                        3 -> AutomationPage()
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoPlayHome(
    onAddSchedule: () -> Unit,
    onTestNow: () -> Unit,
    onTommyClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("AutoPlay", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            "Your music. Your commands. Automatically.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("READY TO PLAY", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("No active automation", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Create a schedule or test AutoPlay instantly.")
            }
        }

        Text("Quick actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onAddSchedule,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Add schedule") }
            Spacer(Modifier.width(10.dp))
            OutlinedButton(
                onClick = onTestNow,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Test now") }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Next automation", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                Text("No schedule yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Add your first music automation to get started.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.weight(1f))
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            FloatingActionButton(
                onClick = onTommyClick,
                shape = RoundedCornerShape(18.dp)
            ) { Text("Tommy", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun AutoPlaySettings(
    tommyEnabled: Boolean,
    onTommyEnabledChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Control Tommy permissions and app behavior.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Tommy assistant", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (tommyEnabled) "Tommy is ON. Voice listening and the floating bubble are active." else "Tommy is OFF. Voice listening and the floating bubble are stopped."
                    )
                }
                Switch(
                    checked = tommyEnabled,
                    onCheckedChange = onTommyEnabledChange
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp)) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Permissions", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Tommy uses microphone access for Hey Tommy voice listening and overlay access for the floating bubble.")
            }
        }
    }
}
