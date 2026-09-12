package com.tuned.app.data

object QualityLabel {

    /** Prefers a computed bitrate (most informative); falls back to file-extension format. */
    fun estimate(fileName: String?, mimeType: String?, sizeBytes: Long?, durationSec: Int): String? {
        if (sizeBytes != null && sizeBytes > 0 && durationSec > 0) {
            val kbps = (sizeBytes * 8 / 1000) / durationSec
            if (kbps in 8..2000) return "${kbps}kbps"
        }
        val ext = fileName?.substringAfterLast('.', "")?.uppercase()?.takeIf { it.isNotBlank() && it.length <= 5 }
        if (ext != null) return ext
        val mimeExt = mimeType?.substringAfter('/', "")?.uppercase()?.takeIf { it.isNotBlank() && it.length <= 5 }
        return mimeExt
    }
}
