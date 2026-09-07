package com.tuned.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SettingsSheet(
    initialToken: String,
    initialChannels: List<String>,
    initialDirectOutput: Boolean,
    onDismiss: () -> Unit,
    onSave: (token: String, channels: List<String>, directOutput: Boolean) -> Unit
) {
    var token by remember { mutableStateOf(initialToken) }
    var channels by remember { mutableStateOf(initialChannels.joinToString(",")) }
    var directOutput by remember { mutableStateOf(initialDirectOutput) }

    Box(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.fillMaxWidth().background(Surface)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextPrimary)
                }
            }

            Text("Telegram Settings", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)

            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Bot Token") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            OutlinedTextField(
                value = channels,
                onValueChange = { channels = it },
                label = { Text("Channels (comma-separated)") },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Direct Output", color = TextPrimary)
                Switch(
                    checked = directOutput,
                    onCheckedChange = { directOutput = it },
                    colors = SwitchDefaults.colors(checkedThumbColor = Cyan)
                )
            }

            Button(
                onClick = {
                    onSave(token, channels.split(",").map { it.trim() }.filter { it.isNotEmpty() }, directOutput)
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Violet)
            ) {
                Text("Save", color = androidx.compose.ui.graphics.Color.Black)
            }
        }
    }
}
