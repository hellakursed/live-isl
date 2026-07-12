package com.liveisl.app.asr

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class AsrLanguage(val tag: String, val displayName: String) {
    ENGLISH("en-IN", "English"),
    HINDI("hi-IN", "Hindi"),
}

data class AsrPartial(
    val text: String,
    val isFinal: Boolean,
    val latencyMs: Long,
    val engine: String,
)

enum class AsrEngineKind {
    SHERPA,
    ANDROID_SPEECH,
    MOCK,
}

interface StreamingAsr {
    val partials: SharedFlow<AsrPartial>
    val isListening: StateFlow<Boolean>
    val engineKind: AsrEngineKind
    val engineLabel: String

    suspend fun start(language: AsrLanguage = AsrLanguage.ENGLISH)
    suspend fun stop()
    fun release()
}
