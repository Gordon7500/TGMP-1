package com.tuned.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Simple JSON-backed local storage. Deliberately avoids Room/DataStore-with-serialization
 * to keep the dependency surface (and therefore the cloud-build failure surface) small.
 */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("tuned_store", Context.MODE_PRIVATE)

    var botToken: String
        get() = prefs.getString("bot_token", "") ?: ""
        set(value) = prefs.edit().putString("bot_token", value).apply()

    var updateOffset: Long
        get() = prefs.getLong("update_offset", 0L)
        set(value) = prefs.edit().putLong("update_offset", value).apply()

    var directOutputEnabled: Boolean
        get() = prefs.getBoolean("direct_output", true)
        set(value) = prefs.edit().putBoolean("direct_output", value).apply()

    var localStorageEnabled: Boolean
        get() = prefs.getBoolean("local_storage_enabled", false)
        set(value) = prefs.edit().putBoolean("local_storage_enabled", value).apply()

    /** From https://my.telegram.org — required once, for the real-account login (TDLib) path. */
    var tdApiId: Int
        get() = prefs.getInt("td_api_id", 0)
        set(value) = prefs.edit().putInt("td_api_id", value).apply()

    var tdApiHash: String
        get() = prefs.getString("td_api_hash", "") ?: ""
        set(value) = prefs.edit().putString("td_api_hash", value).apply()

    /** One of: "title", "artist", "dateAdded", "duration" */
    var sortOrder: String
        get() = prefs.getString("sort_order", "dateAdded") ?: "dateAdded"
        set(value) = prefs.edit().putString("sort_order", value).apply()

    var equalizerEnabled: Boolean
        get() = prefs.getBoolean("eq_enabled", false)
        set(value) = prefs.edit().putBoolean("eq_enabled", value).apply()

    /** Band levels in millibels, one entry per band. Empty until the device's band count is known. */
    var equalizerBandLevels: List<Int>
        get() {
            val raw = prefs.getString("eq_bands", "[]") ?: "[]"
            val arr = JSONArray(raw)
            return (0 until arr.length()).map { arr.getInt(it) }
        }
        set(value) {
            val arr = JSONArray()
            value.forEach { arr.put(it) }
            prefs.edit().putString("eq_bands", arr.toString()).apply()
        }

    /** 0-1000, matches android.media.audiofx.BassBoost's strength range. */
    var bassBoostStrength: Int
        get() = prefs.getInt("bass_boost_strength", 0)
        set(value) = prefs.edit().putInt("bass_boost_strength", value).apply()

    var bassBoostEnabled: Boolean
        get() = prefs.getBoolean("bass_boost_enabled", false)
        set(value) = prefs.edit().putBoolean("bass_boost_enabled", value).apply()

    var channels: List<String>
        get() {
            val raw = prefs.getString("channels", "[]") ?: "[]"
            val arr = JSONArray(raw)
            return (0 until arr.length()).map { arr.getString(it) }
        }
        set(value) {
            val arr = JSONArray()
            value.forEach { arr.put(it) }
            prefs.edit().putString("channels", arr.toString()).apply()
        }

    var tracks: List<Track>
        get() {
            val raw = prefs.getString("tracks", "[]") ?: "[]"
            val arr = JSONArray(raw)
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Track(
                    fileId = o.getString("fileId"),
                    fileUniqueId = o.getString("fileUniqueId"),
                    title = o.getString("title"),
                    artist = o.getString("artist"),
                    durationSec = o.optInt("durationSec", 0),
                    thumbFileId = o.optString("thumbFileId", null.toString()).takeIf { it != "null" },
                    sourceChat = o.optString("sourceChat", ""),
                    dateAdded = o.optLong("dateAdded", 0L)
                )
            }
        }
        set(value) {
            val arr = JSONArray()
            value.forEach { t ->
                val o = JSONObject()
                o.put("fileId", t.fileId)
                o.put("fileUniqueId", t.fileUniqueId)
                o.put("title", t.title)
                o.put("artist", t.artist)
                o.put("durationSec", t.durationSec)
                o.put("thumbFileId", t.thumbFileId)
                o.put("sourceChat", t.sourceChat)
                o.put("dateAdded", t.dateAdded)
                arr.put(o)
            }
            prefs.edit().putString("tracks", arr.toString()).apply()
        }
}
