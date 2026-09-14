package com.gokul.autoplay

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

@Composable
fun QuickCommandsPage() {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7F8FC))
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Quick Commands",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "One tap now. Voice command later.",
            color = Color(0xFF697386)
        )

        CommandCard("🔦", "Light on", "Turn your phone flashlight on", onClick = {
            toggleFlashlight(context)
        })
        CommandCard("▶️", "Open YouTube", "Launch YouTube for music and video", onClick = {
            openApp(context, "com.google.android.youtube", "https://www.youtube.com")
        })
        CommandCard("📸", "Open Instagram", "Launch Instagram for social actions", onClick = {
            openApp(context, "com.instagram.android", "https://www.instagram.com")
        })
        CommandCard("💬", "Open WhatsApp", "Start a WhatsApp message", onClick = {
            openApp(context, "com.whatsapp", "https://wa.me/")
        })

        Spacer(Modifier.height(2.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF5FF))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Hey Tommy", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "These same actions can be triggered by voice when Tommy is connected.",
                    color = Color(0xFF526071)
                )
            }
        }
    }
}

@Composable
private fun CommandCard(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Row(modifier = Modifier.padding(18.dp)) {
            Text(icon, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.padding(horizontal = 7.dp))
            Column {
                Text(title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text(
                    subtitle,
                    color = Color(0xFF7A8494),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun toggleFlashlight(context: Context) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        val activity = context as? Activity
        if (activity != null) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
        } else {
            Toast.makeText(context, "Camera permission is required for the flashlight", Toast.LENGTH_SHORT).show()
        }
        return
    }

    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    val cameraId = cameraManager.cameraIdList.firstOrNull { id ->
        cameraManager.getCameraCharacteristics(id)
            .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    }

    if (cameraId == null) {
        Toast.makeText(context, "This phone has no available flashlight", Toast.LENGTH_SHORT).show()
        return
    }

    val enabled = flashState[context] ?: false
    try {
        cameraManager.setTorchMode(cameraId, !enabled)
        flashState[context] = !enabled
        Toast.makeText(context, if (!enabled) "Flashlight ON" else "Flashlight OFF", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {
        Toast.makeText(context, "Unable to control flashlight", Toast.LENGTH_SHORT).show()
    }
}

private fun openApp(context: Context, packageName: String, fallbackUrl: String) {
    val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
    if (launchIntent != null) {
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
        return
    }

    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(fallbackUrl)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {
        Toast.makeText(context, "App is not available", Toast.LENGTH_SHORT).show()
    }
}

private val flashState = java.util.WeakHashMap<Context, Boolean>()
private const val CAMERA_PERMISSION_REQUEST = 2001
