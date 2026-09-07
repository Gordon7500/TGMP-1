package com.tuned.app.telegram

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

sealed class TdAuthState {
    data object Connecting : TdAuthState()
    data object NeedPhoneNumber : TdAuthState()
    data object NeedCode : TdAuthState()
    data object NeedPassword : TdAuthState()
    data object Ready : TdAuthState()
    data class Error(val message: String) : TdAuthState()
}

/**
 * Drives TDLib's login flow. This is the first phase (get logged in) — pulling actual chat/audio
 * data through this client is a separate follow-up, deliberately kept out of this class so login
 * can be verified working on its own first.
 */
class TelegramUserAuth(
    private val context: Context,
    private val apiId: Int,
    private val apiHash: String
) {
    private val client = TdJsonClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _authState = MutableStateFlow<TdAuthState>(TdAuthState.Connecting)
    val authState: StateFlow<TdAuthState> = _authState

    private var running = true

    init {
        scope.launch { receiveLoop() }
    }

    private suspend fun receiveLoop() {
        while (running) {
            val result = client.receive(2.0) ?: continue
            handleUpdate(result)
        }
    }

    private fun handleUpdate(json: String) {
        try {
            val obj = JSONObject(json)
            if (obj.optString("@type") != "updateAuthorizationState") return
            val state = obj.optJSONObject("authorization_state") ?: return
            when (state.optString("@type")) {
                "authorizationStateWaitTdlibParameters" -> sendTdlibParameters()
                "authorizationStateWaitPhoneNumber" -> _authState.value = TdAuthState.NeedPhoneNumber
                "authorizationStateWaitCode" -> _authState.value = TdAuthState.NeedCode
                "authorizationStateWaitPassword" -> _authState.value = TdAuthState.NeedPassword
                "authorizationStateReady" -> _authState.value = TdAuthState.Ready
                "authorizationStateClosed" -> {
                    running = false
                    _authState.value = TdAuthState.Error("Session closed")
                }
            }
        } catch (e: Exception) {
            _authState.value = TdAuthState.Error("Couldn't parse TDLib response: ${e.message}")
        }
    }

    private fun sendTdlibParameters() {
        val dir = context.filesDir.absolutePath + "/tdlib"
        val params = JSONObject().apply {
            put("@type", "setTdlibParameters")
            put("database_directory", dir)
            put("use_message_database", true)
            put("use_secret_chats", false)
            put("api_id", apiId)
            put("api_hash", apiHash)
            put("system_language_code", "en")
            put("device_model", "Android")
            put("application_version", "1.0")
        }
        client.send(params.toString())
    }

    fun submitPhoneNumber(phone: String) {
        client.send(JSONObject().apply {
            put("@type", "setAuthenticationPhoneNumber")
            put("phone_number", phone)
        }.toString())
    }

    fun submitCode(code: String) {
        client.send(JSONObject().apply {
            put("@type", "checkAuthenticationCode")
            put("code", code)
        }.toString())
    }

    fun submitPassword(password: String) {
        client.send(JSONObject().apply {
            put("@type", "checkAuthenticationPassword")
            put("password", password)
        }.toString())
    }

    fun logOut() {
        client.send(JSONObject().apply { put("@type", "logOut") }.toString())
    }

    fun close() {
        running = false
        client.destroy()
    }
}
