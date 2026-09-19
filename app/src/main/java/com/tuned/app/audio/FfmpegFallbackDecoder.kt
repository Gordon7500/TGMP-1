package com.tuned.app.audio

import android.content.Context

/**
 * The FFmpeg-based hi-res format fallback (DSD/DSF, WavPack, ALAC-on-devices-without-it) has
 * been rolled back — adding that dependency caused a crash-on-reopen regression that persisted
 * even after guarding every call site, meaning the failure was happening at a level (native
 * library loading during class verification) that can occur before any of our own try/catch
 * code ever runs. Rather than keep guessing at fixes for a dependency I can't test myself, this
 * was pulled out entirely to restore stability. This file is kept as a harmless no-op stub so
 * nothing else needs to change — [KNOWN_UNSUPPORTED_EXTENSIONS] is empty, so AudioEngine never
 * routes anything here anymore, and it always reports failure if somehow called.
 */
object FfmpegFallbackDecoder {

    val KNOWN_UNSUPPORTED_EXTENSIONS = emptySet<String>()

    fun transcodeToWav(context: Context, inputPath: String, outputPath: String): Boolean = false

    fun extensionOf(path: String): String =
        path.substringAfterLast('.', "").substringBefore('?').lowercase()

    fun cleanupOldTempFiles(context: Context) {
        // No-op — nothing to clean up now that the FFmpeg fallback is inactive.
    }
}
