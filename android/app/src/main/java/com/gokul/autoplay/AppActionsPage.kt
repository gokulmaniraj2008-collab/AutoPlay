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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AppActionsPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FC))
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "App Actions",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Choose what Hey Tommy can control.",
            color = Color(0xFF697386)
        )

        ActionCard("▶", "YouTube", "Open YouTube and search for a song", "Ready")
        ActionCard("▣", "Flashlight", "Turn the phone light on or off", "Ready")
        ActionCard("◎", "Instagram", "Open Instagram for supported actions", "Setup later")
        ActionCard("◌", "WhatsApp", "Open WhatsApp and prepare a message", "Setup later")

        Spacer(Modifier.height(2.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF5FF))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Voice examples", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("“Hey Tommy, light on”", color = Color(0xFF526071))
                Text("“Hey Tommy, open YouTube”", color = Color(0xFF526071))
                Text("“Hey Tommy, open WhatsApp”", color = Color(0xFF526071))
            }
        }
    }
}

@Composable
private fun ActionCard(
    icon: String,
    title: String,
    subtitle: String,
    status: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(modifier = Modifier.padding(18.dp)) {
            Text(icon, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.padding(horizontal = 8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    color = Color(0xFF7A8494),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                status,
                color = if (status == "Ready") Color(0xFF247A45) else Color(0xFF7A8494),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
