package com.tuned.app.audio

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import com.tuned.app.data.Store
import com.tuned.app.data.Track
import com.tuned.app.telegram.TdAuthState
import com.tuned.app.telegram.TdLibSessionManager
import com.tuned.app.telegram.TelegramClient
import com.tuned.app.telegram.TelegramUserAuth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

data class ServicePlaybackState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val amplitude: Int = 0,
    val statusMessage: String? = null,
    val formatLabel: String? = null
)

class PlaybackService : Service() {

    private val binder = LocalBinder()
    private lateinit var store: Store
    private lateinit var engine: AudioEngine
    lateinit var effects: AudioEffectsController
        private set

    private lateinit var mediaSession: MediaSessionCompat
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var queue: List<Track> = emptyList()
    private var currentIndex: Int = -1
    private var isForeground = false

    private val _state = MutableStateFlow(ServicePlaybackState())
    val state: StateFlow<ServicePlaybackState> = _state.asStateFlow()

    inner class LocalBinder : Binder() {
        fun getService(): PlaybackService = this@PlaybackService
    }

    override fun onCreate() {
        super.onCreate()
        store = Store(applicationContext)
        effects = AudioEffectsController(store)
        engine = AudioEngine(
            OutputDeviceRouter(applicationContext),
            applicationContext,
            effects.bandEqualizer,
            com.tuned.app.usb.UsbAudioOutput(applicationContext)
        )
        engine.onStateChanged = { s -> onEngineStateChanged(s) }
        engine.onProgress = { pos, dur ->
            _state.value = _state.value.copy(positionMs = pos, durationMs = dur)
            updatePlaybackState()
        }
        engine.onAmplitude = { amp -> _state.value = _state.value.copy(amplitude = amp) }
        engine.onAudioSessionId = { id -> effects.onSessionIdChanged(id) }
        engine.onFormatInfo = { sampleRateHz, bitDepth, channels ->
            val khz = sampleRateHz / 1000.0
            val khzText = if (khz == khz.toInt().toDouble()) "${khz.toInt()}kHz" else "${"%.1f".format(khz)}kHz"
            _state.value = _state.value.copy(formatLabel = "$bitDepth-bit / $khzText")
        }

        createNotificationChannel()
        setupMediaSession()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
        return START_NOT_STICKY
    }

    // ---------- Public control surface (used by both the UI and the MediaSession callback) ----------

    fun setQueueAndPlay(tracks: List<Track>, startIndex: Int) {
        queue = tracks
        currentIndex = startIndex
        playCurrent()
    }

    fun togglePlayPause() {
        if (_state.value.isPlaying) engine.pause() else engine.resume()
    }

    fun skipNext() = skip(1)
    fun skipPrevious() = skip(-1)

    fun seekTo(ms: Long) = engine.seekTo(ms)

    private fun skip(delta: Int) {
        if (queue.isEmpty()) return
        var idx = currentIndex + delta
        if (idx < 0) idx = queue.size - 1
        if (idx >= queue.size) idx = 0
        currentIndex = idx
        playCurrent()
    }

    private fun playCurrent() {
        val track = queue.getOrNull(currentIndex) ?: return
        _state.value = _state.value.copy(currentTrack = track, statusMessage = "Loading track…", formatLabel = null)
        updateMetadata(track)
        startForegroundIfNeeded(track)

        if (track.isLocal && track.localUri != null) {
            _state.value = _state.value.copy(statusMessage = null)
            engine.play(track.localUri, store.directOutputEnabled, store.usbExclusiveEnabled, serviceScope)
            return
        }

        if (track.tdFileId != null) {
            serviceScope.launch {
                val auth = ensureTdAuth()
                if (auth == null) {
                    _state.value = _state.value.copy(statusMessage = "Telegram account isn't set up — open Settings to log in.")
                    return@launch
                }
                val ready = waitForReady(auth, timeoutMs = 15_000)
                if (!ready) {
                    _state.value = _state.value.copy(statusMessage = "Telegram account session isn't ready yet — try again in a moment.")
                    return@launch
                }
                val path = try {
                    auth.resolveLocalFilePath(track.tdFileId)
                } catch (e: Exception) {
                    _state.value = _state.value.copy(statusMessage = "Couldn't download this track: ${e.message}")
                    return@launch
                }
                if (path == null) {
                    _state.value = _state.value.copy(statusMessage = "Couldn't download this track.")
                    return@launch
                }
                _state.value = _state.value.copy(statusMessage = null)
                engine.play(path, store.directOutputEnabled, store.usbExclusiveEnabled, serviceScope)
            }
            return
        }

        serviceScope.launch {
            try {
                val client = TelegramClient(store.botToken)
                val url = client.resolveFileUrl(track.fileId)
                _state.value = _state.value.copy(statusMessage = null)
                engine.play(url, store.directOutputEnabled, store.usbExclusiveEnabled, serviceScope)
            } catch (e: Exception) {
                _state.value = _state.value.copy(statusMessage = "Couldn't play track: ${e.message}")
            }
        }
    }

