package com.example.data.parsing

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Pure file-format handling for the reader: paginates EPUB/MOBI/TXT into text
 * pages and renders PDF pages to bitmaps. Extracted out of the ViewModel so the
 * format logic is isolated, unit-testable, and free of Android lifecycle state.
 *
 * Text formats are split into fixed-size character "pages". [DEFAULT_CHARS_PER_PAGE]
 * is the baseline; callers can pass a smaller/larger value to reflow for a larger
 * or smaller reading font (see font-aware pagination in the reader).
 */
object BookParser {

    const val DEFAULT_CHARS_PER_PAGE = 1400
    const val MIN_CHARS_PER_PAGE = 400
    const val MAX_CHARS_PER_PAGE = 4000

    /** Lowercased file extension, e.g. "pdf", "epub". */
    fun extensionOf(filePath: String): String =
        filePath.substringAfterLast(".", "").lowercase()

    fun isPdf(filePath: String): Boolean = extensionOf(filePath) == "pdf"

    // --- PDF ---

    fun getPdfPageCount(filePath: String): Int {
        return try {
            openRenderer(filePath).use { it.renderer.pageCount }
        } catch (e: Exception) {
            e.printStackTrace()
            0
        }
    }

    /**
     * Renders a single PDF page to a bitmap, upscaled for crisp display.
     * Returns null on any failure or out-of-range index.
     */
    fun renderPdfPage(filePath: String, pageIndex: Int, scale: Float = 1.8f): Bitmap? {
        return try {
            openRenderer(filePath).use { handle ->
                val renderer = handle.renderer
                if (pageIndex < 0 || pageIndex >= renderer.pageCount) return null
                renderer.openPage(pageIndex).use { page ->
                    val width = (page.width * scale).toInt()
                    val height = (page.height * scale).toInt()
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // --- Text formats ---

    /** Convenience: extract then paginate in one call. */
    fun parseText(
        filePath: String,
        charsPerPage: Int = DEFAULT_CHARS_PER_PAGE
    ): List<String> = paginate(extractText(filePath), charsPerPage)

    /**
     * Reads and cleans the full text of a book once. Callers can cache the result
     * and re-[paginate] it cheaply when the reading font changes, instead of
     * re-reading the file. Returns a user-facing placeholder if nothing readable
     * could be extracted.
     */
    fun extractText(filePath: String): String {
        return when (extensionOf(filePath)) {
            "epub" -> extractEpub(filePath)
            "mobi" -> extractMobi(filePath)
            else -> extractTxt(filePath)
        }
    }

    /** Splits full [text] into [charsPerPage]-sized pages (clamped to sane bounds). */
    fun paginate(text: String, charsPerPage: Int = DEFAULT_CHARS_PER_PAGE): List<String> {
        val chars = charsPerPage.coerceIn(MIN_CHARS_PER_PAGE, MAX_CHARS_PER_PAGE)
        val pages = mutableListOf<String>()
        chunkInto(text, chars, pages)
        if (pages.isEmpty()) pages.add(text.ifBlank { "Empty text." })
        return pages
    }

    private fun extractEpub(filePath: String): String {
        val sb = StringBuilder()
        try {
            ZipFile(filePath).use { zipFile ->
                val textEntries = mutableListOf<ZipEntry>()
                val entries = zipFile.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name.lowercase()
                    if ((name.endsWith(".html") || name.endsWith(".xhtml") || name.endsWith(".txt")) &&
                        !name.contains("toc")
                    ) {
                        textEntries.add(entry)
                    }
                }
                textEntries.sortBy { it.name }

                for (entry in textEntries) {
                    zipFile.getInputStream(entry).use { stream ->
                        val reader = BufferedReader(InputStreamReader(stream))
                        val entrySb = StringBuilder()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            entrySb.append(line).append("\n")
                        }
                        val cleaned = stripHtml(entrySb.toString())
                        if (cleaned.isNotEmpty()) sb.append(cleaned).append("\n\n")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return sb.toString().trim().ifEmpty {
            "Empty or unparsable EPUB text. Ensure the EPUB file contains readable xhtml components."
        }
    }

    private fun extractMobi(filePath: String): String {
        try {
            val bytes = FileInputStream(File(filePath)).use { it.readBytes() }
            val sb = StringBuilder()
            var printableCount = 0
            for (b in bytes) {
                val c = b.toInt().toChar()
                if (b in 32..126 || b == '\n'.code.toByte() || b == '\r'.code.toByte() || b == '\t'.code.toByte()) {
                    sb.append(c)
                    printableCount++
                } else {
                    if (printableCount > 20) sb.append(" ")
                    printableCount = 0
                }
            }
            val cleaned = sb.toString()
                .replace(Regex("<[^>]*>"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (cleaned.length > 100) return cleaned
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "Could not extract printable content from MOBI. The file might be encrypted."
    }

    private fun extractTxt(filePath: String): String {
        return try {
            File(filePath).readText(Charsets.UTF_8).ifBlank { "Empty text file." }
        } catch (e: Exception) {
            e.printStackTrace()
            "Empty text file."
        }
    }

    // --- Helpers ---

    private fun stripHtml(html: String): String =
        html.replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()

    /** Splits [text] into [charsPerPage]-sized trimmed chunks, appending to [out]. */
    private fun chunkInto(text: String, charsPerPage: Int, out: MutableList<String>) {
        if (text.isEmpty()) return
        var i = 0
        while (i < text.length) {
            val end = (i + charsPerPage).coerceAtMost(text.length)
            out.add(text.substring(i, end).trim())
            i += charsPerPage
        }
    }

    private fun openRenderer(filePath: String): RendererHandle {
        val pfd = ParcelFileDescriptor.open(File(filePath), ParcelFileDescriptor.MODE_READ_ONLY)
        return RendererHandle(pfd, PdfRenderer(pfd))
    }

    /** Bundles a [PdfRenderer] with its file descriptor so both close together. */
    private class RendererHandle(
        private val pfd: ParcelFileDescriptor,
        val renderer: PdfRenderer
    ) : AutoCloseable {
        override fun close() {
            renderer.close()
            pfd.close()
        }
    }
}
