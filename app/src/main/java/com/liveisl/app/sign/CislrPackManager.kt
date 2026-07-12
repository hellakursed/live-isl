package com.liveisl.app.sign

import android.content.Context
import android.util.Log
import com.liveisl.app.data.SignClipPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

data class CislrPackStatus(
    val installed: Boolean = false,
    val glossCount: Int = 0,
    val videoCount: Int = 0,
    val message: String = "CISLR pack not installed",
    val downloading: Boolean = false,
    val progress: Float = 0f,
)

/**
 * Installs / reports the on-device CISLR video pack under filesDir.
 *
 * Pack layout:
 *   filesDir/sign_sources/cislr/glosses.json
 *   filesDir/sign_sources/cislr/videos/(clips).mp4
 *
 * Obtain the pack with scripts/fetch_cislr_pack.sh, or download the hosted zip
 * via [downloadAndInstall] (extracts then deletes the zip).
 */
class CislrPackManager(private val context: Context) {
    private val root get() = SignClipPaths.packRoot(context, VideoSource.CISLR)

    private val _status = MutableStateFlow(CislrPackStatus())
    val status: StateFlow<CislrPackStatus> = _status.asStateFlow()

    init {
        refreshStatus()
    }

    fun refreshStatus(): CislrPackStatus {
        val dict = SignClipPaths.dictionaryFile(context, VideoSource.CISLR)
        val videos = SignClipPaths.videosDir(context, VideoSource.CISLR)
        val videoCount = videos.listFiles()?.count { it.extension.equals("mp4", true) } ?: 0
        val glossCount = if (dict.exists()) {
            try {
                dict.readText().split("\"glossId\"").size - 1
            } catch (_: Exception) {
                0
            }
        } else {
            0
        }
        val installed = dict.exists() && videoCount > 0
        val status = CislrPackStatus(
            installed = installed,
            glossCount = glossCount.coerceAtLeast(0),
            videoCount = videoCount,
            message = when {
                installed -> "CISLR ready — $videoCount clips / $glossCount glosses"
                dict.exists() && videoCount == 0 -> "Dictionary present but no videos yet"
                else -> "Pack not on device — tap Download pack"
            },
        )
        _status.value = status
        return status
    }

    /**
     * Import an already-extracted pack directory (e.g. after adb push).
     * Copies glosses.json + videos/ into the canonical filesDir location.
     */
    suspend fun importFromDirectory(sourceDir: File): Result<CislrPackStatus> =
        withContext(Dispatchers.IO) {
            runCatching {
                val srcDict = File(sourceDir, "glosses.json")
                val srcVideos = File(sourceDir, "videos")
                require(srcDict.exists()) { "Missing glosses.json in $sourceDir" }
                require(srcVideos.isDirectory) { "Missing videos/ in $sourceDir" }

                val destDict = SignClipPaths.dictionaryFile(context, VideoSource.CISLR)
                val destVideos = SignClipPaths.videosDir(context, VideoSource.CISLR)
                destVideos.mkdirs()
                destDict.parentFile?.mkdirs()
                srcDict.copyTo(destDict, overwrite = true)

                srcVideos.listFiles()?.filter { it.extension.equals("mp4", true) }?.forEach { f ->
                    f.copyTo(File(destVideos, f.name), overwrite = true)
                }
                refreshStatus()
            }
        }

    /**
     * Download a zip pack from [zipUrl], extract into the CISLR filesDir, then delete the zip.
     * Expected zip entries: glosses.json and videos/(name).mp4 (with or without a root folder).
     */
    suspend fun downloadAndInstall(zipUrl: String): Result<CislrPackStatus> =
        withContext(Dispatchers.IO) {
            val tmp = File(context.cacheDir, "cislr_pack_download.zip")
            runCatching {
                _status.value = _status.value.copy(downloading = true, progress = 0f, message = "Downloading…")
                if (tmp.exists()) tmp.delete()
                downloadToFile(zipUrl, tmp) { p ->
                    _status.value = _status.value.copy(
                        progress = p * 0.7f,
                        message = "Downloading… ${(p * 100).toInt()}%",
                    )
                }
                _status.value = _status.value.copy(progress = 0.72f, message = "Extracting…")
                // Replace previous install so we don't mix packs
                if (root.exists()) {
                    root.deleteRecursively()
                }
                root.mkdirs()
                extractZip(tmp, root)
                _status.value = _status.value.copy(progress = 0.95f, message = "Cleaning up…")
                val status = refreshStatus()
                if (!status.installed) error("Extracted pack is incomplete")
                status
            }.also { result ->
                // Always delete the downloaded zip (success or failure)
                runCatching { if (tmp.exists()) tmp.delete() }
                if (result.isFailure) {
                    _status.value = refreshStatus().copy(
                        downloading = false,
                        message = result.exceptionOrNull()?.message ?: "Download failed",
                    )
                } else {
                    _status.value = result.getOrThrow().copy(downloading = false, progress = 1f)
                }
            }
        }

    private fun downloadToFile(url: String, out: File, onProgress: (Float) -> Unit) {
        var current = url
        var redirects = 0
        while (redirects < 8) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 60_000
                // Large pack (~800MB) — keep the socket alive for slow networks
                readTimeout = 10 * 60_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "LiveISL/1.0")
                setRequestProperty("Accept", "*/*")
            }
            try {
                when (val code = conn.responseCode) {
                    in 300..399 -> {
                        val next = conn.getHeaderField("Location")
                            ?: error("Redirect without Location ($code)")
                        current = if (next.startsWith("http")) next else URL(URL(current), next).toString()
                        redirects++
                        continue
                    }
                    in 200..299 -> {
                        val total = conn.contentLengthLong.coerceAtLeast(1L)
                        out.parentFile?.mkdirs()
                        BufferedInputStream(conn.inputStream).use { input ->
                            FileOutputStream(out).use { output ->
                                val buf = ByteArray(256 * 1024)
                                var read: Int
                                var done = 0L
                                while (input.read(buf).also { read = it } != -1) {
                                    output.write(buf, 0, read)
                                    done += read
                                    onProgress((done.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                                }
                            }
                        }
                        return
                    }
                    else -> error("HTTP $code for $current")
                }
            } finally {
                conn.disconnect()
            }
        }
        error("Too many redirects for $url")
    }

    private fun extractZip(zip: File, destRoot: File) {
        destRoot.mkdirs()
        var extracted = 0
        ZipInputStream(BufferedInputStream(zip.inputStream())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.trimStart('/').substringAfter("cislr/")
                if (name.isBlank() || entry.isDirectory) {
                    zis.closeEntry()
                    entry = zis.nextEntry
                    continue
                }
                val safe = when {
                    name == "glosses.json" || name.endsWith("/glosses.json") ->
                        File(destRoot, "glosses.json")
                    name.contains("videos/") && name.endsWith(".mp4", ignoreCase = true) ->
                        File(SignClipPaths.videosDir(context, VideoSource.CISLR), File(name).name)
                    else -> null
                }
                if (safe != null) {
                    safe.parentFile?.mkdirs()
                    FileOutputStream(safe).use { zis.copyTo(it) }
                    extracted++
                    if (extracted % 200 == 0) {
                        val p = (0.72f + (extracted / 5000f).coerceAtMost(0.2f))
                        _status.value = _status.value.copy(
                            progress = p,
                            message = "Extracting… $extracted files",
                        )
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        Log.i(TAG, "Extracted CISLR pack ($extracted files) into $destRoot")
    }

    companion object {
        private const val TAG = "CislrPackManager"
    }
}
