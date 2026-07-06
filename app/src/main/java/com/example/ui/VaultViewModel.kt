package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.AiEnrichmentService
import com.example.data.Book
import com.example.data.Link
import com.example.data.Note
import com.example.data.Highlight
import com.example.data.SecureKeyStore
import com.example.data.VaultRepository
import com.example.data.parsing.BookParser
import com.example.ui.map.MapLayoutEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

class VaultViewModel(
    application: Application,
    private val repository: VaultRepository
) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val prefs = context.getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)
    private val secureKeyStore = SecureKeyStore(context)
    private val aiService = AiEnrichmentService()

    init {
        migrateLegacyApiKey()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val existingBooks = repository.allBooks.first()
                if (existingBooks.isEmpty()) {
                    seedData()
                }
            } catch (e: Exception) {
                Log.e("VaultViewModel", "Initialization check failed", e)
            }
        }
    }

    // --- Gemini API Key (encrypted at rest via SecureKeyStore) ---
    private val _apiKey = MutableStateFlow(secureKeyStore.getApiKey())
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    fun saveApiKey(key: String) {
        secureKeyStore.setApiKey(key)
        _apiKey.value = key
    }

    /**
     * One-time migration: earlier builds stored the key in plaintext under
     * "gemini_api_key" in the regular prefs. Move it into the encrypted store
     * and scrub the plaintext copy so keys aren't left readable on disk.
     */
    private fun migrateLegacyApiKey() {
        val legacyKey = prefs.getString("gemini_api_key", null)
        if (!legacyKey.isNullOrEmpty()) {
            if (secureKeyStore.getApiKey().isEmpty()) {
                secureKeyStore.setApiKey(legacyKey)
            }
            prefs.edit().remove("gemini_api_key").apply()
        }
    }

    // --- State: Database Flows ---
    val notes: StateFlow<List<Note>> = repository.allNotes.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val books: StateFlow<List<Book>> = repository.allBooks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val links: StateFlow<List<Link>> = repository.allLinks.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val highlights: StateFlow<List<Highlight>> = repository.allHighlights.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // --- Computed Shelves ---
    val shelves: StateFlow<Set<String>> = books.combine(MutableStateFlow(emptySet<String>())) { bookList, customShelves ->
        val bookShelves = bookList.flatMap { it.shelves }.toSet()
        bookShelves + customShelves
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // --- Active Reader State ---
    private val _currentBook = MutableStateFlow<Book?>(null)
    val currentBook: StateFlow<Book?> = _currentBook.asStateFlow()

    private val _readerPages = MutableStateFlow<List<String>>(emptyList())
    val readerPages: StateFlow<List<String>> = _readerPages.asStateFlow()

    // Cached full text of the open text book, so changing font re-paginates from
    // memory instead of re-reading the file. Null for PDFs / when no book is open.
    private var currentBookRawText: String? = null

    private val _readerPageCount = MutableStateFlow(0)
    val readerPageCount: StateFlow<Int> = _readerPageCount.asStateFlow()

    private val _currentPageIndex = MutableStateFlow(0)
    val currentPageIndex: StateFlow<Int> = _currentPageIndex.asStateFlow()

    private val _readingTheme = MutableStateFlow(prefs.getString("reading_theme", "sepia") ?: "sepia")
    val readingTheme: StateFlow<String> = _readingTheme.asStateFlow()

    private val _fontSize = MutableStateFlow(prefs.getFloat("font_size", 18f))
    val fontSize: StateFlow<Float> = _fontSize.asStateFlow()

    // Bounded LRU cache of rendered PDF pages for speed (see loadPdfBitmap).
    private val _pdfBitmaps = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())
    val pdfBitmaps: StateFlow<Map<Int, Bitmap>> = _pdfBitmaps.asStateFlow()
    private val MAX_CACHED_PDF_PAGES = 4

    fun setReadingTheme(theme: String) {
        prefs.edit().putString("reading_theme", theme).apply()
        _readingTheme.value = theme
    }

    fun setFontSize(size: Float) {
        prefs.edit().putFloat("font_size", size).apply()
        _fontSize.value = size
        repaginateCurrentTextBook()
    }

    /**
     * Re-chunks the open text book for the current font, preserving reading
     * position proportionally. No-op for PDFs (page count is fixed) or when no
     * text book is open. Cheap: re-chunks cached text, no file I/O.
     */
    private fun repaginateCurrentTextBook() {
        val raw = currentBookRawText ?: return
        val oldCount = _readerPageCount.value
        val oldIndex = _currentPageIndex.value
        val fraction = if (oldCount > 1) oldIndex.toFloat() / (oldCount - 1) else 0f

        val pages = BookParser.paginate(raw, charsPerPageFor(_fontSize.value))
        _readerPages.value = pages
        _readerPageCount.value = pages.size
        _currentPageIndex.value = if (pages.size > 1) {
            (fraction * (pages.size - 1)).roundToInt().coerceIn(0, pages.size - 1)
        } else 0
    }

    // --- Visual Map Zoom & Pan State ---
    val mapScale = MutableStateFlow(1f)
    val mapOffsetX = MutableStateFlow(0f)
    val mapOffsetY = MutableStateFlow(0f)

    // Map Filters
    val mapTagFilter = MutableStateFlow<String?>(null)
    val mapShelfFilter = MutableStateFlow<String?>(null)

    // Node Positions
    private val _nodePositions = MutableStateFlow<Map<Long, Pair<Float, Float>>>(emptyMap())
    val nodePositions: StateFlow<Map<Long, Pair<Float, Float>>> = _nodePositions.asStateFlow()

    // --- Connection Test State ---
    private val _testConnectionResult = MutableStateFlow<Pair<Boolean, String>?>(null)
    val testConnectionResult = _testConnectionResult.asStateFlow()

    fun clearTestConnectionResult() {
        _testConnectionResult.value = null
    }

    fun testConnection(apiKeyToTest: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val key = apiKeyToTest.trim().ifEmpty { getActiveApiKey() }
            if (key.isEmpty()) {
                _testConnectionResult.value = Pair(false, "API Key is empty! Paste a key or configure .env")
                return@launch
            }

            _testConnectionResult.value = aiService.testConnection(key)
        }
    }

    // Get active key: user pasted, or fallback to BuildConfig.GEMINI_API_KEY if configured
    private fun getActiveApiKey(): String {
        return _apiKey.value.trim().ifEmpty {
            try {
                val fallback = com.example.BuildConfig.GEMINI_API_KEY
                if (fallback != "MY_GEMINI_API_KEY") fallback else ""
            } catch (e: Exception) {
                ""
            }
        }
    }

    // --- Core Operations: Ebook Reader ---

    fun importBook(uri: Uri, fileName: String, shelvesList: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val file = copyUriToInternal(uri, fileName)
            if (file != null) {
                val book = Book(
                    title = fileName.substringBeforeLast("."),
                    filePath = file.absolutePath,
                    lastPosition = 0,
                    shelves = shelvesList
                )
                repository.insertBook(book)
            }
        }
    }

    private fun copyUriToInternal(uri: Uri, fileName: String): File? {
        return try {
            val destFile = File(context.filesDir, "${System.currentTimeMillis()}_$fileName")
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            destFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun selectBook(book: Book) {
        _currentBook.value = book
        _currentPageIndex.value = book.lastPosition
        _pdfBitmaps.value = emptyMap() // Clear old PDF cache

        viewModelScope.launch(Dispatchers.IO) {
            if (BookParser.isPdf(book.filePath)) {
                currentBookRawText = null
                val count = BookParser.getPdfPageCount(book.filePath)
                _readerPageCount.value = count
                _readerPages.value = List(count) { "Page ${it + 1}" }
                loadPdfBitmap(book.filePath, book.lastPosition)
            } else {
                val raw = BookParser.extractText(book.filePath)
                currentBookRawText = raw
                val pages = BookParser.paginate(raw, charsPerPageFor(_fontSize.value))
                _readerPages.value = pages
                _readerPageCount.value = pages.size
            }
            persistTotalPagesIfNeeded(book, _readerPageCount.value)
        }
    }

    /**
     * Larger reading fonts fit fewer characters per page, so page chunks shrink as
     * the font grows. Glyph area scales with the square of the font size, so chars
     * per page scales with (baseline / fontSize)^2. 18sp is the baseline.
     */
    private fun charsPerPageFor(fontSize: Float): Int {
        val scale = 18f / fontSize.coerceAtLeast(1f)
        return (BookParser.DEFAULT_CHARS_PER_PAGE * scale * scale).toInt().coerceIn(
            BookParser.MIN_CHARS_PER_PAGE,
            BookParser.MAX_CHARS_PER_PAGE
        )
    }

    private suspend fun persistTotalPagesIfNeeded(book: Book, pageCount: Int) {
        if (pageCount > 0 && book.totalPages != pageCount) {
            repository.updateTotalPages(book.id, pageCount)
        }
    }

    fun closeBook() {
        val book = _currentBook.value
        if (book != null) {
            val page = _currentPageIndex.value
            viewModelScope.launch(Dispatchers.IO) {
                repository.updateLastPosition(book.id, page)
            }
        }
        _currentBook.value = null
        _readerPages.value = emptyList()
        _readerPageCount.value = 0
        _pdfBitmaps.value = emptyMap()
        currentBookRawText = null
    }

    fun nextPage() {
        val current = _currentPageIndex.value
        val total = _readerPageCount.value
        if (current < total - 1) {
            val nextPageIdx = current + 1
            _currentPageIndex.value = nextPageIdx
            saveBookPosition(nextPageIdx)
            prefetchPdfIfNecessary(nextPageIdx)
        }
    }

    fun prevPage() {
        val current = _currentPageIndex.value
        if (current > 0) {
            val prevPageIdx = current - 1
            _currentPageIndex.value = prevPageIdx
            saveBookPosition(prevPageIdx)
            prefetchPdfIfNecessary(prevPageIdx)
        }
    }

    fun goToPage(page: Int) {
        val total = _readerPageCount.value
        if (page in 0 until total) {
            _currentPageIndex.value = page
            saveBookPosition(page)
            prefetchPdfIfNecessary(page)
        }
    }

    private fun saveBookPosition(position: Int) {
        val book = _currentBook.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateLastPosition(book.id, position)
        }
    }

    private fun prefetchPdfIfNecessary(pageIndex: Int) {
        val book = _currentBook.value ?: return
        if (BookParser.isPdf(book.filePath)) {
            viewModelScope.launch(Dispatchers.IO) {
                loadPdfBitmap(book.filePath, pageIndex)
                // Warm up next page
                if (pageIndex + 1 < _readerPageCount.value) {
                    loadPdfBitmap(book.filePath, pageIndex + 1)
                }
            }
        }
    }

    private fun loadPdfBitmap(filePath: String, pageIndex: Int) {
        if (_pdfBitmaps.value.containsKey(pageIndex)) return
        val bitmap = BookParser.renderPdfPage(filePath, pageIndex) ?: return
        // Bounded LRU: keep only the pages nearest the current one so a long PDF
        // can't accumulate unbounded bitmaps and exhaust memory.
        val updated = LinkedHashMap(_pdfBitmaps.value)
        updated[pageIndex] = bitmap
        while (updated.size > MAX_CACHED_PDF_PAGES) {
            val evict = updated.keys
                .filter { it != pageIndex }
                .maxByOrNull { kotlin.math.abs(it - _currentPageIndex.value) }
                ?: break
            updated.remove(evict)
        }
        _pdfBitmaps.value = updated
    }

    fun addShelfToBook(bookId: Long, newShelf: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val book = repository.getBookById(bookId) ?: return@launch
            val cleaned = newShelf.trim()
            if (cleaned.isNotEmpty() && !book.shelves.contains(cleaned)) {
                val updated = book.shelves + cleaned
                repository.updateShelves(bookId, updated)
            }
        }
    }

    fun removeShelfFromBook(bookId: Long, shelf: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val book = repository.getBookById(bookId) ?: return@launch
            val updated = book.shelves - shelf
            repository.updateShelves(bookId, updated)
        }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteBook(book)
        }
    }

    // --- Batch Tag Editor ---
    fun batchEditBookTags(bookTitle: String, action: String, targetTag: String, replacementTag: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            val allNotesList = notes.first()
            val bookNotes = allNotesList.filter { it.source == bookTitle }
            val formattedTarget = AiEnrichmentService.normalizeTag(targetTag)
            val formattedReplacement = AiEnrichmentService.normalizeTag(replacementTag)

            for (note in bookNotes) {
                val updatedTags = note.tags.toMutableList()
                when (action) {
                    "ADD" -> {
                        if (formattedTarget.isNotEmpty() && !updatedTags.contains(formattedTarget)) {
                            updatedTags.add(formattedTarget)
                            repository.updateNoteTags(note.id, updatedTags)
                        }
                    }
                    "REMOVE" -> {
                        if (updatedTags.contains(formattedTarget)) {
                            updatedTags.remove(formattedTarget)
                            repository.updateNoteTags(note.id, updatedTags)
                        }
                    }
                    "RENAME" -> {
                        if (updatedTags.contains(formattedTarget) && formattedReplacement.isNotEmpty()) {
                            updatedTags.remove(formattedTarget)
                            if (!updatedTags.contains(formattedReplacement)) {
                                updatedTags.add(formattedReplacement)
                            }
                            repository.updateNoteTags(note.id, updatedTags)
                        }
                    }
                }
            }
        }
    }

    // --- Core Operations: Note Taker & AI Tagger ---

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    private val _showAiConfirmation = MutableStateFlow(false)
    val showAiConfirmation: StateFlow<Boolean> = _showAiConfirmation.asStateFlow()

    fun clearAiError() {
        _aiError.value = null
    }

    fun dismissAiConfirmation() {
        _showAiConfirmation.value = false
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteNote(note)
        }
    }

    fun insertLink(noteIdA: Long, noteIdB: Long, relationship: String, reasoning: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertLink(Link(noteIdA = noteIdA, noteIdB = noteIdB, relationship = relationship, reasoning = reasoning))
        }
    }

    fun deleteLink(linkId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteLinkById(linkId)
        }
    }

    // Saves a note and triggers Gemini AI if key is present
    fun saveNoteAndEnrich(
        id: Long = 0,
        title: String,
        content: String,
        source: String?,
        origin: String = "user",
        userTags: List<String> = emptyList(),
        triggerAi: Boolean = true
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isAiLoading.value = true
            _aiError.value = null

            // 1. Initial Insert to get a valid ID (or update)
            val rawNote = Note(
                id = id,
                title = title,
                content = content,
                source = source,
                origin = origin,
                tags = userTags
            )
            val noteId = repository.insertNote(rawNote)
            val activeNoteId = if (id == 0L) noteId else id

            // 2. AI Enrichment if requested and key is available
            val activeKey = getActiveApiKey()
            if (triggerAi && activeKey.isNotEmpty() && content.isNotBlank()) {
                when (val outcome = aiService.enrich(activeKey, title, content, source, userTags)) {
                    is AiEnrichmentService.Outcome.Success -> {
                        val enrichedNote = Note(
                            id = activeNoteId,
                            title = outcome.title,
                            content = content,
                            summary = outcome.summary,
                            source = source,
                            tags = outcome.mergedTags,
                            origin = "ai" // Mark as AI origin once enriched
                        )
                        repository.insertNote(enrichedNote)
                        _showAiConfirmation.value = true
                        autoSuggestLinks(enrichedNote)
                    }
                    is AiEnrichmentService.Outcome.Failure -> {
                        _aiError.value = outcome.message
                    }
                }
                _isAiLoading.value = false
            } else {
                _isAiLoading.value = false
                if (triggerAi && activeKey.isEmpty()) {
                    _aiError.value = "No API Key found. To use AI auto-tagging, please paste your Gemini API Key in the Settings tab."
                }
            }
        }
    }

    // Auto-link notes based on tag intersections
    private suspend fun autoSuggestLinks(newNote: Note) {
        val allNotesList = repository.allNotes.first()
        for (otherNote in allNotesList) {
            if (otherNote.id == newNote.id) continue
            // Check if they share tags
            val commonTags = newNote.tags.intersect(otherNote.tags.toSet())
            if (commonTags.isNotEmpty()) {
                val currentLinks = repository.allLinks.first()
                val alreadyLinked = currentLinks.any { 
                    (it.noteIdA == newNote.id && it.noteIdB == otherNote.id) || 
                    (it.noteIdA == otherNote.id && it.noteIdB == newNote.id)
                }
                if (!alreadyLinked) {
                    repository.insertLink(
                        Link(
                            noteIdA = newNote.id,
                            noteIdB = otherNote.id,
                            relationship = "Shared tags",
                            reasoning = "Notes are linked through common tags: ${commonTags.joinToString(", ")}"
                        )
                    )
                }
            }
        }
    }

    // --- Node Map Position Initializer ---
    fun updateMapPositions() {
        viewModelScope.launch {
            val noteList = notes.first()
            if (noteList.isEmpty()) return@launch

            val positions = _nodePositions.value.toMutableMap()
            var index = 0
            for (note in noteList) {
                if (!positions.containsKey(note.id)) {
                    // Prefer a persisted position; otherwise seed on the spiral.
                    val persisted = if (note.posX != null && note.posY != null) {
                        Pair(note.posX, note.posY)
                    } else null
                    positions[note.id] = persisted ?: MapLayoutEngine.seedPosition(index)
                }
                index++
            }
            _nodePositions.value = positions
        }
    }

    fun moveNode(noteId: Long, dx: Float, dy: Float) {
        val current = _nodePositions.value[noteId] ?: Pair(0f, 0f)
        _nodePositions.value = _nodePositions.value + (noteId to Pair(current.first + dx, current.second + dy))
    }

    /** Persists a node's position after a drag ends, so the layout survives restarts. */
    fun commitNodePosition(noteId: Long) {
        val pos = _nodePositions.value[noteId] ?: return
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateNotePosition(noteId, pos.first, pos.second)
        }
    }

    /**
     * Runs a force-directed relaxation over the current node positions using the
     * links as springs, then persists the settled layout. Linked notes pull
     * together; unrelated ones spread apart.
     */
    fun autoArrangeMap() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _nodePositions.value
            if (current.size < 2) return@launch
            val edges = links.first().map { it.noteIdA to it.noteIdB }
            val relaxed = MapLayoutEngine.relax(current, edges)
            _nodePositions.value = relaxed
            relaxed.forEach { (id, p) -> repository.updateNotePosition(id, p.first, p.second) }
        }
    }

    /** Resets pan/zoom to the default identity view. */
    fun resetMapView() {
        mapScale.value = 1f
        mapOffsetX.value = 0f
        mapOffsetY.value = 0f
    }

    // --- Highlights Management ---
    // Keyed by sentenceIndex within the page so duplicate sentences and text
    // reflow don't collide (sentenceText is still stored for display/quotes).
    fun addHighlight(bookId: Long, pageIndex: Int, sentenceIndex: Int, sentenceText: String, colorHex: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertHighlight(
                Highlight(
                    bookId = bookId,
                    pageIndex = pageIndex,
                    sentenceIndex = sentenceIndex,
                    sentenceText = sentenceText.trim(),
                    colorHex = colorHex
                )
            )
        }
    }

    fun removeHighlight(bookId: Long, pageIndex: Int, sentenceIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteHighlightAtIndex(bookId, pageIndex, sentenceIndex)
        }
    }

    private fun seedData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Book 1: Meditations
                val file1 = File(context.filesDir, "meditations.txt")
                val med1 = """
                    We are made for cooperation, like feet, like hands, like the rows of the upper and lower teeth. To act against one another then is contrary to nature; and it is acting against one another to be vexed and to turn away.
                    
                    When you wake up in the morning, tell yourself: The people I deal with today will be meddling, ungrateful, arrogant, dishonest, jealous, and surly. They are like this because they cannot distinguish good from evil. But I have seen the beauty of good, and the ugliness of evil, and have recognized that the wrongdoer has a nature related to my own.
                """.trimIndent()
                val med2 = """
                    Everything we hear is an opinion, not a fact. Everything we see is a perspective, not the truth.
                    
                    If you are distressed by anything external, the pain is not due to the thing itself, but to your estimate of it; and this you have the power to revoke at any moment.
                """.trimIndent()
                val med3 = """
                    The happiness of your life depends upon the quality of your thoughts: therefore, guard accordingly, and take care that you entertain no notions unsuitable to virtue and reasonable nature.
                """.trimIndent()
                val medText = med1.padEnd(1400, ' ') + med2.padEnd(1400, ' ') + med3
                file1.writeText(medText, Charsets.UTF_8)

                val bookId1 = repository.insertBook(
                    Book(
                        title = "Meditations - Marcus Aurelius",
                        filePath = file1.absolutePath,
                        lastPosition = 0,
                        shelves = listOf("Philosophy", "Stoicism")
                    )
                )

                // Book 2: Beyond Good and Evil
                val file2 = File(context.filesDir, "beyond_good_evil.txt")
                val bg1 = """
                    He who fights with monsters might take care lest he thereby become a monster. And if you gaze for long into an abyss, the abyss gazes also into you.
                    
                    What is done out of love always takes place beyond good and evil.
                """.trimIndent()
                val bg2 = """
                    The demand to be loved is the greatest of all arrogant assumptions.
                    
                    There are no moral phenomena at all, only a moral interpretation of phenomena.
                """.trimIndent()
                val bgText = bg1.padEnd(1400, ' ') + bg2
                file2.writeText(bgText, Charsets.UTF_8)

                val bookId2 = repository.insertBook(
                    Book(
                        title = "Beyond Good and Evil - Nietzsche",
                        filePath = file2.absolutePath,
                        lastPosition = 0,
                        shelves = listOf("Philosophy", "Existentialism")
                    )
                )

                // Book 3: The Republic
                val file3 = File(context.filesDir, "the_republic.txt")
                val rep1 = """
                    The Allegory of the Cave: Imagine human beings living in an underground, cave-like dwelling, with an entrance open to the light... They have been there since childhood, with their necks and legs fettered, so they can only look forward.
                    
                    Behind them, at a distance, is a fire burning, and between the fire and the prisoners there is a raised way with a low wall.
                """.trimIndent()
                val rep2 = """
                    The heaviest penalty for deciding not to rule is to be ruled by someone worse than oneself.
                    
                    Wise men speak because they have something to say; fools because they have to say something.
                """.trimIndent()
                val repText = rep1.padEnd(1400, ' ') + rep2
                file3.writeText(repText, Charsets.UTF_8)

                val bookId3 = repository.insertBook(
                    Book(
                        title = "The Republic - Plato",
                        filePath = file3.absolutePath,
                        lastPosition = 0,
                        shelves = listOf("Philosophy", "Classics")
                    )
                )

                // Insert sample Notes
                val noteId1 = repository.insertNote(
                    Note(
                        title = "Marcus Aurelius on Morning Preparation",
                        content = "Preparing oneself each morning for difficult people keeps one's expectations grounded. By understanding that others act out of ignorance of good and evil, we can maintain empathy and avoid vexation.",
                        summary = "Marcus Aurelius discusses maintaining empathy and expectation-setting during morning preparation.",
                        createdAt = System.currentTimeMillis() - 86400000 * 2, // 2 days ago
                        tags = listOf("stoicism", "empathy", "morning_routine"),
                        source = "Meditations - Marcus Aurelius, Page 1",
                        origin = "user"
                    )
                )

                val noteId2 = repository.insertNote(
                    Note(
                        title = "Nietzsche's Monster and Abyss",
                        content = "Fighting monsters runs the risk of becoming a monster. This caution highlights the danger of adopting the methods and cruelty of the adversary in any ideological or physical battle. Gazing into the abyss indicates how deep obsession can consume our thoughts.",
                        summary = "Nietzsche warning about becoming a monster and the recursive consuming nature of the abyss.",
                        createdAt = System.currentTimeMillis() - 86400000 * 1, // 1 day ago
                        tags = listOf("nietzsche", "existentialism", "psychology"),
                        source = "Beyond Good and Evil - Nietzsche, Page 1",
                        origin = "user"
                    )
                )

                val noteId3 = repository.insertNote(
                    Note(
                        title = "Plato's Allegory of the Cave",
                        content = "The prisoners in the cave can only see shadows projected on the wall, which they mistake for absolute reality. True enlightenment involves escaping the shackles, ascending to the surface, and seeing the fire and the sun directly.",
                        summary = "The classic Cave Allegory where perceived shadows on walls are mistaken for objective reality.",
                        createdAt = System.currentTimeMillis() - 3600000 * 4, // 4 hours ago
                        tags = listOf("plato", "epistemology", "enlightenment"),
                        source = "The Republic - Plato, Page 1",
                        origin = "user"
                    )
                )

                val noteId4 = repository.insertNote(
                    Note(
                        title = "AI Analysis: Comparative Ethics",
                        content = "A fascinating overlap exists between Stoic virtue in Meditations and Plato's Enlightened rulers. While Marcus Aurelius focuses on personal virtue and accepting one's social duty, Plato's Republic constructs an ideal state led by philosopher-kings who have ascended from the cave. In contrast, Nietzsche's 'Beyond Good and Evil' rejects both traditional virtue and systemic ideals as limiting constructs.",
                        summary = "Synthesizing classical virtues from Aurelius and Plato with Nietzsche's rejection of moral frameworks.",
                        createdAt = System.currentTimeMillis(), // now
                        tags = listOf("comparative_philosophy", "ethics", "virtue"),
                        source = "Gemini Synthesis",
                        origin = "ai"
                    )
                )

                // Insert sample visual links connecting the notes
                repository.insertLink(
                    Link(
                        noteIdA = noteId1,
                        noteIdB = noteId4,
                        relationship = "Virtue Comparison",
                        reasoning = "Compares Aurelius' morning stoic virtue with comparative ethical systems."
                    )
                )

                repository.insertLink(
                    Link(
                        noteIdA = noteId3,
                        noteIdB = noteId4,
                        relationship = "Intellectual Ascent",
                        reasoning = "Links Plato's philosopher ascension to synthesis of political and personal ideals."
                    )
                )

                repository.insertLink(
                    Link(
                        noteIdA = noteId2,
                        noteIdB = noteId4,
                        relationship = "Existential Divergence",
                        reasoning = "Contrasts Nietzsche's subjective truth with classical objective moral models."
                    )
                )

                // Insert Atomic Notes (Zettelkasten Atomic concepts)
                val atomicId1 = repository.insertNote(
                    Note(
                        title = "Amor Fati: Love of Fate",
                        content = "A central stoic principle of active acceptance. Marcus urges us not just to tolerate our fate, but to love it and find purpose in whatever happens. The obstacle does not block our path; it becomes the path itself.",
                        summary = "Understanding Amor Fati as the active embrace of whatever fate brings, turning obstacles into fuel for character growth.",
                        createdAt = System.currentTimeMillis() - 86400000 * 3,
                        tags = listOf("stoicism", "amor_fati", "resilience"),
                        source = "Meditations - Marcus Aurelius, Book IV",
                        origin = "atomic"
                    )
                )

                val atomicId2 = repository.insertNote(
                    Note(
                        title = "The Inner Citadel",
                        content = "The mind's fortress that nothing external can penetrate or disturb unless we permit it. Marcus Aurelius emphasizes that while we cannot control external events, we retain absolute control over our own thoughts, judgments, and choices.",
                        summary = "Exploring the concept of the Inner Citadel—the unassailable mental sanctuary where we maintain control of our choices.",
                        createdAt = System.currentTimeMillis() - 86400000 * 3,
                        tags = listOf("stoicism", "inner_citadel", "control"),
                        source = "Meditations - Marcus Aurelius, Book VIII",
                        origin = "atomic"
                    )
                )

                val atomicId3 = repository.insertNote(
                    Note(
                        title = "The Dichotomy of Control",
                        content = "Dividing all matters into two groups: things in our control (our thoughts, reactions, values, and actions) and things outside our control (others' opinions, physical events, and outcomes). Focusing exclusively on what we can control reduces anxiety.",
                        summary = "Applying the Dichotomy of Control to eliminate external distress and redirect energy toward personal agency.",
                        createdAt = System.currentTimeMillis() - 86400000 * 3,
                        tags = listOf("stoicism", "dichotomy_of_control", "agency"),
                        source = "Meditations - Marcus Aurelius, Book XII",
                        origin = "atomic"
                    )
                )

                // Insert links connecting the atomic notes
                repository.insertLink(
                    Link(
                        noteIdA = atomicId1,
                        noteIdB = atomicId3,
                        relationship = "Obstacles and Control",
                        reasoning = "Loving one's fate (Amor Fati) is a direct consequence of understanding what lies within our control."
                    )
                )

                repository.insertLink(
                    Link(
                        noteIdA = atomicId2,
                        noteIdB = atomicId3,
                        relationship = "Fortress of Control",
                        reasoning = "The Inner Citadel is built upon the Dichotomy of Control; the fortress protects the self from uncontrollable external events."
                    )
                )

                repository.insertLink(
                    Link(
                        noteIdA = noteId1, // Marcus Aurelius on Morning Preparation
                        noteIdB = atomicId2, // Inner Citadel
                        relationship = "Citadel Preparation",
                        reasoning = "Morning visualization constructs the Inner Citadel to prepare for difficult interactions during the day."
                    )
                )

                repository.insertLink(
                    Link(
                        noteIdA = atomicId1, // Amor Fati
                        noteIdB = noteId4, // Comparative Ethics
                        relationship = "Subjective Acceptance",
                        reasoning = "Compares Nietzsche's concept of Amor Fati with Marcus Aurelius' Stoic duty in the broader ethics map."
                    )
                )

                // Initialize positions for these newly seeded nodes
                updateMapPositions()

            } catch (e: Exception) {
                Log.e("VaultViewModel", "Error seeding sample data", e)
            }
        }
    }
}
