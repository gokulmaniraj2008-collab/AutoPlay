package com.gokul.autoplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AutoPlayApp()
        }
    }
}

@Composable
private fun AutoPlayApp() {
    val page = remember { mutableStateOf(1) }

    if (page.value == 1) {
        PageOne(onHomeClick = { page.value = 2 })
    } else {
        PageTwo(onBackClick = { page.value = 1 })
    }
}

@Composable
private fun PageOne(onHomeClick: () -> Unit) {
    val background = Color(0xFFF8F6FF)
    val darkText = Color(0xFF17151D)
    val violet = Color(0xFF7256E8)

    Scaffold(
        containerColor = background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "AutoPlay",
                color = darkText,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "A clean rebuild starts here.",
                color = darkText,
                fontSize = 18.sp
            )
            Text(
                text = "Page 1 of the new AutoPlay app",
                color = darkText,
                fontSize = 16.sp
            )
            Button(
                onClick = onHomeClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = violet,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Home",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun PageTwo(onBackClick: () -> Unit) {
    val background = Color(0xFFF8F6FF)
    val darkText = Color(0xFF17151D)
    val violet = Color(0xFF7256E8)

    Scaffold(
        containerColor = background,
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(background)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "AutoPlay",
                color = darkText,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Page 2 of the new AutoPlay app",
                color = darkText,
                fontSize = 18.sp
            )
            Button(
                onClick = onBackClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = violet,
                    contentColor = Color.White
                )
            ) {
                Text(
                    text = "Back",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
