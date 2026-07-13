package com.liveisl.app.nlp

/**
 * Research-backed Indian Sign Language gloss planner.
 *
 * Sources informing the rules (clip-limited approximation — no non-manuals / space):
 * - ISL / IPSL is **SOV** (Aboh, Pfau & Zeshan 2005; Zeshan 2003/2004; Singh 2015).
 * - Classic descriptions place **WH-signs clause-finally** (Zeshan 2003/2004;
 *   Aboh et al. 2005). Later work notes occasional initial/doubled WH under emotion
 *   (Kulshreshtha 2020+); we keep the stable pedagogical default: WH final.
 * - Time / scene-setting material tends to appear early (topic–comment / time-first
 *   patterning common in ISL teaching materials and IPSL examples).
 * - Negation is kept near the right periphery (after the verb, before WH).
 * - English articles, copulas, and auxiliaries are dropped (not typically signed).
 *
 * This planner only reorders and filters lemmas for video/avatar gloss queues.
 */
object IslGlossPlanner {

    private val stopWords = setOf(
        "a", "an", "the", "is", "are", "am", "was", "were", "be", "been", "being",
        "to", "of", "in", "on", "at", "for", "and", "or", "but", "with", "from",
        "do", "does", "did", "have", "has", "had", "will", "would", "can",
        "could", "should", "shall", "may", "might", "must", "please",
        "um", "uh", "just", "really", "very", "so", "too", "also",
        "that", "this", "these", "those", // demonstratives often INDEX in ISL; drop bare English
        "it", "its", "as", "if", "than", "then",
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
        "don't" to "not",
        "dont" to "not",
        "doesn't" to "not",
        "didn't" to "not",
        "won't" to "not",
        "can't" to "not",
        "cannot" to "not",
    )

    private val timeWords = setOf(
        "today", "tomorrow", "yesterday", "now", "later", "morning", "night",
        "evening", "afternoon", "always", "sometimes", "before", "after",
    )

    private val questionWords = setOf(
        "what", "where", "when", "who", "why", "how", "which", "whom", "whose",
        "क्या", "कहाँ", "कब", "कौन", "क्यों", "कैसे",
    )

    private val negWords = setOf("no", "not", "never", "none", "nobody", "nothing")

    private val subjectPronouns = setOf(
        "i", "you", "he", "she", "we", "they", "someone", "everybody", "everyone",
    )

    /**
     * Verbal predicates. Ambiguous noun/verb items (help, work, love, …) stay here;
     * [plan] treats all but the final verb-class token as objects (SOV).
     */
    private val verbs = setOf(
        "want", "need", "go", "come", "see", "look", "eat", "drink", "like", "love",
        "give", "take", "help", "know", "think", "say", "tell", "make", "buy",
        "work", "play", "read", "write", "walk", "run", "sleep", "sit", "stand",
        "open", "close", "stop", "wait", "meet", "leave", "ask", "answer",
        "call", "send", "bring", "show", "feel", "live", "stay", "start", "finish",
        "learn", "teach", "use", "find", "put", "get", "keep", "let", "try",
    )

    fun tokenize(text: String): List<String> =
        text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s']"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

    fun lemmatize(token: String): String {
        val raw = token.lowercase().trim()
        irregularLemmas[raw]?.let { return it }
        if (raw.endsWith("'s")) return lemmatize(raw.removeSuffix("'s"))
        // "going".length == 5 — require >= 5 so common progressives lemmatize.
        if (raw.endsWith("ing") && raw.length >= 5) {
            val stem = raw.removeSuffix("ing")
            return stem.ifBlank { raw }
        }
        if (raw.endsWith("ed") && raw.length > 4) return raw.removeSuffix("ed")
        if (raw.endsWith("ies") && raw.length > 4) return raw.dropLast(3) + "y"
        if (raw.endsWith("es") && raw.length > 4) return raw.removeSuffix("es")
        if (raw.endsWith("s") && raw.length > 3 && !raw.endsWith("ss")) return raw.removeSuffix("s")
        return raw
    }

    fun isStopWord(lemma: String): Boolean = lemma in stopWords || lemma == "be"

    /**
     * Plan gloss order for ISL: TIME → SUBJECT → OBJECT/OTHER → VERB → NEG → WH.
     */
    fun plan(lemmas: List<String>): List<String> {
        if (lemmas.size <= 1) return lemmas
        val time = mutableListOf<String>()
        val subjects = mutableListOf<String>()
        val objects = mutableListOf<String>()
        val verbList = mutableListOf<String>()
        val neg = mutableListOf<String>()
        val wh = mutableListOf<String>()

        for (lemma in lemmas) {
            when {
                lemma in questionWords -> wh += lemma
                lemma in timeWords -> time += lemma
                lemma in negWords -> neg += lemma
                lemma in subjectPronouns -> subjects += lemma
                lemma in verbs -> verbList += lemma
                else -> objects += lemma
            }
        }

        // English "need help" / "want water": first verb-class token is the predicate;
        // later verb-class tokens are treated as nominal objects (SOV → object before verb).
        if (verbList.size > 1) {
            val main = verbList.first()
            objects += verbList.drop(1)
            verbList.clear()
            verbList += main
        }

        // SOV-ish: pronouns/subjects before remaining nominals before verb;
        // WH final (classic ISL); NEG after verb.
        return (time + subjects + objects + verbList + neg + wh).distinct()
            .ifEmpty { lemmas }
    }

    fun prepareLemmas(words: List<String>): List<String> =
        words
            .map { lemmatize(it) }
            .filter { it.isNotBlank() && !isStopWord(it) }
            .let { plan(it) }
}
