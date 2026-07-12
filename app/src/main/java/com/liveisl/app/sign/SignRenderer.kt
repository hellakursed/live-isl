package com.liveisl.app.sign

import com.liveisl.app.data.Gloss
import kotlinx.coroutines.flow.StateFlow

/**
 * Pluggable ISL output backend. Video clips now; AvatarSignRenderer later.
 */
interface SignRenderer {
    val currentGloss: StateFlow<Gloss?>
    val queueDepth: StateFlow<Int>
    val lastClipStartLagMs: StateFlow<Long>
    val isPlaying: StateFlow<Boolean>

    fun play(glosses: List<Gloss>)
    fun enqueue(glosses: List<Gloss>)
    fun interrupt()
    fun release()
}
