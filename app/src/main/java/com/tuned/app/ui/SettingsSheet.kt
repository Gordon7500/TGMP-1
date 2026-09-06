package com.tuned.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    initialToken: String,
    initialChannels: List<String>,
    initialDirectOutput: Boolean,
    onDismiss: () -> Unit,
    onSave: (token: String, channels: List<String>, directOutput: Boolean) -> Unit
) {
    var token by remember { mutableStateOf(initialToken) }
    var channels by remember { mutableStateOf(initialChannels) }
    var channelInput by remember { mutableStateOf("") }
    var directOutput by remember { mutableStateOf(initialDirectOutput) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Surface
    ) {
        Column(Modifier.padding(20.dp).padding(bottom = 24.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Connect Telegram", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextPrimary)
                }
            }

            Spacer(Modifier.height(18.dp))
            Text("Bot token", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                placeholder = { Text("123456:ABC-DEF...", color = TextMuted) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = fieldColors()
            )
            Text(
                "From @BotFather. Stored only on this device, sent only to Telegram.",
                color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(Modifier.height(22.dp))
            Text("Channels", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Add the bot as a member (or admin, for private channels) of each channel first.",
                color = TextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
            )
            LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                items(channels) { ch ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp)
                            .background(Surface2, RoundedCornerShape(10.dp)).padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(ch, color = TextPrimary, fontSize = 13.5.sp)
                        Text(
                            "×", color = TextMuted, fontSize = 16.sp,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clickable { channels = channels - ch }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = channelInput,
                    onValueChange = { channelInput = it },
                    placeholder = { Text("@channelname", color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = fieldColors()
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = {
                        val v = channelInput.trim()
                        if (v.isNotEmpty()) {
                            val normalized = if (v.startsWith("@") || v.toLongOrNull() != null) v else "@$v"
                            if (normalized !in channels) channels = channels + normalized
                            channelInput = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Surface2)
                ) { Text("Add", color = TextPrimary) }
            }

            Spacer(Modifier.height(22.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Direct output to DAC", color = TextPrimary, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Matches sample rate exactly and routes straight to a connected USB DAC when present. Turn off to use the phone's normal audio output.",
                        color = TextMuted, fontSize = 11.5.sp, modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Switch(
                    checked = directOutput,
                    onCheckedChange = { directOutput = it },
                    colors = SwitchDefaults.colors(checkedTrackColor = Violet)
                )
            }

            Spacer(Modifier.height(22.dp))
            Button(
                onClick = { onSave(token.trim(), channels, directOutput) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Violet)
            ) { Text("Save & Sync", color = androidx.compose.ui.graphics.Color.Black, fontWeight = FontWeight.SemiBold) }

            Spacer(Modifier.height(20.dp))
            Text(
                "Telegram bots can't retrieve a channel's old history — only messages posted after the bot joins. " +
                    "Keep the app open once after adding a new channel to catch its recent posts.",
                color = TextMuted, fontSize = 11.5.sp
            )
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Surface2,
    unfocusedContainerColor = Surface2,
    focusedBorderColor = Violet,
    unfocusedBorderColor = Line,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
