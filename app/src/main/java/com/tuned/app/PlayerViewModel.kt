package com.tuned.app

import android.content.Context
import androidx.lifecycle.ViewModel
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

class PlayerViewModel(private val context: Context? = null) : ViewModel() {
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state
    
    private val store: Store? = context?.let { Store(it) }
    private val localTrackProvider: LocalTrackProvider? = context?.let { LocalTrackProvider(it) }

    init {
        loadTracks()
    }

    private fun loadTracks() {
        viewModelScope.launch {
            val allTracks = mutableListOf<Track>()
            
            // Load Telegram tracks
            store?.tracks?.let { allTracks.addAll(it) }
            
            // Load local storage tracks if enabled
            if (store?.localStorageEnabled == true) {
                localTrackProvider?.getLocalTracks()?.let { allTracks.addAll(it) }
            }
            
            _state.value = _state.value.copy(tracks = allTracks)
        }
    }

    fun loadLocalTracks() {
        viewModelScope.launch {
            localTrackProvider?.let { provider ->
                val localTracks = provider.getLocalTracks()
                _state.value = _state.value.copy(
                    localTracks = localTracks,
                    statusMessage = "Loaded ${localTracks.size} local tracks"
                )
                
                // Merge with existing tracks
                val allTracks = (_state.value.tracks + localTracks).distinctBy { it.fileUniqueId }
                _state.value = _state.value.copy(tracks = allTracks)
            }
        }
    }

    fun searchTracks(query: String) {
        viewModelScope.launch {
            val telegramTracks = store?.tracks?.filter {
                it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
            } ?: emptyList()
            
            val localTracks = localTrackProvider?.searchLocalTracks(query) ?: emptyList()
            val allTracks = (telegramTracks + localTracks).distinctBy { it.fileUniqueId }
            
            _state.value = _state.value.copy(tracks = allTracks)
        }
    }

    fun toggleLocalStorage(enabled: Boolean) {
        store?.localStorageEnabled = enabled
        _state.value = _state.value.copy(localStorageEnabled = enabled)
        if (enabled) {
            loadLocalTracks()
        } else {
            // Remove local tracks
            val filteredTracks = _state.value.tracks.filter { it.sourceChat != "Local Storage" }
            _state.value = _state.value.copy(tracks = filteredTracks)
        }
    }

    fun setSortOrder(order: SortOrder) { /* Implementation */ }
    fun playTrack(track: Track) { /* Implementation */ }
    fun togglePlayPause() { /* Implementation */ }
    fun playAdjacent(offset: Int) { /* Implementation */ }
    fun seekTo(positionMs: Long) { /* Implementation */ }
    fun saveSettings(token: String, channels: List<String>, directOutput: Boolean) { /* Implementation */ }
    fun setEqualizerEnabled(enabled: Boolean) { /* Implementation */ }
    fun setEqualizerBand(index: Int, level: Int) { /* Implementation */ }
    fun setBassBoostEnabled(enabled: Boolean) { /* Implementation */ }
    fun setBassBoostStrength(strength: Int) { /* Implementation */ }
}
