package com.tuned.app.usb

import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface

/**
 * Android's high-level UsbInterface/UsbEndpoint API tells you an interface is "audio class" but
 * not the actual format (sample rate, bit depth) an alternate setting streams — that lives in
 * USB Audio Class-specific descriptors that Android doesn't parse for you. This walks the raw
 * descriptor bytes to find that information ourselves, for both UAC 1.0 and UAC 2.0.
 *
 * UAC1 and UAC2 describe formats very differently:
 * - UAC1 lists discrete sample rates right there in the Format Type descriptor.
 * - UAC2 doesn't — the format descriptor only has bit depth. Sample rates live on a separate
 *   "Clock Source" entity, discovered via a GET_RANGE control request, not a static descriptor.
 *   This code assumes a single clock source per device, which covers the large majority of
 *   consumer USB DACs (multi-clock designs exist but are uncommon outside pro audio interfaces).
 *
 * Scope: PCM format type I only, both UAC versions.
 */
object UsbAudioDescriptorParser {

    data class StreamingOption(
        val usbInterface: UsbInterface,
        val endpoint: UsbEndpoint,
        val channels: Int,
        val bitDepth: Int,
        val sampleRates: List<Int>,
        val isUac2: Boolean,
        val clockEntityId: Int? = null,
        val acInterfaceNumber: Int? = null
    )

    private const val USB_CLASS_AUDIO = 0x01
    private const val AUDIO_SUBCLASS_CONTROL = 0x01
    private const val AUDIO_SUBCLASS_STREAMING = 0x02
    private const val CS_INTERFACE = 0x24
    private const val AS_GENERAL = 0x01
    private const val FORMAT_TYPE = 0x02
    private const val CLOCK_SOURCE = 0x0A
    private const val UAC2_PROTOCOL = 0x20

    private const val UAC2_CS_SAM_FREQ_CONTROL = 0x01
    private const val REQUEST_RANGE = 0x02

    /** Scans every alt-setting of every audio-streaming interface for a usable PCM format. */
    fun findStreamingOptions(device: UsbDevice, connection: UsbDeviceConnection): List<StreamingOption> {
        val raw = connection.rawDescriptors ?: return emptyList()
        val results = mutableListOf<StreamingOption>()

        val chunks = mutableListOf<ByteArray>()
        var offset = 0
        while (offset + 1 < raw.size) {
            val length = raw[offset].toInt() and 0xFF
            if (length < 2 || offset + length > raw.size) break
            chunks.add(raw.copyOfRange(offset, offset + length))
            offset += length
        }

        val acInterfaceNumber = findAudioControlInterfaceNumber(device)
        val clockEntityId = findClockSourceEntityId(chunks)

        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass != USB_CLASS_AUDIO || iface.interfaceSubclass != AUDIO_SUBCLASS_STREAMING) continue
            if (iface.alternateSetting == 0) continue

            val endpoint = (0 until iface.endpointCount)
                .map { iface.getEndpoint(it) }
                .firstOrNull {
                    it.direction == UsbConstants.USB_DIR_OUT &&
                        it.type == UsbConstants.USB_ENDPOINT_XFER_ISOC
                } ?: continue

            val isUac2 = iface.interfaceProtocol == UAC2_PROTOCOL

