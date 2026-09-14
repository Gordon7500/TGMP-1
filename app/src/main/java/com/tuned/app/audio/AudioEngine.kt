package com.tuned.app.audio

import android.content.Context
import android.media.*
import android.net.Uri
import kotlinx.coroutines.*
import java.nio.ByteBuffer

/**
 * Plays audio by decoding straight to PCM ourselves (MediaExtractor + MediaCodec) and writing
 * that PCM to an AudioTrack configured to match the source's sample rate / channel layout
 * exactly, instead of handing the file to a black-box player that may resample it.
 *
 * IMPORTANT — read this before assuming "bit-perfect" is guaranteed:
 * - This gives you an *exact-format, minimal-processing* output path: no forced resampling by
 *   this app, and (when a USB DAC is connected) the track is routed straight to it via
 *   AudioTrack.setPreferredDevice() using a sample rate the DAC itself reports supporting.
 * - Whether Android's audio server then opens a true hardware "direct" path with zero further
 *   touch (no mixing, no volume-curve DSP) is decided by the OS/OEM audio HAL, not by app code.
 *   Behavior genuinely varies by phone and DAC — this is the part that needs on-device testing.
 * - True bit-perfect for internal phone speakers/headphone-jack DACs is usually not possible at
 *   all — most OEMs hard-lock the internal DAC's output pipeline to a fixed system sample rate.
 * - Lossy sources (MP3/AAC/OGG) are never "bit-perfect" in the meaningful sense — that concept
 *   only really applies to lossless sources (FLAC/WAV/ALAC).
 * - Turning on the equalizer or bass boost (see AudioEffectsController) applies real digital
 *   signal processing to the samples before they reach the DAC — that's the direct opposite of
 *   bit-perfect. The two features are fundamentally in tension; enabling EQ means you are, by
 *   definition, no longer getting an untouched signal, regardless of the direct-output setting.
 * - When exclusive USB mode (see UsbAudioOutput) is active and connected, this bypasses
 *   AudioTrack/the OS mixer entirely for genuine bit-perfect output — but bass boost has no
 *   effect in that mode, since it relies on an AudioTrack session that doesn't exist there.
 */
