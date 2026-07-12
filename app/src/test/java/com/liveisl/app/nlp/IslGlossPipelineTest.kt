package com.liveisl.app.nlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IslGlossPipelinePureTest {
    private val lemmaToGloss = mapOf(
        "hello" to "MEET",
        "hi" to "MEET",
        "water" to "DRINK",
        "want" to "NEED",
        "i" to "I",
        "me" to "I",
        "need" to "NEED",
        "help" to "HELP",
    )

    private val stop = setOf("a", "an", "the", "is", "are", "to", "of", "and")

    private fun process(text: String): List<String> {
        val tokens = text.lowercase()
            .replace(Regex("[^\\p{L}\\p{N}\\s']"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in stop }
        return tokens.mapNotNull { lemmaToGloss[it] }
    }

    @Test
    fun mapsKnownWords() {
        assertEquals(listOf("MEET"), process("hello"))
    }

    @Test
    fun skipsUnknownInsteadOfFingerspell() {
        assertTrue(process("xyzabc").isEmpty())
    }

    @Test
    fun openVocabularySentence() {
        val g = process("I want water")
        assertTrue(g.containsAll(listOf("I", "NEED", "DRINK")))
    }

    @Test
    fun hindiStyleAliasViaMap() {
        assertEquals(listOf("MEET"), process("hi"))
    }
}
