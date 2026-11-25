package com.zoai.smarttools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BgTop = Color(0xFF0B0B1E)
private val BgBottom = Color(0xFF12082A)
private val NeonCyan = Color(0xFF22E5FF)
private val NeonPurple = Color(0xFFB14CFF)
private val NeonPink = Color(0xFFFF4DF0)
private val NeonBlue = Color(0xFF1E90FF)
private val NeonGreen = Color(0xFF39FF14)
private val NeonViolet = Color(0xFFBC4DFF)
private val CardDark = Color(0xFF07071A)

enum class ToolType { CHAT, CODING, APP_MAKER }

private data class ToolItem(
    val type: ToolType,
    val title: String,
    val line1: String,
    val line2: String,
    val icon: ImageVector,
    val color: Color
)

private val tools = listOf(
    ToolItem(ToolType.CHAT, "AI Chat", "Ask Anything...", "Chat with AI", Icons.Filled.Chat, NeonBlue),
    ToolItem(ToolType.CODING, "AI Coding", "Generate & Debug Code", "Python, HTML, C++", Icons.Filled.Code, NeonGreen),
    ToolItem(ToolType.APP_MAKER, "AI App Maker", "Create Android Apps", "with AI Help", Icons.Filled.Android, NeonViolet)
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                var screen by remember { mutableStateOf("home") }
                when (screen) {
                    "chat" -> ChatScreen(
                        title = "AI Chat",
                        accent = NeonCyan,
                        storageKey = "chat_history",
                        systemPrompt = CHAT_SYSTEM,
                        onBack = { screen = "home" }
                    )
                    "coding" -> ChatScreen(
                        title = "AI Coding",
                        accent = NeonGreen,
                        storageKey = "coding_history",
                        systemPrompt = CODING_SYSTEM,
                        onBack = { screen = "home" }
                    )
                    else -> ZoAiHomeScreen(
                        onToolClick = { type ->
                            screen = when (type) {
                                ToolType.CHAT -> "chat"
                                ToolType.CODING -> "coding"
                                else -> "home"
                            }
                        },
                        onSettingsClick = { }
                    )
                }
            }
        }
    }
}

@Composable
fun ZoAiHomeScreen(
    onToolClick: (ToolType) -> Unit,
    onSettingsClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Header(onSettingsClick)

            Spacer(modifier = Modifier.height(20.dp))

            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                tools.forEach { item ->
                    ToolCard(item = item, onClick = { onToolClick(item.type) })
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Footer()
        }
    }
}

@Composable
private fun Header(onSettingsClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                imageVector = Icons.Filled.SmartToy,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.size(88.dp)
            )
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .border(2.dp, NeonPurple, CircleShape)
                    .clickable(onClick = onSettingsClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }

        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White)) { append("Zo") }
                withStyle(
                    SpanStyle(brush = Brush.horizontalGradient(listOf(NeonPink, NeonPurple)))
                ) { append("Ai") }
            },
            fontSize = 72.sp,
            fontWeight = FontWeight.Black
        )

        Text(
            text = "AI SMART TOOLS",
            color = NeonCyan,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(modifier = Modifier.height(14.dp))

        val pillShape = RoundedCornerShape(50)
        Box(
            modifier = Modifier
                .clip(pillShape)
                .border(2.dp, NeonPink, pillShape)
                .background(Color(0x33B14CFF), pillShape)
                .padding(horizontal = 22.dp, vertical = 8.dp)
        ) {
            Text(
                text = "One App • Multiple AI Tools",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ToolCard(item: ToolItem, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(124.dp)
            .shadow(elevation = 14.dp, shape = shape, spotColor = item.color, ambientColor = item.color)
            .clip(shape)
            .background(
                Brush.horizontalGradient(listOf(item.color.copy(alpha = 0.22f), CardDark))
            )
            .border(2.dp, item.color, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF05050F))
                    .border(3.dp, item.color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = null,
                    tint = item.color,
                    modifier = Modifier.size(42.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(72.dp)
                    .background(item.color)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = item.line1,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 17.sp
                )
                Text(
                    text = item.line2,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 17.sp
                )
            }

            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .border(2.dp, item.color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = item.color
                )
            }
        }
    }
}

@Composable
private fun Footer() {
    val shape = RoundedCornerShape(50)
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .clip(shape)
                .border(1.5.dp, NeonPurple, shape)
                .padding(horizontal = 28.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Bolt, contentDescription = null, tint = NeonPink)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Powered by AI",
                        color = NeonPink,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = "Build • Learn • Grow",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}
