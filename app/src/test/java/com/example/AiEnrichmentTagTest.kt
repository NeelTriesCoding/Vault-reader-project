package com.example

import com.example.api.AiEnrichmentService
import org.junit.Assert.assertEquals
import org.junit.Test

class AiEnrichmentTagTest {

    @Test
    fun `normalizeTag lowercases and underscores`() {
        assertEquals("philosophy_notes", AiEnrichmentService.normalizeTag("Philosophy Notes"))
        assertEquals("stoicism", AiEnrichmentService.normalizeTag("  Stoicism  "))
    }

    @Test
    fun `mergeTags keeps user tags first and de-duplicates`() {
        val merged = AiEnrichmentService.mergeTags(
            userTags = listOf("stoicism", "ethics"),
            aiTags = listOf("Ethics", "virtue", "stoicism")
        )
        // Order: user tags first, then new AI tags; no duplicates (normalized).
        assertEquals(listOf("stoicism", "ethics", "virtue"), merged)
    }

    @Test
    fun `mergeTags drops blanks`() {
        val merged = AiEnrichmentService.mergeTags(
            userTags = listOf("keep"),
            aiTags = listOf("", "   ")
        )
        assertEquals(listOf("keep"), merged)
    }
}
