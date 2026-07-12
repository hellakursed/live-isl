package com.liveisl.app.nlp

import com.liveisl.app.data.Gloss
import com.liveisl.app.data.GlossDictionary

/**
 * Open-vocabulary English/Hindi → ISL gloss pipeline.
 * Prefers dictionary + synonym hits that have real videos.
 * Unknown words are skipped for playback (kept in transcript only) instead of
 * letter-fingerspelling into empty "no video" cards.
 */
class IslGlossPipeline(
    private val dictionary: GlossDictionary,
) {
    private val stopWords = setOf(
        "a", "an", "the", "is", "are", "am", "was", "were", "be", "been",
        "to", "of", "in", "on", "at", "for", "and", "or", "but", "with",
        "do", "does", "did", "have", "has", "had", "will", "would", "can",
        "could", "should", "please", "um", "uh", "just", "really", "very",
        "है", "हैं", "का", "की", "के", "में", "से", "को", "पर", "और",
    )

    private val irregularLemmas = mapOf(
        "are" to "be",
        "is" to "be",
        "am" to "be",
        "was" to "be",
        "were" to "be",
        "went" to "go",
        "gone" to "go",
        "came" to "come",
        "better" to "good",
        "best" to "good",
        "children" to "child",
        "men" to "man",
        "women" to "woman",
        "me" to "i",
        "my" to "i",
        "mine" to "i",
        "your" to "you",
        "yours" to "you",
        "him" to "he",
        "his" to "he",
        "her" to "she",
        "hers" to "she",
        "them" to "they",
        "their" to "they",
        "thanks" to "thank",
        "wanna" to "want",
        "gonna" to "go",
    )

    var lastSkippedWords: List<String> = emptyList()
        private set

    fun process(text: String): List<Gloss> {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return emptyList()
        val lemmas = tokens.map { lemmatize(it) }.filter { it.isNotBlank() && it !in stopWords }
        val reordered = reorderForIsl(lemmas)
        return reordered.mapNotNull { lemmaToGlossOrNull(it) }
    }

    fun processCommittedWords(words: List<String>, requireVideo: Boolean = true): List<Gloss> {
        val skipped = mutableListOf<String>()
        val glosses = words
            .map { lemmatize(it) }
            .filter { it.isNotBlank() && it !in stopWords }
            .let { reorderForIsl(it) }
            .mapNotNull { lemma ->
                lemmaToGlossOrNull(lemma, requireVideo) ?: run {
                    if (!requireVideo) {
                        // 3D mode: still animate unknown lemmas
                        Gloss(
                            glossId = lemma.uppercase(),
                            displayLabel = lemma.uppercase(),
                            videoAssetPath = null,
                            durationMs = 900,
                            sourceLemma = lemma,
                        )
                    } else {
                        skipped += lemma
                        null
                    }
                }
            }
        lastSkippedWords = skipped
        return glosses
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s']"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
    }

    private fun lemmatize(token: String): String {
        irregularLemmas[token]?.let { return it }
        if (token.endsWith("'s")) return token.removeSuffix("'s")
        if (token.endsWith("ing") && token.length > 5) return token.removeSuffix("ing")
        if (token.endsWith("ed") && token.length > 4) return token.removeSuffix("ed")
        if (token.endsWith("ies") && token.length > 4) return token.dropLast(3) + "y"
        if (token.endsWith("es") && token.length > 4) return token.removeSuffix("es")
        if (token.endsWith("s") && token.length > 3 && !token.endsWith("ss")) return token.removeSuffix("s")
        return token
    }

    private fun reorderForIsl(lemmas: List<String>): List<String> {
        if (lemmas.size <= 1) return lemmas
        val timeWords = setOf("today", "tomorrow", "yesterday", "now", "later", "morning", "night")
        val questionWords = setOf(
            "what", "where", "when", "who", "why", "how", "which",
            "क्या", "कहाँ", "कब", "कौन", "क्यों", "कैसे",
        )
        val time = lemmas.filter { it in timeWords }
        val questions = lemmas.filter { it in questionWords }
        val rest = lemmas.filter { it !in timeWords && it !in questionWords }
        val verbs = setOf(
            "want", "need", "go", "come", "see", "eat", "drink", "like", "love",
            "give", "take", "help", "know", "think", "say", "tell", "make", "buy",
        )
        val verbTail = rest.filter { it in verbs }
        val nonVerbs = rest.filter { it !in verbs }
        return (time + questions + nonVerbs + verbTail).distinct().ifEmpty { lemmas }
    }

    private fun lemmaToGlossOrNull(lemma: String, requireVideo: Boolean = true): Gloss? {
        val entry = dictionary.lookup(lemma) ?: return null
        if (requireVideo && entry.videoPath.isNullOrBlank()) return null
        return Gloss(
            glossId = entry.glossId,
            displayLabel = entry.glossId.uppercase(),
            videoAssetPath = entry.videoPath,
            durationMs = entry.durationMs,
            isFingerspell = false,
            sourceLemma = lemma,
        )
    }
}
