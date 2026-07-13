package com.liveisl.app.nlp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IslGlossPlannerTest {
    @Test
    fun timeBeforeSubjectObjectVerb() {
        // "I need help today" → today I help need (TIME SUBJ OBJ VERB)
        val planned = IslGlossPlanner.prepareLemmas(listOf("I", "need", "help", "today"))
        assertEquals(listOf("today", "i", "help", "need"), planned)
    }

    @Test
    fun whQuestionFinal() {
        // Classic ISL: WH clause-final (Zeshan / Aboh et al.)
        val planned = IslGlossPlanner.prepareLemmas(listOf("what", "do", "you", "want"))
        assertEquals(listOf("you", "want", "what"), planned)
    }

    @Test
    fun dropsEnglishFunctionWords() {
        val planned = IslGlossPlanner.prepareLemmas(listOf("I", "am", "going", "to", "the", "school"))
        assertEquals(listOf("i", "school", "go"), planned)
    }

    @Test
    fun negationAfterVerbBeforeWh() {
        val planned = IslGlossPlanner.prepareLemmas(listOf("why", "you", "not", "go"))
        assertEquals(listOf("you", "go", "not", "why"), planned)
    }
}

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

    private fun process(text: String): List<String> {
        val lemmas = IslGlossPlanner.prepareLemmas(IslGlossPlanner.tokenize(text))
        return lemmas.mapNotNull { lemmaToGloss[it] }
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
    fun openVocabularySentenceSovOrder() {
        val g = process("I want water")
        assertEquals(listOf("I", "DRINK", "NEED"), g)
    }

    @Test
    fun hindiStyleAliasViaMap() {
        assertEquals(listOf("MEET"), process("hi"))
    }
}
