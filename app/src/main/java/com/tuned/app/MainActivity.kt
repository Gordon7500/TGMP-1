package com.tuned.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
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

private fun audioPermissionName(): String =
    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE

@Composable
private fun AppRoot(viewModel: PlayerViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showSettings by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showTelegramLogin by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.setLocalStorageEnabled(true)
        // If denied, the switch simply stays off — no further action needed.
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
            localStorageEnabled = state.localStorageEnabled,
            onDismiss = { showSettings = false },
            onSave = { token, channels, directOutput ->
                viewModel.saveSettings(token, channels, directOutput)
                showSettings = false
            },
            onOpenAccountLogin = {
                showSettings = false
                showTelegramLogin = true
            },
            onRequestLocalStorage = {
                val permission = audioPermissionName()
                val alreadyGranted = ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
                if (alreadyGranted) {
                    viewModel.setLocalStorageEnabled(true)
                } else {
                    permissionLauncher.launch(permission)
                }
            },
            onDisableLocalStorage = { viewModel.setLocalStorageEnabled(false) }
        )
    }

    if (showTelegramLogin) {
        TelegramLoginSheet(onDismiss = { showTelegramLogin = false })
    }

    if (showEqualizer) {
        EqualizerSheet(
            equalizerEnabled = state.equalizerEnabled,
            bands = state.equalizerBands,
            bandRangeMb = state.equalizerRangeMb,
            bassBoostEnabled = state.bassBoostEnabled,
            bassBoostStrength = state.bassBoostStrength,
            onDismiss = { showEqualizer = false },
            onEqualizerEnabledChange = { viewModel.setEqualizerEnabled(it) },
            onBandChange = { index, level -> viewModel.setEqualizerBand(index, level) },
            onBassBoostEnabledChange = { viewModel.setBassBoostEnabled(it) },
            onBassBoostStrengthChange = { viewModel.setBassBoostStrength(it) }
        )
    }
}
