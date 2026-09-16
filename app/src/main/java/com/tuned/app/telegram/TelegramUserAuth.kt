package com.tuned.app.telegram

import android.content.Context
import com.tuned.app.data.Track
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

sealed class TdAuthState {
    data object Connecting : TdAuthState()
    data object NeedPhoneNumber : TdAuthState()
    data object NeedCode : TdAuthState()
    data object NeedPassword : TdAuthState()
    data object Ready : TdAuthState()
    data class Error(val message: String) : TdAuthState()
}

/**
 * Drives TDLib's login flow AND (once logged in) fetching audio from the account's real chats.
 * Every outgoing request that expects a specific reply is tagged with a random "@extra" id so
 * the single shared receive loop can route the matching response back to whoever asked for it —
 * TDLib's JSON interface delivers everything (updates and responses alike) on one stream.
 */
class TelegramUserAuth(
    private val context: Context,
    private val apiId: Int,
    private val apiHash: String
) {
    private val client = TdJsonClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    // Serializes every single outgoing request. Overlapping calls into the native TDLib layer
    // (e.g. a second search starting before the first one's sequence of requests has fully
    // finished) is the most likely cause of a native-level crash that a Kotlin try/catch can't
    // stop — this makes that impossible by construction, regardless of the exact trigger.
    private val requestMutex = Mutex()

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

            val extra = obj.optString("@extra", "")
            if (extra.isNotEmpty()) {
                pending.remove(extra)?.complete(obj)
                return
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
                    TdLibSessionManager.clearIfCurrent(this)
                }
            }
        } catch (e: Exception) {
            _authState.value = TdAuthState.Error("Couldn't parse TDLib response: ${e.message}")
        }
    }

    /** Sends a request and suspends until its specific reply arrives (or times out). */
    private suspend fun sendForResult(request: JSONObject, timeoutMs: Long = 20_000): JSONObject? =
        requestMutex.withLock {
            val extra = UUID.randomUUID().toString()
            request.put("@extra", extra)
            val deferred = CompletableDeferred<JSONObject>()
            pending[extra] = deferred
            try {
                client.send(request.toString())
                withTimeoutOrNull(timeoutMs) { deferred.await() }
            } finally {
                pending.remove(extra)
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

    // ---------- Fetching audio from real chats (phase 2) ----------

    /** Parses a TDLib message into a Track if it's a messageAudio; null otherwise. */
    private fun parseAudioMessage(msg: JSONObject, chatId: Long, chatTitle: String): Track? {
        val content = msg.optJSONObject("content") ?: return null
        if (content.optString("@type") != "messageAudio") return null
        val audio = content.optJSONObject("audio") ?: return null
        val fileObj = audio.optJSONObject("audio") ?: return null

        val tdFileId = fileObj.optInt("id", -1)
        if (tdFileId == -1) return null
        val uniqueId = fileObj.optJSONObject("remote")?.optString("unique_id")
            ?: "td-$chatId-${msg.optLong("id")}"
        val sizeBytes = fileObj.optLong("size", -1).let { if (it > 0) it else fileObj.optLong("expected_size", -1) }
            .let { if (it > 0) it else null }
        val durationSec = audio.optInt("duration", 0)
        val fileName = audio.optString("file_name").takeIf { it.isNotBlank() }
        val mimeType = audio.optString("mime_type").takeIf { it.isNotBlank() }

        return Track(
            fileId = "",
            fileUniqueId = "td:$uniqueId",
            title = audio.optString("title").takeIf { it.isNotBlank() } ?: fileName ?: "Untitled",
            artist = audio.optString("performer").takeIf { it.isNotBlank() } ?: "Unknown artist",
            durationSec = durationSec,
            thumbFileId = null,
            sourceChat = chatTitle,
            dateAdded = msg.optLong("date", System.currentTimeMillis() / 1000),
            tdChatId = chatId,
            tdMessageId = msg.optLong("id"),
            qualityLabel = com.tuned.app.data.QualityLabel.estimate(fileName, mimeType, sizeBytes, durationSec)
        )
    }

    /**
     * Fast, on-demand search: asks Telegram's own servers to search audio matching [query]
     * across your whole account, right now — no pre-scanning or waiting required. This is what
     * powers typing into the search bar.
     */
    suspend fun searchAudioByQuery(query: String, limit: Int = 40): List<Track> {
        if (query.isBlank()) return emptyList()

        val result = sendForResult(
            JSONObject().apply {
                put("@type", "searchMessages")
                put("chat_list", JSONObject.NULL)
                put("query", query)
                put("offset_date", 0)
                put("offset_chat_id", 0)
                put("offset_message_id", 0)
                put("limit", limit)
                put("filter", JSONObject().apply { put("@type", "searchMessagesFilterAudio") })
            },
            timeoutMs = 15_000
        ) ?: return emptyList()

        val messages = result.optJSONArray("messages") ?: return emptyList()
        val chatTitleCache = mutableMapOf<Long, String>()
        val found = mutableListOf<Track>()

        for (i in 0 until messages.length()) {
            val msg = messages.getJSONObject(i)
            val chatId = msg.optLong("chat_id")
            val chatTitle = chatTitleCache.getOrPut(chatId) {
                sendForResult(JSONObject().apply {
                    put("@type", "getChat")
                    put("chat_id", chatId)
                })?.optString("title")?.takeIf { it.isNotBlank() } ?: "Chat"
            }
            parseAudioMessage(msg, chatId, chatTitle)?.let { found.add(it) }
        }
        return found
    }

    /**
     * Scans your account's main chat list for audio files, paging through each chat's full
     * audio history (not just the first page) up to a safety cap per chat. This can take a
     * while on a large account — [onProgress] reports what it's doing as it goes. Optional now
     * that on-demand search exists — useful if you want everything pre-loaded up front instead.
     */
    suspend fun fetchAudioTracks(
        maxChats: Int = 100,
        maxMessagesPerChat: Int = 1000,
        onProgress: (String) -> Unit = {}
    ): List<Track> {
        val found = mutableListOf<Track>()

        repeat(4) {
            sendForResult(
                JSONObject().apply {
                    put("@type", "loadChats")
                    put("chat_list", JSONObject().apply { put("@type", "chatListMain") })
                    put("limit", maxChats)
                },
                timeoutMs = 10_000
            )
        }

        val chatsResult = sendForResult(
            JSONObject().apply {
                put("@type", "getChats")
                put("chat_list", JSONObject().apply { put("@type", "chatListMain") })
                put("limit", maxChats)
            }
        ) ?: return found

        val chatIds = chatsResult.optJSONArray("chat_ids") ?: return found

        for (i in 0 until chatIds.length()) {
            val chatId = chatIds.getLong(i)

            val chatInfo = sendForResult(JSONObject().apply {
                put("@type", "getChat")
                put("chat_id", chatId)
            })
            val chatTitle = chatInfo?.optString("title")?.takeIf { it.isNotBlank() } ?: "Chat"

            var fromMessageId = 0L
            var fetchedForThisChat = 0
            var page = 0

            while (fetchedForThisChat < maxMessagesPerChat) {
                page++
                onProgress("Chat ${i + 1} of ${chatIds.length()} ($chatTitle) — page $page…")

                val searchResult = sendForResult(
                    JSONObject().apply {
                        put("@type", "searchChatMessages")
                        put("chat_id", chatId)
                        put("query", "")
                        put("filter", JSONObject().apply { put("@type", "searchMessagesFilterAudio") })
                        put("from_message_id", fromMessageId)
                        put("limit", 100)
                    },
                    timeoutMs = 15_000
                ) ?: break

                val messages = searchResult.optJSONArray("messages") ?: break
                if (messages.length() == 0) break

                for (m in 0 until messages.length()) {
                    val msg = messages.getJSONObject(m)
                    parseAudioMessage(msg, chatId, chatTitle)?.let {
                        found.add(it)
                        fetchedForThisChat++
                    }
                }

                if (messages.length() < 100) break // last page for this chat
                fromMessageId = messages.getJSONObject(messages.length() - 1).optLong("id")
            }
        }

        return found
    }

    /**
     * Public entry point for playing a persisted TDLib track. Re-fetches the message fresh
     * (using the stable chat/message ID) to get a *current* file ID before downloading — the
     * raw file ID itself is only valid within one login session and would otherwise silently
     * point at nothing after the app restarts and creates a new session.
     */
    suspend fun resolveLocalFilePathForMessage(chatId: Long, messageId: Long): String? {
        val messageResult = sendForResult(
            JSONObject().apply {
                put("@type", "getMessage")
                put("chat_id", chatId)
                put("message_id", messageId)
            },
            timeoutMs = 15_000
        ) ?: throw Exception("Couldn't find this message anymore")

        if (messageResult.optString("@type") == "error") {
            throw Exception(messageResult.optString("message", "Message no longer available"))
        }

        val content = messageResult.optJSONObject("content")
        val audio = content?.optJSONObject("audio")
        val fileObj = audio?.optJSONObject("audio")
        val freshFileId = fileObj?.optInt("id", -1) ?: -1
        if (freshFileId == -1) throw Exception("This track's audio is no longer available")

        return downloadByFileId(freshFileId)
    }

    /** Downloads (if needed) and returns the local file path for a *current-session* file ID.
     *  Retries once automatically since a single failed attempt on a large file isn't
     *  necessarily a permanent problem. */
    private suspend fun downloadByFileId(fileId: Int): String? {
        repeat(2) { attempt ->
            val result = sendForResult(
                JSONObject().apply {
                    put("@type", "downloadFile")
                    put("file_id", fileId)
                    put("priority", 1)
                    put("offset", 0)
                    put("limit", 0)
                    put("synchronous", true)
                },
                timeoutMs = 120_000 // large audio files can take a while
            )

            if (result == null) {
                if (attempt == 1) throw Exception("Download timed out")
                return@repeat // retry
            }

            if (result.optString("@type") == "error") {
                val message = result.optString("message", "Unknown error")
                if (attempt == 1) throw Exception(message)
                return@repeat // retry
            }

            val local = result.optJSONObject("local")
            if (local != null && local.optBoolean("is_downloading_completed", false)) {
                val path = local.optString("path").takeIf { it.isNotBlank() }
                if (path != null) return path
            }

            if (attempt == 1) throw Exception("File wasn't fully downloaded")
        }
        return null
    }
}
