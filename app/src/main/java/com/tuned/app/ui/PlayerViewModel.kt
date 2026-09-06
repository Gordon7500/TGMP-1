package com.tuned.app.ui

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tuned.app.audio.AudioEffectsController
import com.tuned.app.audio.PlaybackService
import com.tuned.app.audio.ServicePlaybackState
import com.tuned.app.data.Store
import com.tuned.app.data.Track
import com.tuned.app.telegram.TelegramClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

enum class SortOrder(val label: String) {
    DATE_ADDED("Recently added"),
    TITLE("Title A-Z"),
    ARTIST("Artist A-Z"),
    DURATION("Duration")
}

data class EqualizerBand(val index: Int, val label: String, val levelMb: Int)

data class UiState(
    val tracks: List<Track> = emptyList(),
    val channels: List<String> = emptyList(),
    val botToken: String = "",
    val botTokenSet: Boolean = false,
    val directOutputEnabled: Boolean = true,
    val sortOrder: SortOrder = SortOrder.DATE_ADDED,
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val amplitude: Int = 0,
    val statusMessage: String? = null,
    val isSyncing: Boolean = false,
    val equalizerEnabled: Boolean = false,
    val equalizerBands: List<EqualizerBand> = emptyList(),
    val equalizerRangeMb: IntRange = -1500..1500,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Int = 0
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val store = Store(application)

    private val _state = MutableStateFlow(
        UiState(
            tracks = sortedTracks(store.tracks, sortOrderFrom(store.sortOrder)),
            channels = store.channels,
            botToken = store.botToken,
            botTokenSet = store.botToken.isNotBlank(),
            directOutputEnabled = store.directOutputEnabled,
            sortOrder = sortOrderFrom(store.sortOrder),
            equalizerEnabled = store.equalizerEnabled,
            bassBoostEnabled = store.bassBoostEnabled,
            bassBoostStrength = store.bassBoostStrength
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var playbackService: PlaybackService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as PlaybackService.LocalBinder).getService()
            playbackService = service
            bound = true
            refreshEqualizerInfoFrom(service.effects)
            viewModelScope.launch {
                service.state.collect { playback -> applyPlaybackState(playback) }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackService = null
            bound = false
        }
    }

    init {
        val app = getApplication<Application>()
        app.bindService(Intent(app, PlaybackService::class.java), connection, Context.BIND_AUTO_CREATE)
        if (store.botToken.isNotBlank()) sync()
    }

    private var lastAppliedTrackId: String? = null

    private fun applyPlaybackState(p: ServicePlaybackState) {
        _state.value = _state.value.copy(
            currentTrack = p.currentTrack,
            isPlaying = p.isPlaying,
            positionMs = p.positionMs,
            durationMs = p.durationMs,
            amplitude = p.amplitude,
            statusMessage = p.statusMessage ?: _state.value.statusMessage
        )
        if (p.currentTrack?.fileUniqueId != lastAppliedTrackId && _state.value.equalizerBands.isEmpty()) {
            lastAppliedTrackId = p.currentTrack?.fileUniqueId
            playbackService?.effects?.let { refreshEqualizerInfoFrom(it) }
        }
    }

    private fun refreshEqualizerInfoFrom(effects: AudioEffectsController) {
        val bandCount = effects.numberOfBands
        if (bandCount == 0) return // no session yet; refreshed again once a track starts playing
        val bands = (0 until bandCount).map { i ->
            EqualizerBand(i, effects.bandFrequencyLabel(i), effects.currentBandLevel(i))
        }
        _state.value = _state.value.copy(
            equalizerBands = bands,
            equalizerRangeMb = effects.bandLevelRangeMb()
        )
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

    fun setSortOrder(order: SortOrder) {
        store.sortOrder = order.name
        _state.value = _state.value.copy(
            sortOrder = order,
            tracks = sortedTracks(store.tracks, order)
        )
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
                        tracks = sortedTracks(merged, _state.value.sortOrder),
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
        val idx = list.indexOfFirst { it.fileUniqueId == track.fileUniqueId }
        if (idx == -1) return
        ensureServiceStarted()
        playbackService?.setQueueAndPlay(list, idx)
    }

    fun togglePlayPause() {
        ensureServiceStarted()
        playbackService?.togglePlayPause()
    }

    fun seekTo(ms: Long) = playbackService?.seekTo(ms)
    fun playAdjacent(delta: Int) {
        ensureServiceStarted()
        if (delta >= 0) playbackService?.skipNext() else playbackService?.skipPrevious()
    }

    private fun ensureServiceStarted() {
        val app = getApplication<Application>()
        ContextCompat.startForegroundService(app, Intent(app, PlaybackService::class.java))
    }

    // ---------- Equalizer / bass boost ----------

    fun setEqualizerEnabled(enabled: Boolean) {
        playbackService?.effects?.setEqualizerEnabled(enabled)
        _state.value = _state.value.copy(equalizerEnabled = enabled)
    }

    fun setEqualizerBand(index: Int, levelMb: Int) {
        playbackService?.effects?.setBandLevel(index, levelMb)
        val bands = _state.value.equalizerBands.map {
            if (it.index == index) it.copy(levelMb = levelMb) else it
        }
        _state.value = _state.value.copy(equalizerBands = bands)
    }

    fun setBassBoostEnabled(enabled: Boolean) {
        playbackService?.effects?.setBassBoostEnabled(enabled)
        _state.value = _state.value.copy(bassBoostEnabled = enabled)
    }

    fun setBassBoostStrength(strength: Int) {
        playbackService?.effects?.setBassBoostStrength(strength)
        _state.value = _state.value.copy(bassBoostStrength = strength)
    }

    fun clearStatus() {
        _state.value = _state.value.copy(statusMessage = null)
    }

    override fun onCleared() {
        super.onCleared()
        if (bound) {
            getApplication<Application>().unbindService(connection)
            bound = false
        }
    }

    companion object {
        fun sortOrderFrom(name: String): SortOrder =
            SortOrder.entries.find { it.name == name } ?: SortOrder.DATE_ADDED

        fun sortedTracks(tracks: List<Track>, order: SortOrder): List<Track> = when (order) {
            SortOrder.DATE_ADDED -> tracks.sortedByDescending { it.dateAdded }
            SortOrder.TITLE -> tracks.sortedBy { it.title.lowercase() }
            SortOrder.ARTIST -> tracks.sortedBy { it.artist.lowercase() }
            SortOrder.DURATION -> tracks.sortedByDescending { it.durationSec }
        }
    }
}
