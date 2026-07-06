package com.example.api

/**
 * Wraps the Gemini "enrich this note" interaction: prompt construction, the
 * network call, JSON parsing, and merging AI-suggested tags with the user's own.
 * Extracted from the ViewModel so the AI contract lives in one testable place and
 * the ViewModel only has to map the [Outcome] onto UI state.
 */
class AiEnrichmentService(
    private val service: GeminiApiService = GeminiClient.service
) {

    /** Result of a note enrichment attempt. */
    sealed interface Outcome {
        data class Success(
            val title: String,
            val summary: String,
            val mergedTags: List<String>
        ) : Outcome

        /** A user-presentable failure message (empty response, parse error, network, …). */
        data class Failure(val message: String) : Outcome
    }

    suspend fun enrich(
        apiKey: String,
        title: String,
        content: String,
        source: String?,
        userTags: List<String>
    ): Outcome {
        val request = GenerateContentRequest(
            contents = listOf(Content(parts = listOf(Part(text = buildUserPrompt(title, content, source))))),
            generationConfig = GenerationConfig(responseMimeType = "application/json", temperature = 0.2),
            systemInstruction = Content(parts = listOf(Part(text = SYSTEM_PROMPT)))
        )

        return try {
            val response = service.generateContent(apiKey, request)
            val rawText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: return Outcome.Failure("AI response was empty.")

            val enrichment = GeminiClient.parseEnrichment(rawText)
                ?: return Outcome.Failure("Failed to parse AI output. AI output: $rawText")

            Outcome.Success(
                title = enrichment.title.ifBlank { title.ifBlank { "Untitled Note" } },
                summary = enrichment.summary,
                mergedTags = mergeTags(userTags, enrichment.tags)
            )
        } catch (e: Exception) {
            Outcome.Failure("AI Enrichment Failed: ${e.localizedMessage ?: e.message}")
        }
    }

    /** Lightweight one-word round-trip used by the Settings "Test API" button. */
    suspend fun testConnection(apiKey: String): Pair<Boolean, String> {
        return try {
            val response = service.generateContent(
                apiKey = apiKey,
                request = GenerateContentRequest(
                    contents = listOf(Content(parts = listOf(Part(text = "Hello! Answer in exactly one word."))))
                )
            )
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (text != null) {
                Pair(true, "Connection Successful! Response: $text")
            } else {
                Pair(false, "Received empty response from Gemini API.")
            }
        } catch (e: Exception) {
            Pair(false, "Connection Failed: ${e.localizedMessage ?: e.message}")
        }
    }

    companion object {
        /** Normalizes a tag to lowercase, underscore-joined, alphanumeric-ish form. */
        fun normalizeTag(tag: String): String = tag.trim().lowercase().replace(" ", "_")

        /** Merges user tags with AI tags, normalizing and de-duplicating, user first. */
        fun mergeTags(userTags: List<String>, aiTags: List<String>): List<String> {
            val ai = aiTags.map { normalizeTag(it) }
            return (userTags + ai).distinct().filter { it.isNotEmpty() }
        }

        private fun buildUserPrompt(title: String, content: String, source: String?): String = """
            User Title: $title
            Book Source: ${source ?: "None"}
            Note Content:
            $content
        """.trimIndent()

        private val SYSTEM_PROMPT = """
            You are a professional knowledge analyst. Analyze the following note content and output a raw JSON response.

            Rules:
            1. If the User Title is empty or blank, generate a concise, professional title. If not empty, preserve it exactly.
            2. Generate 3 to 5 lowercase tags. Format: lowercase, alphanumeric, replace spaces with underscores (e.g. "philosophy_notes"). Include the book source name as a tag if applicable.
            3. Generate a highly descriptive ~10-sentence summary. It must detail the main arguments, context, and potential intellectual connections to other domains or notes.

            Output ONLY valid JSON matching this schema:
            {
              "title": "the note title",
              "tags": ["tag_one", "tag_two"],
              "summary": "10-sentence rich summary..."
            }
        """.trimIndent()
    }
}
