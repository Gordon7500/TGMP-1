package com.tuned.app.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A real 10-band graphic equalizer that processes 16-bit PCM samples directly, rather than
 * relying on Android's built-in Equalizer effect — which only ever exposes however many bands
 * the device's audio framework happens to provide (almost always 5, and not something an app
 * can request more of). This runs the actual DSP ourselves: one peaking (bell) biquad filter per
 * band, cascaded, with independent filter history per channel so left/right don't bleed into
 * each other.
 */
class TenBandEqualizer {

    companion object {
        val FREQUENCIES = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
        const val BAND_COUNT = 10
        const val MIN_GAIN_DB = -12
        const val MAX_GAIN_DB = 12
        private const val Q = 1.0
    }

    @Volatile
    var enabled: Boolean = false

    private val gainsDb = DoubleArray(BAND_COUNT)
    private var sampleRate = 44100
    private var channelCount = 2
    private var states: Array<Array<BiquadState>> = arrayOf()

    private class BiquadState {
        var b0 = 1.0; var b1 = 0.0; var b2 = 0.0; var a1 = 0.0; var a2 = 0.0
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
    }

    /** Call once per track start (or whenever sample rate/channel count changes). */
    @Synchronized
    fun configure(sampleRate: Int, channelCount: Int) {
        this.sampleRate = sampleRate
        this.channelCount = channelCount.coerceIn(1, 2)
        states = Array(BAND_COUNT) { Array(this.channelCount) { BiquadState() } }
        for (b in 0 until BAND_COUNT) recomputeCoefficients(b)
    }

    @Synchronized
    fun setBandGain(band: Int, gainDb: Int) {
        if (band !in 0 until BAND_COUNT) return
        gainsDb[band] = gainDb.toDouble().coerceIn(MIN_GAIN_DB.toDouble(), MAX_GAIN_DB.toDouble())
        recomputeCoefficients(band)
    }

    fun getBandGain(band: Int): Int = gainsDb.getOrElse(band) { 0.0 }.roundToInt()

    private fun recomputeCoefficients(band: Int) {
        if (states.isEmpty()) return
        val f0 = FREQUENCIES[band].toDouble()
        val a = 10.0.pow(gainsDb[band] / 40.0)
        val w0 = 2 * PI * f0 / sampleRate
        val alpha = sin(w0) / (2 * Q)
        val cosw0 = cos(w0)

        val b0 = 1 + alpha * a
        val b1 = -2 * cosw0
        val b2 = 1 - alpha * a
        val a0 = 1 + alpha / a
        val a1 = -2 * cosw0
        val a2 = 1 - alpha / a

        for (ch in 0 until channelCount) {
            val s = states[band][ch]
            s.b0 = b0 / a0
            s.b1 = b1 / a0
            s.b2 = b2 / a0
            s.a1 = a1 / a0
            s.a2 = a2 / a0
        }
    }

    /** Processes 16-bit little-endian interleaved PCM samples in place. */
    @Synchronized
    fun process(buffer: ByteArray, length: Int) {
        if (!enabled || states.isEmpty()) return
        var i = 0
        var ch = 0
        while (i + 1 < length) {
            var sample = (((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()).toDouble()
            val channel = ch % channelCount

            for (b in 0 until BAND_COUNT) {
                val s = states[b][channel]
                val x0 = sample
                val y0 = s.b0 * x0 + s.b1 * s.x1 + s.b2 * s.x2 - s.a1 * s.y1 - s.a2 * s.y2
                s.x2 = s.x1; s.x1 = x0
                s.y2 = s.y1; s.y1 = y0
                sample = y0
            }

            val clamped = sample.roundToInt().coerceIn(-32768, 32767)
            buffer[i] = (clamped and 0xFF).toByte()
            buffer[i + 1] = ((clamped shr 8) and 0xFF).toByte()

            i += 2
            ch++
        }
    }
}
