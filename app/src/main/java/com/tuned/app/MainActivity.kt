package com.tuned.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tuned.app.ui.EqualizerSheet
import com.tuned.app.ui.LibraryScreen
import com.tuned.app.ui.PlayerBar
import com.tuned.app.ui.SettingsSheet

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppRoot()
        }
    }
}

@Composable
private fun AppRoot(viewModel: PlayerViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.toggleLocalStorage(true)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LibraryScreen(
            tracks = state.tracks,
            currentTrackId = state.currentTrack?.fileUniqueId,
            sortOrder = state.sortOrder,
            onSortOrderChange = { viewModel.setSortOrder(it) },
            onTrackClick = { viewModel.playTrack(it) },
            onOpenSettings = { showSettings = true },
            onOpenEqualizer = { showEqualizer = true },
            onConnectClick = { showSettings = true },
            statusMessage = state.statusMessage,
            modifier = Modifier.fillMaxSize()
        )

        state.currentTrack?.let { track ->
            PlayerBar(
                track = track,
                isPlaying = state.isPlaying,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                amplitude = state.amplitude,
                onPlayPause = { viewModel.togglePlayPause() },
                onNext = { viewModel.playAdjacent(1) },
                onPrev = { viewModel.playAdjacent(-1) },
                onSeek = { viewModel.seekTo(it) },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    if (showSettings) {
        SettingsSheet(
            initialToken = state.botToken,
            initialChannels = state.channels,
            initialDirectOutput = state.directOutputEnabled,
            onDismiss = { showSettings = false },
            onSave = { token: String, channels: List<String>, directOutput: Boolean ->
                viewModel.saveSettings(token, channels, directOutput)
                showSettings = false
            }
        )
    }

    if (showEqualizer) {
        EqualizerSheet(
            equalizerEnabled = state.equalizerEnabled,
            bands = state.equalizerBands,
            bandRangeMb = state.equalizerRangeMb,
            bassBoostEnabled = state.bassBoostEnabled,
            bassBoostStrength = state.bassBoostStrength,
            onDismiss = { showEqualizer = false },
            onEqualizerEnabledChange = { enabled: Boolean -> viewModel.setEqualizerEnabled(enabled) },
            onBandChange = { index: Int, level: Int -> viewModel.setEqualizerBand(index, level) },
            onBassBoostEnabledChange = { enabled: Boolean -> viewModel.setBassBoostEnabled(enabled) },
            onBassBoostStrengthChange = { strength: Int -> viewModel.setBassBoostStrength(strength) }
        )
    }
}
