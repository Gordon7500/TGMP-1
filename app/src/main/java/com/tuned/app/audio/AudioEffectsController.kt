package com.tuned.app.audio

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import com.tuned.app.data.Store

/**
 * Attaches/re-attaches Equalizer and BassBoost effects whenever the audio session changes
 * (a new AudioTrack — and therefore a new session — is created on every track change in
 * AudioEngine). Settings are persisted in Store and reapplied automatically each time.
 *
 * Note: using these features means the signal is no longer bit-perfect/untouched — see the
 * doc comment on AudioEngine. That's inherent to what an equalizer does, not a bug here.
 */
class AudioEffectsController(private val store: Store) {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var currentSessionId: Int = -1

    val numberOfBands: Int
        get() = equalizer?.numberOfBands?.toInt() ?: 0

    fun bandFrequencyLabel(band: Int): String {
        val eq = equalizer ?: return ""
        val centerFreqHz = eq.getCenterFreq(band.toShort()) / 1000
        return if (centerFreqHz >= 1000) "${centerFreqHz / 1000}kHz" else "${centerFreqHz}Hz"
    }

    fun bandLevelRangeMb(): IntRange {
        val eq = equalizer ?: return -1500..1500
        val range = eq.bandLevelRange
        return range[0].toInt()..range[1].toInt()
    }

    /** Call whenever AudioEngine reports a new session ID (i.e. on every new track). */
    fun onSessionIdChanged(sessionId: Int) {
        if (sessionId == currentSessionId || sessionId == 0) return
        release()
        currentSessionId = sessionId

        try {
            val eq = Equalizer(0, sessionId)
            equalizer = eq
            val savedLevels = store.equalizerBandLevels
            if (savedLevels.size == eq.numberOfBands.toInt()) {
                savedLevels.forEachIndexed { i, level ->
                    try { eq.setBandLevel(i.toShort(), level.toShort()) } catch (_: Exception) {}
                }
            } else {
                // First run on this device: capture the default band layout so the UI has
                // something to show and store it as the baseline (all bands flat).
                val flat = List(eq.numberOfBands.toInt()) { 0 }
                store.equalizerBandLevels = flat
            }
            eq.enabled = store.equalizerEnabled
        } catch (_: Exception) {
            equalizer = null
        }

        try {
            val bb = BassBoost(0, sessionId)
            bassBoost = bb
            bb.setStrength(store.bassBoostStrength.toShort())
            bb.enabled = store.bassBoostEnabled
        } catch (_: Exception) {
            bassBoost = null
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        store.equalizerEnabled = enabled
        equalizer?.enabled = enabled
    }

    fun setBandLevel(band: Int, levelMb: Int) {
        equalizer?.setBandLevel(band.toShort(), levelMb.toShort())
        val levels = store.equalizerBandLevels.toMutableList()
        while (levels.size <= band) levels.add(0)
        levels[band] = levelMb
        store.equalizerBandLevels = levels
    }

    fun currentBandLevel(band: Int): Int =
        try { equalizer?.getBandLevel(band.toShort())?.toInt() ?: 0 } catch (_: Exception) { 0 }

    fun setBassBoostEnabled(enabled: Boolean) {
        store.bassBoostEnabled = enabled
        bassBoost?.enabled = enabled
    }

    fun setBassBoostStrength(strength: Int) {
        store.bassBoostStrength = strength
        try { bassBoost?.setStrength(strength.toShort()) } catch (_: Exception) {}
    }

    fun release() {
        equalizer?.release()
        equalizer = null
        bassBoost?.release()
        bassBoost = null
    }
}
