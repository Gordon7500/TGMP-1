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
    /** Stable identifiers for a TDLib-account-sourced track. TDLib's raw file IDs are only
     *  valid for one login session, so we resolve a fresh one from these right before playing
     *  instead of persisting the ephemeral file ID itself. */
    val tdChatId: Long? = null,
    val tdMessageId: Long? = null,
    /** e.g. "192kbps" or "FLAC" — best-effort, shown in the library list. */
    val qualityLabel: String? = null
)
