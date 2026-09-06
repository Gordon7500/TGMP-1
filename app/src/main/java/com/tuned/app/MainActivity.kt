@Composable
private fun AppRoot(viewModel: PlayerViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }

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
            onSave = { token, channels, directOutput ->
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
            onEqualizerEnabledChange = { viewModel.setEqualizerEnabled(it) },
            onBandChange = { index, level -> viewModel.setEqualizerBand(index, level) },
            onBassBoostEnabledChange = { viewModel.setBassBoostEnabled(it) },
            onBassBoostStrengthChange = { viewModel.setBassBoostStrength(it) }
        )
    }
}
