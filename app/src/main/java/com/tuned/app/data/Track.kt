package com.tuned.app.data

data class Track(
    val fileId: String,
    val fileUniqueId: String,
    val title: String,
    val artist: String,
    val durationSec: Int,
    val thumbFileId: String?,
    val sourceChat: String,
    val dateAdded: Long,
    val isLocal: Boolean = false,
    val localUri: String? = null,
    /** Set only for tracks pulled through the real-account TDLib login, not the bot. */
    val tdFileId: Int? = null
)
