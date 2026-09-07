package com.tuned.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tuned.app.data.Store
import com.tuned.app.telegram.TdAuthState
import com.tuned.app.telegram.TelegramUserAuth
import kotlinx.coroutines.flow.collect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramLoginSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { Store(context) }

    var apiId by remember { mutableStateOf(if (store.tdApiId != 0) store.tdApiId.toString() else "") }
    var apiHash by remember { mutableStateOf(store.tdApiHash) }
    var credentialsSaved by remember { mutableStateOf(store.tdApiId != 0 && store.tdApiHash.isNotBlank()) }

    var phone by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var auth by remember { mutableStateOf<TelegramUserAuth?>(null) }
    var authState by remember { mutableStateOf<TdAuthState>(TdAuthState.Connecting) }

    LaunchedEffect(auth) {
        auth?.authState?.collect { authState = it }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface) {
        Column(Modifier.padding(20.dp).padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Telegram account login (experimental)",
                    color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextPrimary)
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "This logs into your real Telegram account directly, separate from the bot. " +
                    "It's new and hasn't been battle-tested — if anything looks wrong, stop and " +
                    "report exactly what you see rather than retrying repeatedly.",
                color = TextMuted, fontSize = 11.5.sp
            )

            Spacer(Modifier.height(20.dp))

            if (!credentialsSaved) {
                Text("API ID", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = apiId, onValueChange = { apiId = it.filter { c -> c.isDigit() } },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColorsLogin()
                )
                Spacer(Modifier.height(14.dp))
                Text("API hash", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = apiHash, onValueChange = { apiHash = it.trim() },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColorsLogin()
                )
                Text(
                    "Get both from my.telegram.org → API development tools. One-time setup.",
                    color = TextMuted, fontSize = 11.5.sp, modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = {
                        val id = apiId.toIntOrNull() ?: return@Button
                        store.tdApiId = id
                        store.tdApiHash = apiHash
                        credentialsSaved = true
                        auth = TelegramUserAuth(context.applicationContext, id, apiHash)
                    },
                    enabled = apiId.isNotBlank() && apiHash.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Violet)
                ) { Text("Continue", color = androidx.compose.ui.graphics.Color.Black) }
                return@Column
            }

            LaunchedEffect(Unit) {
                if (auth == null) {
                    auth = TelegramUserAuth(context.applicationContext, store.tdApiId, store.tdApiHash)
                }
            }

            when (val s = authState) {
                is TdAuthState.Connecting -> LoginStatusRow("Connecting…")

                is TdAuthState.NeedPhoneNumber -> {
                    Text("Phone number", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = phone, onValueChange = { phone = it },
                        placeholder = { Text("+1 555 123 4567", color = TextMuted) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColorsLogin()
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { auth?.submitPhoneNumber(phone.trim()) },
                        enabled = phone.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Violet)
                    ) { Text("Send code", color = androidx.compose.ui.graphics.Color.Black) }
                }

                is TdAuthState.NeedCode -> {
                    Text("Login code", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Check Telegram (on another device) for the code.",
                        color = TextMuted, fontSize = 11.5.sp, modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = code, onValueChange = { code = it.filter { c -> c.isDigit() } },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColorsLogin()
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { auth?.submitCode(code.trim()) },
                        enabled = code.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Violet)
                    ) { Text("Verify", color = androidx.compose.ui.graphics.Color.Black) }
                }

                is TdAuthState.NeedPassword -> {
                    Text("2FA password", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = password, onValueChange = { password = it },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(), colors = fieldColorsLogin()
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { auth?.submitPassword(password) },
                        enabled = password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Violet)
                    ) { Text("Unlock", color = androidx.compose.ui.graphics.Color.Black) }
                }

                is TdAuthState.Ready -> {
                    Text("Logged in.", color = Cyan, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Pulling your actual chats/audio through this login is the next step — " +
                            "not built yet. This confirms the login itself works.",
                        color = TextMuted, fontSize = 12.sp
                    )
                }

                is TdAuthState.Error -> {
                    Text("Something went wrong", color = Magenta, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Text(s.message, color = TextMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun LoginStatusRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Violet, strokeWidth = 2.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, color = TextMuted, fontSize = 13.sp)
    }
}

@Composable
private fun fieldColorsLogin() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Surface2,
    unfocusedContainerColor = Surface2,
    focusedBorderColor = Violet,
    unfocusedBorderColor = Line,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)
