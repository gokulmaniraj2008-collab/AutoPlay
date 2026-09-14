package com.gokul.autoplay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AutomationPage() {
    var youtubeEnabled by remember { mutableStateOf(true) }
    var morningEnabled by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FC))
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Automations", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Set actions once. AutoPlay handles them for you.", color = Color(0xFF697386))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Your automations", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                AutomationRow("YouTube", "Open your music routine", youtubeEnabled) { youtubeEnabled = it }
                Spacer(Modifier.height(8.dp))
                AutomationRow("Morning routine", "Run at 7:00 AM", morningEnabled) { morningEnabled = it }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF5FF))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Coming next", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Hey Tommy voice commands, app actions, and smart routines.", color = Color(0xFF526071))
            }
        }
    }
}

@Composable
private fun AutomationRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Color(0xFF7A8494), style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
}