            if (isUac2) {
                val format = findUac2FormatForInterface(chunks, iface.id, iface.alternateSetting) ?: continue
                if (clockEntityId == null || acInterfaceNumber == null) continue // can't set rate; skip
                val rates = queryUac2SampleRates(connection, clockEntityId, acInterfaceNumber)
                if (rates.isEmpty()) continue
                results.add(
                    StreamingOption(
                        iface, endpoint, format.channels, format.bitDepth, rates,
                        isUac2 = true, clockEntityId = clockEntityId, acInterfaceNumber = acInterfaceNumber
                    )
                )
            } else {
                val format = findUac1FormatForInterface(chunks, iface.id, iface.alternateSetting) ?: continue
                results.add(
                    StreamingOption(iface, endpoint, format.channels, format.bitDepth, format.sampleRates, isUac2 = false)
                )
            }
        }
        return results
    }

    private data class ParsedFormat(val channels: Int, val bitDepth: Int, val sampleRates: List<Int> = emptyList())

    private fun findUac1FormatForInterface(chunks: List<ByteArray>, interfaceNumber: Int, altSetting: Int): ParsedFormat? {
        var matched = false
        for (chunk in chunks) {
            if (chunk.size < 2) continue
            val type = chunk[1].toInt() and 0xFF

            if (type == 0x04 && chunk.size >= 4) {
                matched = (chunk[2].toInt() and 0xFF) == interfaceNumber && (chunk[3].toInt() and 0xFF) == altSetting
                continue
            }
            if (!matched || type != CS_INTERFACE || chunk.size < 3) continue

            val subtype = chunk[2].toInt() and 0xFF
            if (subtype != FORMAT_TYPE || chunk.size < 8) continue

            val channels = chunk[4].toInt() and 0xFF
            val subframeSize = chunk[5].toInt() and 0xFF
            var bitDepth = chunk[6].toInt() and 0xFF
            if (bitDepth == 0) bitDepth = subframeSize * 8

            val sampFreqType = chunk[7].toInt() and 0xFF
            val rates = mutableListOf<Int>()
            if (sampFreqType == 0) {
                if (chunk.size >= 14) rates.add(readUInt24(chunk, 8))
            } else {
                var pos = 8
                repeat(sampFreqType) {
                    if (pos + 2 < chunk.size) {
                        rates.add(readUInt24(chunk, pos))
                        pos += 3
                    }
                }
            }
            if (channels > 0 && bitDepth > 0 && rates.isNotEmpty()) return ParsedFormat(channels, bitDepth, rates)
        }
        return null
    }

    /** UAC2's AS_GENERAL and Format Type descriptors have a different byte layout than UAC1's,
     *  and carry no sample-rate info at all (that's queried separately via [queryUac2SampleRates]). */
    private fun findUac2FormatForInterface(chunks: List<ByteArray>, interfaceNumber: Int, altSetting: Int): ParsedFormat? {
        var matched = false
        var channels = 0
        var bitDepth = 0

        for (chunk in chunks) {
            if (chunk.size < 2) continue
            val type = chunk[1].toInt() and 0xFF

            if (type == 0x04 && chunk.size >= 4) {
                matched = (chunk[2].toInt() and 0xFF) == interfaceNumber && (chunk[3].toInt() and 0xFF) == altSetting
                continue
            }
            if (!matched || type != CS_INTERFACE || chunk.size < 3) continue

            val subtype = chunk[2].toInt() and 0xFF
            // UAC2 AS_GENERAL: length,type,subtype,bTerminalLink,bmControls,bFormatType,bmFormats(4),bNrChannels,...
            if (subtype == AS_GENERAL && chunk.size >= 11) {
                channels = chunk[10].toInt() and 0xFF
            }
            // UAC2 Format Type I: length,type,subtype,bFormatType,bSubslotSize,bBitResolution
            if (subtype == FORMAT_TYPE && chunk.size >= 6) {
                val subslotSize = chunk[4].toInt() and 0xFF
                bitDepth = chunk[5].toInt() and 0xFF
                if (bitDepth == 0) bitDepth = subslotSize * 8
            }
        }
        return if (channels > 0 && bitDepth > 0) ParsedFormat(channels, bitDepth) else null
    }

    private fun findAudioControlInterfaceNumber(device: UsbDevice): Int? {
        for (i in 0 until device.interfaceCount) {
            val iface = device.getInterface(i)
            if (iface.interfaceClass == USB_CLASS_AUDIO && iface.interfaceSubclass == AUDIO_SUBCLASS_CONTROL) {
                return iface.id
            }
        }
        return null
    }

    private fun findClockSourceEntityId(chunks: List<ByteArray>): Int? {
        for (chunk in chunks) {
            if (chunk.size < 4 || (chunk[1].toInt() and 0xFF) != CS_INTERFACE) continue
            if ((chunk[2].toInt() and 0xFF) == CLOCK_SOURCE) {
                return chunk[3].toInt() and 0xFF
            }
        }
        return null
    }

    /** Issues a GET_RANGE request to the clock source and parses the returned min/max/res
     *  sub-ranges into a flat list of sample rates. */
    private fun queryUac2SampleRates(connection: UsbDeviceConnection, clockEntityId: Int, acInterfaceNumber: Int): List<Int> {
        val buffer = ByteArray(258) // generous — spec allows many sub-ranges, though most devices have few
        val wValue = (UAC2_CS_SAM_FREQ_CONTROL shl 8)
        val wIndex = (clockEntityId shl 8) or acInterfaceNumber
        // bmRequestType 0xA1: device-to-host, class, interface recipient.
        val length = connection.controlTransfer(0xA1, REQUEST_RANGE, wValue, wIndex, buffer, buffer.size, 1000)
        if (length < 2) return emptyList()

        val numSubRanges = (buffer[0].toInt() and 0xFF) or ((buffer[1].toInt() and 0xFF) shl 8)
        val rates = mutableListOf<Int>()
        var pos = 2
        repeat(numSubRanges) {
            if (pos + 12 > length) return@repeat
            val min = readUInt32(buffer, pos)
            val max = readUInt32(buffer, pos + 4)
            val res = readUInt32(buffer, pos + 8)
            if (min == max) {
                rates.add(min)
            } else if (res > 0) {
                // A continuous range with a step — add the endpoints; a full expansion isn't
                // needed since we only ever pick the single closest rate to what we're playing.
                rates.add(min)
                rates.add(max)
            }
            pos += 12
        }
        return rates
    }

    private fun readUInt24(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16)

    private fun readUInt32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
}
