package com.tuned.app.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

data class OutputRouting(
    val sampleRate: Int,
    val preferredDevice: AudioDeviceInfo?
)

/**
 * Looks at currently connected audio output devices and, when a USB DAC is attached and direct
 * output is enabled, prefers routing straight to it at a sample rate the DAC itself advertises
 * (closest to the source's native rate, to avoid this app resampling anything itself).
 *
 * NOTE: whether the OS then avoids its own internal resampling/mixing on top of this is a
 * device/OEM-dependent behavior this class cannot fully control — see AudioEngine's doc comment.
 */
class OutputDeviceRouter(private val context: Context) {

    fun resolve(sourceSampleRate: Int, directOutputPreferred: Boolean): OutputRouting {
        if (!directOutputPreferred) {
            return OutputRouting(sourceSampleRate, null)
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        val usbDevice = outputs.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        } ?: return OutputRouting(sourceSampleRate, null)

        val supportedRates = usbDevice.sampleRates
        val chosenRate = when {
            supportedRates.isEmpty() -> sourceSampleRate
            supportedRates.contains(sourceSampleRate) -> sourceSampleRate
            else -> supportedRates.minByOrNull { kotlin.math.abs(it - sourceSampleRate) } ?: sourceSampleRate
        }

        return OutputRouting(chosenRate, usbDevice)
    }
}