class AudioEngine(
    private val router: OutputDeviceRouter,
    private val context: Context,
    private val equalizer: TenBandEqualizer,
    private val usbAudioOutput: com.tuned.app.usb.UsbAudioOutput
) {

    enum class State { IDLE, PLAYING, PAUSED, ENDED, ERROR }

    var state: State = State.IDLE
        private set

    var onStateChanged: ((State) -> Unit)? = null
    var onProgress: ((positionMs: Long, durationMs: Long) -> Unit)? = null
    var onAmplitude: ((Int) -> Unit)? = null // 0-32767, for the visualizer
    var onAudioSessionId: ((Int) -> Unit)? = null // fires once per playback start; needed to attach effects

    private var job: Job? = null
    private var audioTrack: AudioTrack? = null
    private var pauseRequested = false
    private var seekRequestedMs: Long? = null
    private var currentDurationMs: Long = 0

    fun play(url: String, directOutputPreferred: Boolean, scope: CoroutineScope) {
        stop()
        job = scope.launch(Dispatchers.IO) {
            try {
                playInternal(url, directOutputPreferred)
            } catch (t: Throwable) {
                state = State.ERROR
                onStateChanged?.invoke(state)
            }
        }
    }

    fun pause() { pauseRequested = true }
    fun resume() {
        pauseRequested = false
        audioTrack?.play()
        state = State.PLAYING
        onStateChanged?.invoke(state)
    }

    fun seekTo(ms: Long) { seekRequestedMs = ms }

    fun stop() {
        job?.cancel()
        job = null
        audioTrack?.let {
            try { it.pause(); it.flush(); it.release() } catch (_: Exception) {}
        }
        audioTrack = null
        if (usbAudioOutput.isActive) usbAudioOutput.disconnect()
        state = State.IDLE
    }

    private suspend fun playInternal(url: String, directOutputPreferred: Boolean) {
        val extractor = MediaExtractor()
        if (url.startsWith("content://")) {
            extractor.setDataSource(context, Uri.parse(url), null)
        } else {
            extractor.setDataSource(url)
        }

        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                trackIndex = i
                format = f
                break
            }
        }
        if (trackIndex == -1 || format == null) {
            throw IllegalStateException("No audio track found in stream")
        }
        extractor.selectTrack(trackIndex)

        val mime = format.getString(MediaFormat.KEY_MIME)!!
        val sourceSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val sourceChannels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        currentDurationMs = if (format.containsKey(MediaFormat.KEY_DURATION))
            format.getLong(MediaFormat.KEY_DURATION) / 1000 else 0

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val channelConfig = if (sourceChannels >= 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO

        // Try exclusive USB output first — bypasses AudioTrack/the OS mixer entirely. Falls
        // through to the normal AudioTrack path below if no device, no permission, or the
        // device doesn't support a usable 16-bit format near this source's sample rate.
        var usbActive = false
        if (directOutputPreferred) {
            val device = usbAudioOutput.findCandidateDevice()
            if (device != null && usbAudioOutput.hasPermission(device)) {
                usbActive = try {
                    usbAudioOutput.connect(device, sourceSampleRate)
                } catch (_: Exception) {
                    false
                }
            }
        }

        if (usbActive) {
            playViaUsb(extractor, codec, sourceChannels)
            return
        }

        // Ask the router for the best matching output device + sample rate (USB DAC if present).
        val routing = router.resolve(sourceSampleRate, directOutputPreferred)

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(routing.sampleRate)
            .setChannelMask(channelConfig)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        val minBufSize = AudioTrack.getMinBufferSize(
            routing.sampleRate, channelConfig, AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(audioFormat)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBufSize * 2)
            .build()

        routing.preferredDevice?.let { track.preferredDevice = it }
        audioTrack = track
        onAudioSessionId?.invoke(track.audioSessionId)
        equalizer.configure(routing.sampleRate, if (sourceChannels >= 2) 2 else 1)
        track.play()
        state = State.PLAYING
        onStateChanged?.invoke(state)

        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        while (!outputDone && currentCoroutineContext().isActive) {

            if (pauseRequested) {
                track.pause()
                state = State.PAUSED
                onStateChanged?.invoke(state)
                while (pauseRequested && currentCoroutineContext().isActive) delay(80)
                continue
            }

            seekRequestedMs?.let { targetMs ->
                seekRequestedMs = null
                extractor.seekTo(targetMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                codec.flush()
                track.flush()
            }

            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val inputBuffer: ByteBuffer? = codec.getInputBuffer(inIndex)
                    val sampleSize = inputBuffer?.let { extractor.readSampleData(it, 0) } ?: -1
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }

            val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
            if (outIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outIndex)
                if (outputBuffer != null && bufferInfo.size > 0) {
                    val chunk = ByteArray(bufferInfo.size)
                    outputBuffer.position(bufferInfo.offset)
                    outputBuffer.get(chunk)
                    try {
                        equalizer.process(chunk, chunk.size)
                    } catch (_: Exception) {
                        // If the EQ hits a problem, play the unprocessed chunk rather than
                        // letting it take playback down with it.
                    }
                    track.write(chunk, 0, chunk.size)
                    reportAmplitude(chunk)
                    val posMs = (bufferInfo.presentationTimeUs / 1000)
                    onProgress?.invoke(posMs, currentDurationMs)
                }
                codec.releaseOutputBuffer(outIndex, false)
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                    outputDone = true
                }
            }
        }

        state = State.ENDED
        onStateChanged?.invoke(state)

        codec.stop()
        codec.release()
        extractor.release()
        track.stop()
        track.release()
    }

    private suspend fun playViaUsb(extractor: MediaExtractor, codec: MediaCodec, sourceChannels: Int) {
        val connectedFormat = usbAudioOutput.connectedFormat
        equalizer.configure(connectedFormat?.sampleRate ?: 44100, if (sourceChannels >= 2) 2 else 1)
        state = State.PLAYING
        onStateChanged?.invoke(state)

        val bufferInfo = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        try {
            while (!outputDone && currentCoroutineContext().isActive) {

                if (pauseRequested) {
                    state = State.PAUSED
                    onStateChanged?.invoke(state)
                    while (pauseRequested && currentCoroutineContext().isActive) delay(80)
                    continue
                }

                seekRequestedMs?.let { targetMs ->
                    seekRequestedMs = null
                    extractor.seekTo(targetMs * 1000, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                    codec.flush()
                }

                if (!inputDone) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inputBuffer: ByteBuffer? = codec.getInputBuffer(inIndex)
                        val sampleSize = inputBuffer?.let { extractor.readSampleData(it, 0) } ?: -1
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIndex = codec.dequeueOutputBuffer(bufferInfo, 10_000)
                if (outIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        val chunk = ByteArray(bufferInfo.size)
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.get(chunk)
                        try {
                            equalizer.process(chunk, chunk.size)
                        } catch (_: Exception) {
                        }
                        usbAudioOutput.write(chunk)
                        reportAmplitude(chunk)
                        val posMs = (bufferInfo.presentationTimeUs / 1000)
                        onProgress?.invoke(posMs, currentDurationMs)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                }
            }
        } finally {
            state = State.ENDED
            onStateChanged?.invoke(state)
            codec.stop()
            codec.release()
            extractor.release()
            usbAudioOutput.disconnect()
        }
    }

    private fun reportAmplitude(chunk: ByteArray) {
        var maxAbs = 0
        var i = 0
        while (i + 1 < chunk.size) {
            val sample = ((chunk[i + 1].toInt() shl 8) or (chunk[i].toInt() and 0xFF)).toShort().toInt()
            val abs = kotlin.math.abs(sample)
            if (abs > maxAbs) maxAbs = abs
            i += 2
        }
        onAmplitude?.invoke(maxAbs)
    }
}
