package com.liveisl.app.sign

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.liveisl.app.data.Gloss
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.ArrayDeque

class VideoClipSignRenderer(
    private val context: Context,
) : SignRenderer {
    private val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private val queue = ArrayDeque<Gloss>()
    private val assetUriCache = HashMap<String, Uri>()

    private val _currentGloss = MutableStateFlow<Gloss?>(null)
    private val _queueDepth = MutableStateFlow(0)
    private val _lastClipStartLagMs = MutableStateFlow(0L)
    private val _isPlaying = MutableStateFlow(false)
    private val _fallbackLabel = MutableStateFlow<String?>(null)
    private val _isShowingVideo = MutableStateFlow(false)
    private val _playbackSpeed = MutableStateFlow(1f)

    override val currentGloss: StateFlow<Gloss?> = _currentGloss.asStateFlow()
    override val queueDepth: StateFlow<Int> = _queueDepth.asStateFlow()
    override val lastClipStartLagMs: StateFlow<Long> = _lastClipStartLagMs.asStateFlow()
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    val fallbackLabel: StateFlow<String?> = _fallbackLabel.asStateFlow()
    val isShowingVideo: StateFlow<Boolean> = _isShowingVideo.asStateFlow()
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    /** Exposed for Compose PlayerView binding. */
    fun exoPlayer(): ExoPlayer = player

    private var enqueueAtMs = 0L
    private var fallbackAdvance: Runnable? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                playNext()
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Playback error for ${_currentGloss.value?.videoAssetPath}: ${error.message}", error)
            // Missing / unreadable clip → show fallback gloss card then continue
            val gloss = _currentGloss.value
            if (gloss != null) {
                showFallback(gloss)
            } else {
                playNext()
            }
        }
    }

    init {
        player.addListener(listener)
        player.repeatMode = Player.REPEAT_MODE_OFF
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2f)
        _playbackSpeed.value = clamped
        player.playbackParameters = PlaybackParameters(clamped)
    }

    override fun play(glosses: List<Gloss>) {
        interrupt()
        enqueue(glosses)
    }

    override fun enqueue(glosses: List<Gloss>) {
        if (glosses.isEmpty()) return
        val run = Runnable {
            enqueueAtMs = SystemClock.elapsedRealtime()
            synchronized(queue) {
                queue.addAll(glosses)
                _queueDepth.value = queue.size
            }
            if (!_isPlaying.value) {
                playNext()
            }
        }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            run.run()
        } else {
            android.os.Handler(android.os.Looper.getMainLooper()).post(run)
        }
    }

    override fun interrupt() {
        fallbackAdvance?.let {
            android.os.Handler(android.os.Looper.getMainLooper()).removeCallbacks(it)
        }
        fallbackAdvance = null
        synchronized(queue) {
            queue.clear()
            _queueDepth.value = 0
        }
        player.stop()
        player.clearMediaItems()
        _isPlaying.value = false
        _currentGloss.value = null
        _fallbackLabel.value = null
        _isShowingVideo.value = false
    }

    private fun playNext() {
        val next: Gloss? = synchronized(queue) {
            val g = queue.pollFirst()
            _queueDepth.value = queue.size
            g
        }
        if (next == null) {
            _isPlaying.value = false
            _currentGloss.value = null
            _fallbackLabel.value = null
            _isShowingVideo.value = false
            return
        }
        _currentGloss.value = next
        _isPlaying.value = true
        _lastClipStartLagMs.value = (SystemClock.elapsedRealtime() - enqueueAtMs).coerceAtLeast(0)
        enqueueAtMs = SystemClock.elapsedRealtime()

        val path = next.videoAssetPath
        if (path.isNullOrBlank() || !clipExists(path)) {
            Log.w(TAG, "Missing clip for ${next.glossId}: $path")
            showFallback(next)
            return
        }
        try {
            val uri = resolveClipUri(path)
            _fallbackLabel.value = null
            _isShowingVideo.value = true
            player.setMediaItem(MediaItem.fromUri(uri))
            player.prepare()
            player.playbackParameters = PlaybackParameters(_playbackSpeed.value)
            player.playWhenReady = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start clip $path", e)
            showFallback(next)
        }
    }

    private fun showFallback(gloss: Gloss) {
        _fallbackLabel.value = gloss.displayLabel
        _isShowingVideo.value = false
        player.stop()
        val delay = gloss.durationMs.coerceIn(300L, 2000L)
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        fallbackAdvance?.let { handler.removeCallbacks(it) }
        val r = Runnable { playNext() }
        fallbackAdvance = r
        handler.postDelayed(r, delay)
    }

    private fun clipExists(path: String): Boolean {
        if (path.startsWith("cislr/")) {
            val f = resolveCislrFile(path)
            return f.exists() && f.length() > 1000L
        }
        return try {
            context.assets.open(path).close()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun resolveClipUri(path: String): Uri {
        if (path.startsWith("cislr/")) {
            val f = resolveCislrFile(path)
            require(f.exists()) { "Missing CISLR clip $path" }
            return f.toUri()
        }
        return resolveAssetUri(path)
    }

    private fun resolveCislrFile(path: String): File {
        // path like cislr/videos/HELLO.mp4 → filesDir/sign_sources/cislr/videos/HELLO.mp4
        val relative = path.removePrefix("cislr/")
        return File(File(context.filesDir, "sign_sources/cislr"), relative)
    }

    private fun resolveAssetUri(assetPath: String): Uri {
        assetUriCache[assetPath]?.let { cached ->
            val f = File(context.cacheDir, "sign_clips/${assetPath.replace('/', '_')}")
            if (f.exists() && f.length() > 5000L) return cached
        }
        val out = File(context.cacheDir, "sign_clips/${assetPath.replace('/', '_')}")
        out.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            val bytes = input.readBytes()
            if (!out.exists() || out.length() != bytes.size.toLong()) {
                out.writeBytes(bytes)
            }
        }
        val uri = out.toUri()
        assetUriCache[assetPath] = uri
        return uri
    }

    override fun release() {
        interrupt()
        player.removeListener(listener)
        player.release()
    }

    companion object {
        private const val TAG = "VideoClipSignRenderer"
    }
}