    /** Recreates the TDLib session from saved credentials if it's missing (e.g. after the app's
     *  process was restarted by Android and the in-memory session was lost) instead of just
     *  failing and telling the user to log in again when they never actually logged out. */
    private fun ensureTdAuth(): TelegramUserAuth? {
        TdLibSessionManager.auth?.let { return it }
        if (store.tdApiId == 0 || store.tdApiHash.isBlank()) return null
        return TdLibSessionManager.getOrCreate(applicationContext, store.tdApiId, store.tdApiHash)
    }

    /** A freshly (re)created session needs a moment to restore itself from disk before it's
     *  usable — this waits for that instead of failing immediately. */
    private suspend fun waitForReady(auth: TelegramUserAuth, timeoutMs: Long): Boolean {
        if (auth.authState.value is TdAuthState.Ready) return true
        val result = withTimeoutOrNull(timeoutMs) {
            auth.authState.first { it is TdAuthState.Ready || it is TdAuthState.Error }
        }
        return result is TdAuthState.Ready
    }

    private fun onEngineStateChanged(s: AudioEngine.State) {
        _state.value = _state.value.copy(isPlaying = s == AudioEngine.State.PLAYING)
        updatePlaybackState()
        if (s == AudioEngine.State.ENDED) skipNext()
        updateNotification()
    }

    // ---------- MediaSession / lock-screen & notification controls ----------

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "TunedPlaybackSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = togglePlayPauseIfNeeded(shouldPlay = true)
                override fun onPause() = togglePlayPauseIfNeeded(shouldPlay = false)
                override fun onSkipToNext() = skipNext()
                override fun onSkipToPrevious() = skipPrevious()
                override fun onSeekTo(pos: Long) = seekTo(pos)
            })
            isActive = true
        }
    }

    private fun togglePlayPauseIfNeeded(shouldPlay: Boolean) {
        if (shouldPlay != _state.value.isPlaying) togglePlayPause()
    }

    private fun updateMetadata(track: Track) {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.durationSec * 1000L)
            .build()
        mediaSession.setMetadata(metadata)
    }

    private fun updatePlaybackState() {
        val s = _state.value
        val stateInt = if (s.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(stateInt, s.positionMs, 1f)
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    // ---------- Foreground notification ----------

    private fun startForegroundIfNeeded(track: Track) {
        val notification = buildNotification(track)
        if (!isForeground) {
            startForeground(NOTIFICATION_ID, notification)
            isForeground = true
        } else {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val track = _state.value.currentTrack ?: return
        if (!isForeground) return
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(track))
    }

    private fun buildNotification(track: Track): Notification {
        val isPlaying = _state.value.isPlaying

        val prevAction = NotificationCompat.Action(
            android.R.drawable.ic_media_previous, "Previous",
            MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
        )
        val playPauseAction = NotificationCompat.Action(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isPlaying) "Pause" else "Play",
            MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY_PAUSE)
        )
        val nextAction = NotificationCompat.Action(
            android.R.drawable.ic_media_next, "Next",
            MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(isPlaying)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(prevAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Playback", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.stop()
        effects.release()
        mediaSession.isActive = false
        mediaSession.release()
        serviceScope.cancel()
    }

    companion object {
        const val CHANNEL_ID = "tuned_playback"
        const val NOTIFICATION_ID = 1
    }
}
