package com.tuned.app.audio

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import java.io.File

/**
 * Handles formats Android's own MediaCodec has no decoder for at all — DSD/DSF, WavPack, and
 * (on most phones) ALAC. Rather than writing a second parallel playback engine, this transcodes
 * the source into a plain 16-bit PCM WAV file first, which our existing MediaExtractor/MediaCodec
 * pipeline can already play natively — so seeking, pausing, the equalizer, and USB output all
 * keep working exactly as before with no separate code path to maintain.
 *
 * This only ever runs as a fallback, after Android's own decoder has already failed to handle
 * the file — never instead of it.
 */
object FfmpegFallbackDecoder {

    /** File extensions with no Android-native decoder at all, on any phone — skip straight to
     *  FFmpeg for these instead of wasting time on a MediaCodec attempt that's certain to fail. */
    val KNOWN_UNSUPPORTED_EXTENSIONS = setOf("dsf", "dff", "wv")

    /**
     * Transcodes [inputPath] (a local file path, content:// URI, or http(s) URL) into a WAV
     * file at [outputPath]. Returns true on success. Preserves the source's native sample rate
     * and channel count — no forced resampling — since the whole point is playing back the
     * original quality, not homogenizing it.
     */
    fun transcodeToWav(context: Context, inputPath: String, outputPath: String): Boolean {
        val resolvedInput = resolveToLocalPath(context, inputPath) ?: return false

        val session = FFmpegKit.execute(
            "-y -i \"$resolvedInput\" -vn -map 0:a:0 -c:a pcm_s16le \"$outputPath\""
        )
        return ReturnCode.isSuccess(session.returnCode) && File(outputPath).length() > 0
    }

    /** FFmpeg's file protocol can't read Android's content:// URIs directly — copy to a plain
     *  temp file first in that case. Local paths and http(s) URLs are passed through as-is. */
    private fun resolveToLocalPath(context: Context, path: String): String? {
        if (!path.startsWith("content://")) return path

        return try {
            val tempFile = File(context.cacheDir, "tuned_src_${System.currentTimeMillis()}")
            context.contentResolver.openInputStream(Uri.parse(path))?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            tempFile.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    fun extensionOf(path: String): String =
        path.substringAfterLast('.', "").substringBefore('?').lowercase()

    /** Call once on startup — removes leftover temp files from previous sessions so they don't
     *  accumulate in the cache indefinitely. */
    fun cleanupOldTempFiles(context: Context) {
        try {
            val cutoff = System.currentTimeMillis() - 60 * 60 * 1000 // 1 hour
            context.cacheDir.listFiles()?.forEach { f ->
                if ((f.name.startsWith("tuned_fallback_") || f.name.startsWith("tuned_src_")) &&
                    f.lastModified() < cutoff
                ) {
                    f.delete()
                }
            }
        } catch (_: Exception) {
        }
    }
}
