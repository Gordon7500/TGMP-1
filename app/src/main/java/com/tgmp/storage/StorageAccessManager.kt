package com.tgmp.storage

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AudioFile(
    val id: Long,
    val uri: Uri, // ESSENTIAL: Use this for playback 
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val path: String, // Deprecated for file access in Android 10+, keep only for UI display
    val size: Long
)

class StorageAccessManager(private val context: Context) {

    private val baseUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    private val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.SIZE
    )

    suspend fun getAudioFiles(): List<AudioFile> = withContext(Dispatchers.IO) {
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        queryMediaStore(selection, null, "${MediaStore.Audio.Media.TITLE} ASC")
    }

    suspend fun getAudioFilesFromDirectory(directoryName: String): List<AudioFile> = withContext(Dispatchers.IO) {
        val selection: String
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29+: Use RELATIVE_PATH. Note: directoryName should be like "Music/MyFolder/"
            selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.RELATIVE_PATH} LIKE ?"
        } else {
            // API 26-28: Fall back to DATA absolute path
            selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DATA} LIKE ?"
        }
        
        val selectionArgs = arrayOf("%$directoryName%")
        queryMediaStore(selection, selectionArgs, "${MediaStore.Audio.Media.TITLE} ASC")
    }

    suspend fun searchAudioFiles(query: String): List<AudioFile> = withContext(Dispatchers.IO) {
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND (${MediaStore.Audio.Media.TITLE} LIKE ? OR ${MediaStore.Audio.Media.ARTIST} LIKE ?)"
        val selectionArgs = arrayOf("%$query%", "%$query%")
        
        queryMediaStore(selection, selectionArgs, "${MediaStore.Audio.Media.TITLE} ASC")
    }

    private fun queryMediaStore(
        selection: String,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): List<AudioFile> {
        val audioFiles = mutableListOf<AudioFile>()

        try {
            context.contentResolver.query(
                baseUri,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    // Construct the Uri dynamically
                    val uri = ContentUris.withAppendedId(baseUri, id)

                    audioFiles.add(
                        AudioFile(
                            id = id,
                            uri = uri,
                            title = cursor.getString(titleColumn) ?: "Unknown",
                            artist = cursor.getString(artistColumn) ?: "Unknown Artist",
                            album = cursor.getString(albumColumn) ?: "Unknown Album",
                            duration = cursor.getLong(durationColumn),
                            path = cursor.getString(pathColumn) ?: "",
                            size = cursor.getLong(sizeColumn)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // In a production app, do not blindly swallow this. 
            // Re-throw or use a Result wrapper to handle permission denials in the UI.
            e.printStackTrace()
        }

        return audioFiles
    }
}
