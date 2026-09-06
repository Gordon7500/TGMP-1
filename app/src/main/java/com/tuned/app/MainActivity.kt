package com.tuned.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuned.app.ui.*

class MainActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TunedTheme {
                Surface(color = Black, modifier = Modifier.fillMaxSize()) {
                    AppRoot(viewModel)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(viewModel: PlayerViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LibraryScreen(
            tracks = state.tracks,
            currentTrackId = state.currentTrack?.fileUniqueId,
            onTrackClick = { viewModel.playTrack(it) },
            onOpenSettings = { showSettings = true },
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
            onSave = { token, channels, directOutput ->
                viewModel.saveSettings(token, channels, directOutput)
                showSettings = false
            }
        )
    }
}
