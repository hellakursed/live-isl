package com.liveisl.app.data

import android.content.Context
import com.liveisl.app.sign.VideoSource
import java.io.File

/**
 * Resolves on-disk / asset locations for a [VideoSource].
 *
 * Vikasops stays in APK assets. CISLR lives under filesDir after the user
 * installs a pack (see [com.liveisl.app.sign.CislrPackManager]).
 */
object SignClipPaths {
    fun packRoot(context: Context, source: VideoSource): File = when (source) {
        VideoSource.VIKASOPS -> File(context.filesDir, "sign_sources/vikasops") // unused; assets used
        VideoSource.CISLR -> File(context.filesDir, "sign_sources/cislr")
    }

    fun videosDir(context: Context, source: VideoSource): File =
        File(packRoot(context, source), "videos")

    fun dictionaryFile(context: Context, source: VideoSource): File =
        File(packRoot(context, source), "glosses.json")

    /** Asset-relative dictionary for bundled sources. */
    fun assetDictionaryPath(source: VideoSource): String? = when (source) {
        VideoSource.VIKASOPS -> "dictionary/glosses.json"
        VideoSource.CISLR -> "dictionary/glosses_cislr.json" // optional seed / empty stub
    }
}
