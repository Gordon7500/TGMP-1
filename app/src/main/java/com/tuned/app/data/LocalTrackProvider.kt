package com.tuned.app.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

/** Reads audio files already on the device, via the standard MediaStore index (no file-path scanning needed). */
class LocalTrackProvider(private val context: Context) {

    fun getLocalTracks(): List<Track> {
        val tracks = mutableListOf<Track>()

        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.MIME_TYPE
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        context.contentResolver.query(collection, projection, selection, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri = ContentUris.withAppendedId(collection, id).toString()
                val title = cursor.getString(titleCol) ?: cursor.getString(nameCol) ?: "Untitled"
                val artist = cursor.getString(artistCol)?.takeIf { it.isNotBlank() && it != "<unknown>" }
                    ?: "Unknown artist"
                val durationMs = cursor.getLong(durationCol)
                val dateAdded = cursor.getLong(dateCol)
                val sizeBytes = cursor.getLong(sizeCol).takeIf { it > 0 }
                val mimeType = cursor.getString(mimeCol)
                val durationSec = (durationMs / 1000).toInt()

                tracks.add(
                    Track(
                        fileId = uri,
                        fileUniqueId = "local:$id",
                        title = title,
                        artist = artist,
                        durationSec = durationSec,
                        thumbFileId = null,
                        sourceChat = "This device",
                        dateAdded = dateAdded,
                        isLocal = true,
                        localUri = uri,
                        qualityLabel = QualityLabel.estimate(cursor.getString(nameCol), mimeType, sizeBytes, durationSec)
                    )
                )
            }
        }
        return tracks
    }
}
