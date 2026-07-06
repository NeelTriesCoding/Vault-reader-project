package com.example

import com.example.data.parsing.BookParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BookParserTest {

    @Test
    fun `paginate splits text into chunks of the requested size`() {
        val text = "a".repeat(1000)
        val pages = BookParser.paginate(text, charsPerPage = 400)
        // 1000 / 400 -> 3 pages (400, 400, 200)
        assertEquals(3, pages.size)
        assertEquals(400, pages[0].length)
        assertEquals(400, pages[1].length)
        assertEquals(200, pages[2].length)
    }

    @Test
    fun `smaller font-derived size yields more pages`() {
        val text = "b".repeat(4000)
        val fewPages = BookParser.paginate(text, charsPerPage = 2000)
        val manyPages = BookParser.paginate(text, charsPerPage = 500)
        assertTrue(manyPages.size > fewPages.size)
    }

    @Test
    fun `charsPerPage is clamped to sane bounds`() {
        val text = "c".repeat(5000)
        // Way below the minimum should behave like MIN_CHARS_PER_PAGE, not 1.
        val pages = BookParser.paginate(text, charsPerPage = 1)
        val expected = Math.ceil(5000.0 / BookParser.MIN_CHARS_PER_PAGE).toInt()
        assertEquals(expected, pages.size)
    }

    @Test
    fun `blank text yields a single placeholder page`() {
        val pages = BookParser.paginate("", charsPerPage = 400)
        assertEquals(1, pages.size)
    }

    @Test
    fun `extension helpers classify paths`() {
        assertTrue(BookParser.isPdf("/x/y/book.PDF"))
        assertEquals("epub", BookParser.extensionOf("/x/y/book.epub"))
        assertEquals("", BookParser.extensionOf("/x/y/noext"))
    }
}
