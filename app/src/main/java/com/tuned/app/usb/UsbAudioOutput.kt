package com.tuned.app.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbRequest
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Exclusive USB Audio Class output: claims a UAC1 device's audio-streaming interface directly
 * and pushes PCM to it via isochronous transfers, bypassing Android's normal audio mixer/output
 * pipeline entirely (AudioTrack is not involved at all in this path).
 *
 * Honest scope, read before assuming a given DAC will work:
 * - UAC 1.0 and UAC 2.0. UAC2's sample rate is discovered via a live query to the device (a
 *   "clock source" entity) rather than a fixed descriptor list — this assumes a single clock
 *   source per device, which covers most consumer USB DACs but not every possible design.
 * - Synchronous endpoints only — no clock-feedback (asynchronous) handling. Devices that require
 *   feedback-based clock correction may drift or glitch over long playback.
 * - Targets 16-bit PCM. Devices whose only advertised format is 24/32-bit are not selected.
 * - This is the least-tested code in the whole project — real-device behavior varies by DAC in
 *   ways that are very hard to predict without one in hand.
 */
class UsbAudioOutput(private val context: Context) {

    companion object {
        private const val ACTION_USB_PERMISSION = "com.tuned.app.USB_PERMISSION"
    }

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var connection: UsbDeviceConnection? = null
    private var streamThread: Thread? = null
    private val running = AtomicBoolean(false)
    private val pcmQueue = ArrayBlockingQueue<ByteArray>(32)

    data class ConnectedFormat(val sampleRate: Int, val channels: Int, val bitDepth: Int)

    var connectedFormat: ConnectedFormat? = null
        private set

    val isActive: Boolean get() = connectedFormat != null

    fun findCandidateDevice(): UsbDevice? =
        usbManager.deviceList.values.firstOrNull { device ->
            (0 until device.interfaceCount).any { i ->
                val iface = device.getInterface(i)
                iface.interfaceClass == UsbConstants.USB_CLASS_AUDIO
            }
        }

    fun hasPermission(device: UsbDevice): Boolean = usbManager.hasPermission(device)

    /** Triggers Android's system USB permission dialog. Result arrives via the returned deferred. */
    suspend fun requestPermission(device: UsbDevice): Boolean {
        if (usbManager.hasPermission(device)) return true
        val result = CompletableDeferred<Boolean>()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                if (intent?.action != ACTION_USB_PERMISSION) return
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                context.unregisterReceiver(this)
                result.complete(granted)
            }
        }

        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(receiver, IntentFilter(ACTION_USB_PERMISSION), Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, IntentFilter(ACTION_USB_PERMISSION))
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_MUTABLE
        )
        usbManager.requestPermission(device, pendingIntent)

        return result.await()
    }

    /** Claims the device and picks the format whose sample rate is closest to [desiredSampleRate]
     *  (matching the track actually being played, not just whatever the DAC's highest rate is).
     *  Returns false if nothing usable was found. */
    fun connect(device: UsbDevice, desiredSampleRate: Int): Boolean {
        val conn = usbManager.openDevice(device) ?: return false
        val options = UsbAudioDescriptorParser.findStreamingOptions(device, conn)

        // For each 16-bit option, find its closest available rate to what we actually need.
        val candidates = options
            .filter { it.bitDepth == 16 }
            .mapNotNull { opt ->
                val closest = opt.sampleRates.minByOrNull { kotlin.math.abs(it - desiredSampleRate) }
                    ?: return@mapNotNull null
                Triple(opt, closest, kotlin.math.abs(closest - desiredSampleRate))
            }

        val (option, matchedRate, _) = candidates.minByOrNull { it.third } ?: run {
            conn.close()
            return false
        }

        val claimed = conn.claimInterface(option.usbInterface, true)
        if (!claimed) {
            conn.close()
            return false
        }
        conn.setInterface(option.usbInterface)

        val rateBytes4 = byteArrayOf(
            (matchedRate and 0xFF).toByte(),
            ((matchedRate shr 8) and 0xFF).toByte(),
            ((matchedRate shr 16) and 0xFF).toByte(),
            ((matchedRate shr 24) and 0xFF).toByte()
        )

        if (option.isUac2) {
            // UAC2: SET_CUR on the clock source entity via the AudioControl interface, not the
            // streaming endpoint — a fundamentally different control path than UAC1.
            val clockId = option.clockEntityId
            val acNum = option.acInterfaceNumber
            if (clockId != null && acNum != null) {
                val wIndex = (clockId shl 8) or acNum
                conn.controlTransfer(0x21, 0x01, 0x0100, wIndex, rateBytes4, rateBytes4.size, 1000)
            }
        } else if (option.sampleRates.size > 1) {
            // UAC1: SET_CUR, SAMPLING_FREQ_CONTROL, endpoint recipient. Without this, a device
            // that advertises multiple rates for this alt setting may keep using its own default
            // rate instead of the one we've computed — silently mismatching pitch/speed.
            val rateBytes3 = rateBytes4.copyOf(3)
            conn.controlTransfer(0x22, 0x01, 0x0100, option.endpoint.address, rateBytes3, rateBytes3.size, 1000)
        }

        connection = conn
        connectedFormat = ConnectedFormat(
            sampleRate = matchedRate,
            channels = option.channels,
            bitDepth = option.bitDepth
        )
        startStreamLoop(conn, option)
        return true
    }

    /** Feed PCM (matching connectedFormat) here; blocks briefly if the internal queue is full. */
    fun write(pcm: ByteArray) {
        if (!running.get()) return
        try {
            pcmQueue.put(pcm)
        } catch (_: InterruptedException) {
            // Shutting down; drop this chunk.
        }
    }

    private fun startStreamLoop(conn: UsbDeviceConnection, option: UsbAudioDescriptorParser.StreamingOption) {
        running.set(true)
        val endpoint = option.endpoint
        streamThread = Thread {
            val request = UsbRequest()
            request.initialize(conn, endpoint)
            try {
                while (running.get()) {
                    val chunk = try {
                        pcmQueue.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS)
                    } catch (_: InterruptedException) {
                        null
                    } ?: continue

                    val buffer = ByteBuffer.wrap(chunk)
                    if (!request.queue(buffer, chunk.size)) continue
                    conn.requestWait()
                }
            } finally {
                request.close()
            }
        }.also { it.start() }
    }

    fun disconnect() {
        running.set(false)
        streamThread?.interrupt()
        streamThread = null
        pcmQueue.clear()
        connection?.close()
        connection = null
        connectedFormat = null
    }
}
