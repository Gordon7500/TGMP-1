package com.tuned.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tuned.app.audio.AudioEngine
import com.tuned.app.audio.OutputDeviceRouter
import com.tuned.app.data.Store
import com.tuned.app.data.Track
import com.tuned.app.telegram.TelegramClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UiState(
    val tracks: List<Track> = emptyList(),
    val channels: List<String> = emptyList(),
    val botToken: String = "",
    val botTokenSet: Boolean = false,
    val directOutputEnabled: Boolean = true,
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val amplitude: Int = 0,
    val statusMessage: String? = null,
    val isSyncing: Boolean = false
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val store = Store(application)
    private val engine = AudioEngine(OutputDeviceRouter(application))

    private val _state = MutableStateFlow(
        UiState(
            tracks = store.tracks,
            channels = store.channels,
            botToken = store.botToken,
            botTokenSet = store.botToken.isNotBlank(),
            directOutputEnabled = store.directOutputEnabled
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var queueIndex = -1

    init {
        engine.onStateChanged = { s ->
            _state.value = _state.value.copy(isPlaying = s == AudioEngine.State.PLAYING)
        }
        engine.onProgress = { pos, dur ->
            _state.value = _state.value.copy(positionMs = pos, durationMs = dur)
        }
        engine.onAmplitude = { amp ->
            _state.value = _state.value.copy(amplitude = amp)
        }
        if (store.botToken.isNotBlank()) sync()
    }

    fun saveSettings(token: String, channels: List<String>, directOutput: Boolean) {
        store.botToken = token
        store.channels = channels
        store.directOutputEnabled = directOutput
        _state.value = _state.value.copy(
            botToken = token,
            botTokenSet = token.isNotBlank(),
            channels = channels,
            directOutputEnabled = directOutput
        )
        sync()
    }

    fun sync() {
        val token = store.botToken
        if (token.isBlank()) {
            _state.value = _state.value.copy(statusMessage = "Add your bot token in Settings first.")
            return
        }
        _state.value = _state.value.copy(isSyncing = true, statusMessage = "Checking for new tracks…")
        viewModelScope.launch {
            try {
                val client = TelegramClient(token)
                val known = store.tracks.map { it.fileUniqueId }.toSet()
                val (newOffset, newTracks) = client.fetchNewTracks(store.updateOffset, known)
                store.updateOffset = newOffset
                if (newTracks.isNotEmpty()) {
                    val merged = newTracks + store.tracks
                    store.tracks = merged
                    _state.value = _state.value.copy(
                        tracks = merged,
                        statusMessage = "Added ${newTracks.size} new track${if (newTracks.size == 1) "" else "s"}."
                    )
                } else {
                    _state.value = _state.value.copy(
                        statusMessage = "No new tracks found. Make sure the bot has joined your channels."
                    )
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(statusMessage = "Sync failed: ${e.message}")
            } finally {
                _state.value = _state.value.copy(isSyncing = false)
            }
        }
    }

    fun playTrack(track: Track) {
        val list = _state.value.tracks
        queueIndex = list.indexOfFirst { it.fileUniqueId == track.fileUniqueId }
        _state.value = _state.value.copy(currentTrack = track, statusMessage = "Loading track…")
        viewModelScope.launch {
            try {
                val client = TelegramClient(store.botToken)
                val url = client.resolveFileUrl(track.fileId)
                _state.value = _state.value.copy(statusMessage = null)
                engine.play(url, store.directOutputEnabled, viewModelScope)
            } catch (e: Exception) {
                _state.value = _state.value.copy(statusMessage = "Couldn't play track: ${e.message}")
            }
        }
    }

    fun togglePlayPause() {
        val playing = _state.value.isPlaying
        if (playing) engine.pause() else engine.resume()
    }

    fun seekTo(ms: Long) = engine.seekTo(ms)

    fun playAdjacent(delta: Int) {
        val list = _state.value.tracks
        if (list.isEmpty()) return
        var idx = queueIndex + delta
        if (idx < 0) idx = list.size - 1
        if (idx >= list.size) idx = 0
        playTrack(list[idx])
    }

    fun clearStatus() {
        _state.value = _state.value.copy(statusMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        engine.stop()
    }
}
