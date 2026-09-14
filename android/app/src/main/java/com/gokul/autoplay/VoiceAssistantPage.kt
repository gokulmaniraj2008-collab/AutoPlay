package com.gokul.autoplay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun VoiceAssistantPage(
    tommyEnabled: Boolean,
    onTommyToggle: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FC))
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Hey Tommy", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Your voice-first control center", color = Color(0xFF697386))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(112.dp).background(Color(0xFFEAF2FF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("T", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = Color(0xFF2563EB))
                }
                Spacer(Modifier.height(18.dp))
                Text(if (tommyEnabled) "Tommy is listening" else "Tommy is stopped", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    if (tommyEnabled) "Say “Hey Tommy” to start a command." else "Turn Tommy on to enable the wake phrase.",
                    color = Color(0xFF697386)
                )
                Spacer(Modifier.height(14.dp))
                Button(onClick = { onTommyToggle(!tommyEnabled) }) {
                    Text(if (tommyEnabled) "Turn Tommy OFF" else "Start Hey Tommy")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Hey Tommy activation", fontWeight = FontWeight.SemiBold)
                    Text("Voice listening + floating bubble", color = Color(0xFF7A8494), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = tommyEnabled, onCheckedChange = onTommyToggle)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF5FF))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Try a command", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("“Hey Tommy, open YouTube”")
                Text("“Hey Tommy, light on”")
                Text("“Hey Tommy, open WhatsApp”")
            }
        }
    }
}
