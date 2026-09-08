package com.tuned.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tuned.app.data.Track
import com.tuned.app.data.LocalTrackProvider
import com.tuned.app.data.Store
import com.tuned.app.ui.SortOrder
import com.tuned.app.ui.EqualizerBand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class PlayerState(
    val tracks: List<Track> = emptyList(),
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val amplitude: Int = 0,
    val sortOrder: SortOrder = SortOrder.DATE_ADDED,
    val statusMessage: String = "",
    val botToken: String = "",
    val channels: List<String> = emptyList(),
    val directOutputEnabled: Boolean = false,
    val equalizerEnabled: Boolean = false,
    val equalizerBands: List<EqualizerBand> = emptyList(),
    val equalizerRangeMb: IntRange = 0..0,
    val bassBoostEnabled: Boolean = false,
    val bassBoostStrength: Int = 0,
    val localStorageEnabled: Boolean = false,
    val localTracks: List<Track> = emptyList()
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val store = Store(application)
    private val localTrackProvider = LocalTrackProvider(application)

    // === THIS IS THE FIX - LOCAL STORAGE ENABLED ===
    private val localStorageEnabled = store.localStorageEnabled

    private val _state = MutableStateFlow(PlayerState(
        localStorageEnabled = localStorageEnabled
    ))
    val state: StateFlow<PlayerState> = _state

    init {
        loadTracks()
    }

    private fun loadTracks() {
        viewModelScope.launch {
            val allTracks = mutableListOf<Track>()
            
            // Load Telegram tracks safely
            store.tracks?.let { allTracks.addAll(it) }
            
            // Load local storage tracks if enabled in preferences
            if (localStorageEnabled) {
                val localTracks = localTrackProvider.getLocalTracks()
                allTracks.addAll(localTracks)
                _state.value = _state.value.copy(localTracks = localTracks)
            }
            
            _state.value = _state.value.copy(tracks = allTracks.distinctBy { it.fileUniqueId })
        }
    }

    fun loadLocalTracks() {
        viewModelScope.launch {
            val localTracks = localTrackProvider.getLocalTracks()
            _state.value = _state.value.copy(
                localTracks = localTracks,
                statusMessage = "Loaded ${localTracks.size} local tracks"
            )
            
            // Merge with existing non-local tracks
            val currentTelegramTracks = _state.value.tracks.filter { it.sourceChat != "Local Storage" }
            val allTracks = (currentTelegramTracks + localTracks).distinctBy { it.fileUniqueId }
            _state.value = _state.value.copy(tracks = allTracks)
        }
    }

    fun searchTracks(query: String) {
        viewModelScope.launch {
            val telegramTracks = store.tracks?.filter {
                it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
            } ?: emptyList()
            
            val localTracks = localTrackProvider.searchLocalTracks(query)
            val allTracks = (telegramTracks + localTracks).distinctBy { it.fileUniqueId }
            
            _state.value = _state.value.copy(tracks = allTracks)
        }
    }

    fun toggleLocalStorage(enabled: Boolean) {
        store.localStorageEnabled = enabled
        _state.value = _state.value.copy(localStorageEnabled = enabled)
        
        if (enabled) {
            loadLocalTracks()
        } else {
            val filteredTracks = _state.value.tracks.filter { it.sourceChat != "Local Storage" }
            _state.value = _state.value.copy(
                tracks = filteredTracks,
                localTracks = emptyList()
            )
        }
    }

    // All your missing stub functions
    fun setSortOrder(order: SortOrder) { /* TODO: Implement */ }
    fun playTrack(track: Track) { /* TODO: Implement */ }
    fun togglePlayPause() { /* TODO: Implement */ }
    fun playAdjacent(offset: Int) { /* TODO: Implement */ }
    fun seekTo(positionMs: Long) { /* TODO: Implement */ }
    fun saveSettings(token: String, channels: List<String>, directOutput: Boolean) { /* TODO: Implement */ }
    fun setEqualizerEnabled(enabled: Boolean) { /* TODO: Implement */ }
    fun setEqualizerBand(index: Int, level: Int) { /* TODO: Implement */ }
    fun setBassBoostEnabled(enabled: Boolean) { /* TODO: Implement */ }
    fun setBassBoostStrength(strength: Int) { /* TODO: Implement */ }
}
