package com.example.data.parsing

/**
 * Splits a block of prose into sentences for the reader's tap-to-highlight UI.
 *
 * A naive "split on [.!?] + space" breaks on abbreviations ("Mr. Smith",
 * "e.g. this") and initials ("J. R. R. Tolkien"). This splitter suppresses a
 * boundary when the token immediately before the terminator is a known
 * abbreviation or a single-letter initial.
 */
object SentenceSplitter {

    private val ABBREVIATIONS = setOf(
        "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs", "etc",
        "e.g", "i.e", "fig", "al", "inc", "ltd", "co", "vol", "no", "pp",
        "ch", "approx", "dept", "est", "gen", "gov", "capt", "sgt", "rev"
    )

    private val BOUNDARY = Regex("([.!?]+)([\"'”’)\\]]?)(\\s+)")

    fun split(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val sentences = mutableListOf<String>()
        var start = 0
        for (m in BOUNDARY.findAll(text)) {
            val punctIndex = m.range.first
            if (endsWithAbbreviation(text, punctIndex)) continue
            val end = m.range.last + 1
            val sentence = text.substring(start, end).trim()
            if (sentence.isNotEmpty()) sentences.add(sentence)
            start = end
        }
        val tail = text.substring(start).trim()
        if (tail.isNotEmpty()) sentences.add(tail)
        if (sentences.isEmpty()) sentences.add(text.trim())
        return sentences
    }

    /** True if the word ending just before [punctIndex] is an abbreviation/initial. */
    private fun endsWithAbbreviation(text: String, punctIndex: Int): Boolean {
        var i = punctIndex - 1
        if (i < 0) return false
        val sb = StringBuilder()
        while (i >= 0 && (text[i].isLetterOrDigit() || text[i] == '.')) {
            sb.append(text[i])
            i--
        }
        val word = sb.reverse().toString().trimEnd('.').lowercase()
        if (word.isEmpty()) return false
        if (word.length == 1 && word[0].isLetter()) return true // single initial, e.g. "J."
        return word in ABBREVIATIONS
    }
}
