package com.tuned.app.telegram

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

sealed class TdAuthState {
    data object Connecting : TdAuthState()
    data object NeedPhoneNumber : TdAuthState()
    data object NeedCode : TdAuthState()
    data object NeedPassword : TdAuthState()
    data object Ready : TdAuthState()
    data class Error(val message: String) : TdAuthState()
}

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
    private val requestIdCounter = AtomicLong(1)
    private val pendingRequests = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()

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
            
            // Route response to caller if @extra correlation ID exists
            val extra = obj.optString("@extra", "")
            if (extra.isNotEmpty()) {
                pendingRequests.remove(extra)?.complete(obj)
            }

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

    private suspend fun sendRequest(request: JSONObject): JSONObject? {
        val extraId = requestIdCounter.getAndIncrement().toString()
        request.put("@extra", extraId)
        val deferred = CompletableDeferred<JSONObject>()
        pendingRequests[extraId] = deferred
        client.send(request.toString())
        
        return withTimeoutOrNull(15000) { deferred.await() }
    }

    suspend fun resolveLocalFilePath(fileId: Int): String? {
        // 1. Fetch file status from TDLib
        val getFileReq = JSONObject().apply {
            put("@type", "getFile")
            put("file_id", fileId)
        }
        var response = sendRequest(getFileReq) ?: return null
        if (response.optString("@type") == "error") return null

        var local = response.optJSONObject("local") ?: return null
        if (local.optBoolean("is_downloading_completed", false)) {
            return local.optString("path").takeIf { it.isNotEmpty() }
        }

        // 2. File not local; trigger download
        val downloadReq = JSONObject().apply {
            put("@type", "downloadFile")
            put("file_id", fileId)
            put("priority", 32)
            put("synchronous", true)
        }
        response = sendRequest(downloadReq) ?: return null
        local = response.optJSONObject("local") ?: return null
        return local.optString("path").takeIf { it.isNotEmpty() }
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
