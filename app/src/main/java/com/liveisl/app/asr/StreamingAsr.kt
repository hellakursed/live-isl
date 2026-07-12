package com.liveisl.app.asr

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Speech recognition locale. Pick the language you speak for best ASR accuracy.
 * Non-English text is still mapped to English lemmas before ISL (bundled lexicon).
 */
enum class AsrLanguage(
    val tag: String,
    val displayName: String,
    /** BCP-47 language code used for Indic→English lexicon lookup. */
    val bcp47: String,
) {
    ENGLISH("en-IN", "English", "en"),
    HINDI("hi-IN", "Hindi", "hi"),
    TAMIL("ta-IN", "Tamil", "ta"),
    KANNADA("kn-IN", "Kannada", "kn"),
    BENGALI("bn-IN", "Bengali", "bn"),
    AUTO("en-IN", "Auto", "und"),
    ;

    fun next(): AsrLanguage {
        val all = entries
        return all[(ordinal + 1) % all.size]
    }

    /** Locale passed to SpeechRecognizer (AUTO listens as English). */
    fun speechLocale(): AsrLanguage = if (this == AUTO) ENGLISH else this

    companion object {
        fun fromBcp47(tag: String?): AsrLanguage =
            entries.firstOrNull { it != AUTO && it.bcp47 == tag } ?: ENGLISH
    }
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
