package com.liveisl.app.nlp

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.liveisl.app.asr.AsrLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class TranslationResult(
    val originalText: String,
    val englishText: String,
    val detectedLanguageTag: String,
    val translated: Boolean,
)

/**
 * Detects Hindi / Tamil / Kannada / Bengali via Unicode script, then maps tokens to
 * English using the bundled [indic_to_english.json] asset — no network / model download.
 */
class SpeechToEnglishTranslator(context: Context) {
    private val appContext = context.applicationContext
    private val lexicons: Map<String, Map<String, String>> by lazy { loadLexicons() }

    suspend fun toEnglish(
        text: String,
        languageHint: AsrLanguage = AsrLanguage.AUTO,
    ): TranslationResult = withContext(Dispatchers.Default) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) {
            return@withContext TranslationResult(trimmed, trimmed, "und", translated = false)
        }

        val detected = detectLanguageTag(trimmed, languageHint)
        if (detected == "en" || detected == "und") {
            return@withContext TranslationResult(
                originalText = trimmed,
                englishText = trimmed,
                detectedLanguageTag = "en",
                translated = false,
            )
        }

        val dict = lexicons[detected].orEmpty()
        if (dict.isEmpty()) {
            return@withContext TranslationResult(trimmed, trimmed, detected, translated = false)
        }

        val tokens = tokenize(trimmed)
        var hit = 0
        val englishTokens = tokens.map { token ->
            val key = token.trim()
            val mapped = dict[key]
                ?: dict[key.lowercase()]
                ?: dict.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
            if (mapped != null) {
                hit++
                mapped
            } else {
                // Keep unknown tokens as-is (may already be English / names).
                token
            }
        }
        val english = englishTokens.joinToString(" ").trim()
        TranslationResult(
            originalText = trimmed,
            englishText = english.ifBlank { trimmed },
            detectedLanguageTag = detected,
            translated = hit > 0,
        )
    }

    private fun detectLanguageTag(text: String, hint: AsrLanguage): String {
        scriptGuess(text)?.let { return it }
        if (hint != AsrLanguage.AUTO && hint != AsrLanguage.ENGLISH) {
            return hint.bcp47
        }
        return "en"
    }

    private fun tokenize(text: String): List<String> =
        text.split(Regex("\\s+")).filter { it.isNotBlank() }

    private fun loadLexicons(): Map<String, Map<String, String>> {
        return try {
            appContext.assets.open(ASSET_PATH).bufferedReader().use { reader ->
                val type = object : TypeToken<Map<String, Map<String, String>>>() {}.type
                Gson().fromJson(reader, type) ?: emptyMap()
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun close() = Unit

    companion object {
        private const val ASSET_PATH = "dictionary/indic_to_english.json"

        /** Unicode script detection — no ML model required. */
        fun scriptGuess(text: String): String? {
            var hi = 0
            var ta = 0
            var kn = 0
            var bn = 0
            for (ch in text) {
                when (ch.code) {
                    in 0x0900..0x097F -> hi++ // Devanagari
                    in 0x0B80..0x0BFF -> ta++ // Tamil
                    in 0x0C80..0x0CFF -> kn++ // Kannada
                    in 0x0980..0x09FF -> bn++ // Bengali
                }
            }
            val best = listOf("hi" to hi, "ta" to ta, "kn" to kn, "bn" to bn).maxByOrNull { it.second }
            return if (best != null && best.second >= 1) best.first else null
        }
    }
}
