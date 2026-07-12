package com.liveisl.app.sign

import android.content.Context
import com.liveisl.app.data.Gloss
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

/**
 * Switches between video clips and 3D avatar while exposing one SignRenderer API.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SignRendererHub(
    context: Context,
    scope: CoroutineScope,
    private val preferences: SignPreferences,
) {
    val videoRenderer = VideoClipSignRenderer(context)
    val avatarRenderer = AvatarSignRenderer()

    private val _mode = MutableStateFlow(preferences.signOutputMode)
    val mode: StateFlow<SignOutputMode> = _mode

    private fun rendererFor(mode: SignOutputMode): SignRenderer = when (mode) {
        SignOutputMode.VIDEO -> videoRenderer
        SignOutputMode.AVATAR_3D -> avatarRenderer
    }

    val currentGloss: StateFlow<Gloss?> =
        _mode.flatMapLatest { rendererFor(it).currentGloss }
            .stateIn(scope, SharingStarted.Eagerly, null)

    val queueDepth: StateFlow<Int> =
        _mode.flatMapLatest { rendererFor(it).queueDepth }
            .stateIn(scope, SharingStarted.Eagerly, 0)

    val lastClipStartLagMs: StateFlow<Long> =
        _mode.flatMapLatest { rendererFor(it).lastClipStartLagMs }
            .stateIn(scope, SharingStarted.Eagerly, 0L)

    val isPlaying: StateFlow<Boolean> =
        _mode.flatMapLatest { rendererFor(it).isPlaying }
            .stateIn(scope, SharingStarted.Eagerly, false)

    val isShowingVideo: StateFlow<Boolean> =
        _mode.flatMapLatest { m ->
            if (m == SignOutputMode.VIDEO) videoRenderer.isShowingVideo
            else MutableStateFlow(false)
        }.stateIn(scope, SharingStarted.Eagerly, false)

    val fallbackLabel: StateFlow<String?> =
        _mode.flatMapLatest { m ->
            if (m == SignOutputMode.VIDEO) videoRenderer.fallbackLabel
            else MutableStateFlow(null)
        }.stateIn(scope, SharingStarted.Eagerly, null)

    fun setMode(mode: SignOutputMode) {
        if (_mode.value == mode) return
        videoRenderer.interrupt()
        avatarRenderer.interrupt()
        preferences.signOutputMode = mode
        _mode.value = mode
    }

    fun enqueue(glosses: List<Gloss>) {
        rendererFor(_mode.value).enqueue(glosses)
    }

    fun interrupt() {
        videoRenderer.interrupt()
        avatarRenderer.interrupt()
    }

    fun release() {
        videoRenderer.release()
        avatarRenderer.release()
    }

    fun exoPlayer() = videoRenderer.exoPlayer()
}
