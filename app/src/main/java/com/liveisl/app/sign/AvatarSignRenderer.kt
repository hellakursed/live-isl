package com.liveisl.app.sign

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.liveisl.app.data.Gloss
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

/**
 * 3D mannequin signer — queues glosses and drives multi-keyframe [AvatarPose] clips.
 */
class AvatarSignRenderer : SignRenderer {
    private val queue = ArrayDeque<Gloss>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _currentGloss = MutableStateFlow<Gloss?>(null)
    private val _queueDepth = MutableStateFlow(0)
    private val _lastClipStartLagMs = MutableStateFlow(0L)
    private val _isPlaying = MutableStateFlow(false)
    private val _pose = MutableStateFlow(AvatarPose.NEUTRAL)

    override val currentGloss: StateFlow<Gloss?> = _currentGloss.asStateFlow()
    override val queueDepth: StateFlow<Int> = _queueDepth.asStateFlow()
    override val lastClipStartLagMs: StateFlow<Long> = _lastClipStartLagMs.asStateFlow()
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    val pose: StateFlow<AvatarPose> = _pose.asStateFlow()

    private var enqueueAtMs = 0L
    private var advanceRunnable: Runnable? = null
    private var animRunnable: Runnable? = null
    private var keyframeIndex = 0
    private var activeClip: List<AvatarPose> = emptyList()

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
            if (!_isPlaying.value) playNextGloss()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) run.run() else mainHandler.post(run)
    }

    override fun interrupt() {
        advanceRunnable?.let { mainHandler.removeCallbacks(it) }
        animRunnable?.let { mainHandler.removeCallbacks(it) }
        advanceRunnable = null
        animRunnable = null
        synchronized(queue) {
            queue.clear()
            _queueDepth.value = 0
        }
        _isPlaying.value = false
        _currentGloss.value = null
        activeClip = emptyList()
        keyframeIndex = 0
        _pose.value = AvatarPose.NEUTRAL
    }

    private fun playNextGloss() {
        val next: Gloss? = synchronized(queue) {
            val g = queue.pollFirst()
            _queueDepth.value = queue.size
            g
        }
        if (next == null) {
            _isPlaying.value = false
            _currentGloss.value = null
            animateTo(AvatarPose.NEUTRAL, 400) { }
            return
        }
        _currentGloss.value = next
        _isPlaying.value = true
        _lastClipStartLagMs.value = (SystemClock.elapsedRealtime() - enqueueAtMs).coerceAtLeast(0)
        enqueueAtMs = SystemClock.elapsedRealtime()

        activeClip = AvatarPoseLibrary.clipFor(next.glossId)
        keyframeIndex = 0
        playKeyframe()
    }

    private fun playKeyframe() {
        if (keyframeIndex >= activeClip.size) {
            playNextGloss()
            return
        }
        val frame = activeClip[keyframeIndex]
        val hold = frame.durationMs.coerceIn(220L, 1600L)
        animateTo(frame, minOf(260L, hold / 2)) {
            val r = Runnable {
                keyframeIndex += 1
                playKeyframe()
            }
            advanceRunnable = r
            mainHandler.postDelayed(r, hold)
        }
    }

    private fun animateTo(target: AvatarPose, durationMs: Long, onDone: () -> Unit) {
        animRunnable?.let { mainHandler.removeCallbacks(it) }
        val from = _pose.value
        val start = SystemClock.elapsedRealtime()
        val tick = object : Runnable {
            override fun run() {
                val t = ((SystemClock.elapsedRealtime() - start).toFloat() / durationMs)
                    .coerceIn(0f, 1f)
                val e = 1f - (1f - t) * (1f - t)
                _pose.value = AvatarPose.lerp(from, target, e)
                if (t < 1f) {
                    animRunnable = this
                    mainHandler.postDelayed(this, 16L)
                } else {
                    animRunnable = null
                    _pose.value = target
                    onDone()
                }
            }
        }
        animRunnable = tick
        mainHandler.post(tick)
    }

    override fun release() = interrupt()
}
