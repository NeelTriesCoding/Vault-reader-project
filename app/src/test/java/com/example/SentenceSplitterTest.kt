package com.example

import com.example.data.parsing.SentenceSplitter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceSplitterTest {

    @Test
    fun `splits simple sentences`() {
        val result = SentenceSplitter.split("The cat sat. The dog ran! Did it?")
        assertEquals(listOf("The cat sat.", "The dog ran!", "Did it?"), result)
    }

    @Test
    fun `does not split on common abbreviations`() {
        val result = SentenceSplitter.split("Mr. Smith met Dr. Jones at 5 p.m.")
        // "Mr." and "Dr." must not create sentence breaks.
        assertEquals(1, result.size)
        assertTrue(result[0].contains("Mr. Smith"))
        assertTrue(result[0].contains("Dr. Jones"))
    }

    @Test
    fun `does not split on single-letter initials`() {
        val result = SentenceSplitter.split("The author J. R. R. Tolkien wrote it.")
        assertEquals(1, result.size)
    }

    @Test
    fun `splits into two real sentences`() {
        val result = SentenceSplitter.split("He arrived on time. She left later.")
        assertEquals(2, result.size)
        assertTrue(result.last().contains("She left later."))
    }

    @Test
    fun `blank input yields empty list`() {
        assertTrue(SentenceSplitter.split("   ").isEmpty())
    }

    @Test
    fun `single fragment without terminator is one sentence`() {
        val result = SentenceSplitter.split("no terminal punctuation here")
        assertEquals(listOf("no terminal punctuation here"), result)
    }
}
