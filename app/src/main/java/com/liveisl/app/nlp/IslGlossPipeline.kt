package com.liveisl.app.nlp

import com.liveisl.app.data.Gloss
import com.liveisl.app.data.GlossDictionary

/**
 * English lemmas → ISL gloss queue using [IslGlossPlanner] (SOV / WH-final).
 *
 * When [showGlossCards] is true, lemmas without a video clip still emit a text gloss
 * card so the signer queue can display the missing sign as uppercase text.
 */
class IslGlossPipeline(
    private val dictionary: GlossDictionary,
) {
    var lastSkippedWords: List<String> = emptyList()
        private set

    fun process(text: String): List<Gloss> {
        val lemmas = IslGlossPlanner.prepareLemmas(IslGlossPlanner.tokenize(text))
        return lemmas.mapNotNull { lemmaToGloss(it, requireVideo = true, showGlossCards = false) }
    }

    fun processCommittedWords(
        words: List<String>,
        requireVideo: Boolean = true,
        showGlossCards: Boolean = false,
    ): List<Gloss> {
        val skipped = mutableListOf<String>()
        val lemmas = IslGlossPlanner.prepareLemmas(words)
        val glosses = lemmas.mapNotNull { lemma ->
            lemmaToGloss(lemma, requireVideo, showGlossCards) ?: run {
                skipped += lemma
                null
            }
        }
        lastSkippedWords = skipped
        return glosses
    }

    private fun lemmaToGloss(
        lemma: String,
        requireVideo: Boolean,
        showGlossCards: Boolean,
    ): Gloss? {
        val entry = dictionary.lookup(lemma)
        if (entry != null) {
            val hasVideo = !entry.videoPath.isNullOrBlank()
            if (requireVideo && !hasVideo && !showGlossCards) return null
            return Gloss(
                glossId = entry.glossId,
                displayLabel = entry.glossId.uppercase(),
                videoAssetPath = entry.videoPath.takeIf { hasVideo },
                durationMs = if (hasVideo) entry.durationMs else 1000L,
                isFingerspell = !hasVideo,
                sourceLemma = lemma,
            )
        }

        // Unknown lemma: avatar always animates; video mode only if gloss cards on.
        if (!requireVideo || showGlossCards) {
            return Gloss(
                glossId = lemma.uppercase(),
                displayLabel = lemma.uppercase(),
                videoAssetPath = null,
                durationMs = 1000L,
                isFingerspell = true,
                sourceLemma = lemma,
            )
        }
        return null
    }
}
