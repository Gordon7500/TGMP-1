package com.tuned.app.audio

import android.media.audiofx.BassBoost
import com.tuned.app.data.Store

/**
 * Owns the custom 10-band equalizer (real DSP, see TenBandEqualizer) and the system BassBoost
 * effect (which — unlike a graphic equalizer — Android's platform implementation handles well,
 * so no need to reinvent that one). Settings are persisted in Store and reapplied automatically.
 *
 * Note: using either of these means the signal is no longer bit-perfect/untouched — see the
 * doc comment on AudioEngine. That's inherent to what an equalizer/bass boost does, not a bug.
 */
class AudioEffectsController(private val store: Store) {

    val bandEqualizer = TenBandEqualizer()

    private var bassBoost: BassBoost? = null
    private var currentSessionId: Int = -1

    val numberOfBands: Int get() = TenBandEqualizer.BAND_COUNT

    fun bandFrequencyLabel(band: Int): String {
        val hz = TenBandEqualizer.FREQUENCIES.getOrNull(band) ?: return ""
        return if (hz >= 1000) "${hz / 1000}kHz" else "${hz}Hz"
    }

    fun bandLevelRangeMb(): IntRange = TenBandEqualizer.MIN_GAIN_DB..TenBandEqualizer.MAX_GAIN_DB

    init {
        bandEqualizer.enabled = store.equalizerEnabled
        val savedLevels = store.equalizerBandLevels
        if (savedLevels.size == TenBandEqualizer.BAND_COUNT) {
            savedLevels.forEachIndexed { i, level -> bandEqualizer.setBandGain(i, level) }
        } else {
            store.equalizerBandLevels = List(TenBandEqualizer.BAND_COUNT) { 0 }
        }
    }

    /** Call whenever AudioEngine reports a new session ID (i.e. on every new track) — only
     *  BassBoost needs this; the custom equalizer is reconfigured directly by AudioEngine
     *  itself since it needs the exact sample rate, not just a session id. */
    fun onSessionIdChanged(sessionId: Int) {
        if (sessionId == currentSessionId || sessionId == 0) return
        bassBoost?.release()
        currentSessionId = sessionId

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
        bandEqualizer.enabled = enabled
    }

    fun setBandLevel(band: Int, levelDb: Int) {
        bandEqualizer.setBandGain(band, levelDb)
        val levels = store.equalizerBandLevels.toMutableList()
        while (levels.size <= band) levels.add(0)
        levels[band] = levelDb
        store.equalizerBandLevels = levels
    }

    fun currentBandLevel(band: Int): Int = bandEqualizer.getBandGain(band)

    fun setBassBoostEnabled(enabled: Boolean) {
        store.bassBoostEnabled = enabled
        bassBoost?.enabled = enabled
    }

    fun setBassBoostStrength(strength: Int) {
        store.bassBoostStrength = strength
        try { bassBoost?.setStrength(strength.toShort()) } catch (_: Exception) {}
    }

    fun release() {
        bassBoost?.release()
        bassBoost = null
    }
}
