package com.tgmp.storage

import android.content.Context
import android.database.Cursor
import android.media.MediaStore
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AudioFile(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val path: String,
    val size: Long
)

class StorageAccessManager(private val context: Context) {

    /**
     * Query all audio files from device storage
     * Requires READ_EXTERNAL_STORAGE or READ_MEDIA_AUDIO permission
     */
    suspend fun getAudioFiles(): List<AudioFile> = withContext(Dispatchers.IO) {
        val audioFiles = mutableListOf<AudioFile>()
        
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            val cursor: Cursor? = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val pathColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                while (it.moveToNext()) {
                    val audioFile = AudioFile(
                        id = it.getLong(idColumn),
                        title = it.getString(titleColumn) ?: "Unknown",
                        artist = it.getString(artistColumn) ?: "Unknown Artist",
                        album = it.getString(albumColumn) ?: "Unknown Album",
                        duration = it.getLong(durationColumn),
                        path = it.getString(pathColumn) ?: "",
                        size = it.getLong(sizeColumn)
                    )
                    audioFiles.add(audioFile)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        audioFiles
    }

    /**
     * Get audio files from a specific directory
     */
    suspend fun getAudioFilesFromDirectory(directoryPath: String): List<AudioFile> = 
        withContext(Dispatchers.IO) {
            val audioFiles = mutableListOf<AudioFile>()
            
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.SIZE
            )

            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DATA} LIKE ?"
            val selectionArgs = arrayOf("%$directoryPath%")
            val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

            try {
                val cursor: Cursor? = context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )

                cursor?.use {
                    val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val pathColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                    while (it.moveToNext()) {
                        val audioFile = AudioFile(
                            id = it.getLong(idColumn),
                            title = it.getString(titleColumn) ?: "Unknown",
                            artist = it.getString(artistColumn) ?: "Unknown Artist",
                            album = it.getString(albumColumn) ?: "Unknown Album",
                            duration = it.getLong(durationColumn),
                            path = it.getString(pathColumn) ?: "",
                            size = it.getLong(sizeColumn)
                        )
                        audioFiles.add(audioFile)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            audioFiles
        }

    /**
     * Search for audio files by title or artist
     */
    suspend fun searchAudioFiles(query: String): List<AudioFile> = withContext(Dispatchers.IO) {
        val audioFiles = mutableListOf<AudioFile>()
        
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND (${MediaStore.Audio.Media.TITLE} LIKE ? OR ${MediaStore.Audio.Media.ARTIST} LIKE ?)"
        val selectionArgs = arrayOf("%$query%", "%$query%")

        try {
            val cursor: Cursor? = context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val durationColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val pathColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val sizeColumn = it.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                while (it.moveToNext()) {
                    val audioFile = AudioFile(
                        id = it.getLong(idColumn),
                        title = it.getString(titleColumn) ?: "Unknown",
                        artist = it.getString(artistColumn) ?: "Unknown Artist",
                        album = it.getString(albumColumn) ?: "Unknown Album",
                        duration = it.getLong(durationColumn),
                        path = it.getString(pathColumn) ?: "",
                        size = it.getLong(sizeColumn)
                    )
                    audioFiles.add(audioFile)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        audioFiles
    }
}
