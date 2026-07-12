package com.liveisl.app.nlp

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.liveisl.app.asr.AsrLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer

data class TranslationResult(
    val originalText: String,
    val englishText: String,
    val detectedLanguageTag: String,
    val translated: Boolean,
)

/**
 * Detects Hindi / Tamil / Kannada / Bengali via Unicode script (or language chip hint),
 * then maps tokens to English using the bundled [indic_to_english.json] asset.
 *
 * Covers both native-script ASR output and common Latin transliterations
 * (many devices return "namaste" / "vanakkam" instead of native glyphs).
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
        val englishTokens = tokens.mapNotNull { token ->
            val mapped = lookup(dict, token)
            when {
                mapped != null -> {
                    hit++
                    mapped
                }
                // Drop leftover native-script particles that never map to ISL lemmas.
                scriptGuess(token) != null -> null
                else -> token
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
        // Chip selected Indic: treat Latin ASR output as that language (romanization).
        if (hint != AsrLanguage.AUTO && hint != AsrLanguage.ENGLISH) {
            return hint.bcp47
        }
        return "en"
    }

    private fun tokenize(text: String): List<String> {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFC)
        return normalized
            .split(Regex("[\\s\\u00A0]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun lookup(dict: Map<String, String>, raw: String): String? {
        val variants = keyVariants(raw)
        for (key in variants) {
            dict[key]?.let { return it }
            dict[key.lowercase()]?.let { return it }
        }
        // Case-insensitive fallback for Latin keys only.
        for (key in variants) {
            if (key.any { it.code > 127 }) continue
            dict.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value?.let {
                return it
            }
        }
        return null
    }

    private fun keyVariants(raw: String): List<String> {
        val stripped = raw
            .trim()
            .trim('"', '\'', '`', '“', '”', '‘', '’')
            .replace(PUNCTUATION, "")
            .trim()
        if (stripped.isEmpty()) return emptyList()
        val nfc = Normalizer.normalize(stripped, Normalizer.Form.NFC)
        val nfkc = Normalizer.normalize(stripped, Normalizer.Form.NFKC)
        return listOf(nfc, nfkc, stripped).distinct()
    }

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
        private val PUNCTUATION = Regex("[\\p{Punct}\\p{IsPunctuation}।॥٬،؟！？…]+")

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
