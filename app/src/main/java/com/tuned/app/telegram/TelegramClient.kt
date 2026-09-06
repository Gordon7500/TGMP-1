package com.tuned.app.telegram

import com.tuned.app.data.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TelegramException(message: String) : Exception(message)

/**
 * Thin wrapper around the Telegram Bot API. A bot can only read messages/files from chats
 * it has itself joined — there is no API to enumerate channels the *user* has joined.
 */
class TelegramClient(private val token: String) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json".toMediaType()

    private suspend fun call(method: String, params: JSONObject = JSONObject()): JSONObject =
        withContext(Dispatchers.IO) {
            val url = "https://api.telegram.org/bot$token/$method"
            val body = params.toString().toRequestBody(jsonMedia)
            val request = Request.Builder().url(url).post(body).build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string() ?: "{}"
                val json = JSONObject(text)
                if (!json.optBoolean("ok", false)) {
                    throw TelegramException(json.optString("description", "$method failed"))
                }
                json
            }
        }

    /** Fetches new updates since [offset] and returns (newOffset, newTracks). */
    suspend fun fetchNewTracks(offset: Long, knownIds: Set<String>): Pair<Long, List<Track>> {
        val params = JSONObject().apply {
            put("offset", offset)
            put("timeout", 0)
        }
        val result = call("getUpdates", params)
        val updates = result.getJSONArray("result")
        var newOffset = offset
        val found = mutableListOf<Track>()

        for (i in 0 until updates.length()) {
            val update = updates.getJSONObject(i)
            newOffset = update.getLong("update_id") + 1
            val msg = when {
                update.has("channel_post") -> update.getJSONObject("channel_post")
                update.has("message") -> update.getJSONObject("message")
                else -> null
            } ?: continue

            val track = extractTrack(msg) ?: continue
            if (track.fileUniqueId !in knownIds) {
                found.add(track)
            }
        }
        return newOffset to found
    }

    private data class RawAudio(
        val fileId: String,
        val fileUniqueId: String,
        val title: String,
        val artist: String,
        val duration: Int,
        val thumbFileId: String?
    )

    private fun extractTrack(msg: JSONObject): Track? {
        val audio = msg.optJSONObject("audio")
        val doc = msg.optJSONObject("document")

        val raw: RawAudio = when {
            audio != null -> RawAudio(
                fileId = audio.getString("file_id"),
                fileUniqueId = audio.getString("file_unique_id"),
                title = audio.optString("title", audio.optString("file_name", "Untitled")),
                artist = audio.optString("performer", "Unknown artist"),
                duration = audio.optInt("duration", 0),
                thumbFileId = audio.optJSONObject("thumb")?.optString("file_id")
            )
            doc != null && doc.optString("mime_type").startsWith("audio/") -> RawAudio(
                fileId = doc.getString("file_id"),
                fileUniqueId = doc.getString("file_unique_id"),
                title = doc.optString("file_name", "Untitled"),
                artist = "Unknown artist",
                duration = 0,
                thumbFileId = doc.optJSONObject("thumb")?.optString("file_id")
            )
            else -> return null
        }

        val chat = msg.optJSONObject("chat")
        val sourceChat = chat?.optString("title")?.takeIf { it.isNotEmpty() }
            ?: chat?.optString("username")?.takeIf { it.isNotEmpty() }
            ?: "Direct message"

        return Track(
            fileId = raw.fileId,
            fileUniqueId = raw.fileUniqueId,
            title = raw.title,
            artist = raw.artist,
            durationSec = raw.duration,
            thumbFileId = raw.thumbFileId,
            sourceChat = sourceChat,
            dateAdded = msg.optLong("date", System.currentTimeMillis() / 1000)
        )
    }

    /** Resolves a Telegram file_id to a direct, streamable/downloadable HTTPS URL. */
    suspend fun resolveFileUrl(fileId: String): String {
        val params = JSONObject().apply { put("file_id", fileId) }
        val result = call("getFile", params).getJSONObject("result")
        val path = result.getString("file_path")
        return "https://api.telegram.org/file/bot$token/$path"
    }
}
