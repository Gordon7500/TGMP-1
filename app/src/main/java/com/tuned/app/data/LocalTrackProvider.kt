package com.tuned.app.data

import android.content.Context
import com.tgmp.storage.StorageAccessManager
import com.tgmp.storage.AudioFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Converts local device audio files to Track objects for display in the library.
 * Bridges StorageAccessManager with the existing Track data model.
 */
class LocalTrackProvider(context: Context) {
    private val storageManager = StorageAccessManager(context)

    /**
     * Get all local audio files and convert them to Track objects
     */
    suspend fun getLocalTracks(): List<Track> = withContext(Dispatchers.IO) {
        val audioFiles = storageManager.getAudioFiles()
        audioFiles.map { convertAudioFileToTrack(it) }
    }

    /**
     * Search local audio files by title or artist
     */
    suspend fun searchLocalTracks(query: String): List<Track> = withContext(Dispatchers.IO) {
        val audioFiles = storageManager.searchAudioFiles(query)
        audioFiles.map { convertAudioFileToTrack(it) }
    }

    /**
     * Get audio files from a specific directory
     */
    suspend fun getTracksFromDirectory(directoryPath: String): List<Track> =
        withContext(Dispatchers.IO) {
            val audioFiles = storageManager.getAudioFilesFromDirectory(directoryPath)
            audioFiles.map { convertAudioFileToTrack(it) }
        }

    /**
     * Convert AudioFile from storage to Track model
     */
    private fun convertAudioFileToTrack(audioFile: AudioFile): Track {
        return Track(
            fileId = audioFile.id.toString(),
            fileUniqueId = audioFile.id.toString(),
            title = audioFile.title,
            artist = audioFile.artist,
            durationSec = (audioFile.duration / 1000).toInt(),
            thumbFileId = null,
            sourceChat = "Local Storage",
            dateAdded = System.currentTimeMillis()
        )
    }
}
