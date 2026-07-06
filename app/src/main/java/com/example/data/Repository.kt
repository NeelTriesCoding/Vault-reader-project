package com.example.data

import kotlinx.coroutines.flow.Flow

class VaultRepository(private val db: AppDatabase) {
    private val noteDao = db.noteDao()
    private val bookDao = db.bookDao()
    private val linkDao = db.linkDao()
    private val highlightDao = db.highlightDao()

    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()
    val allBooks: Flow<List<Book>> = bookDao.getAllBooks()
    val allLinks: Flow<List<Link>> = linkDao.getAllLinks()
    val allHighlights: Flow<List<Highlight>> = highlightDao.getAllHighlights()

    suspend fun getNoteById(id: Long): Note? {
        return noteDao.getNoteById(id)
    }

    suspend fun insertNote(note: Note): Long {
        return noteDao.insertNote(note)
    }

    suspend fun updateNoteTags(id: Long, tags: List<String>) {
        val converters = Converters()
        noteDao.updateNoteTags(id, converters.fromList(tags))
    }

    suspend fun deleteNoteById(id: Long) {
        noteDao.deleteNoteById(id)
    }

    suspend fun deleteNote(note: Note) {
        noteDao.deleteNote(note)
    }

    suspend fun getBookById(id: Long): Book? {
        return bookDao.getBookById(id)
    }

    suspend fun insertBook(book: Book): Long {
        return bookDao.insertBook(book)
    }

    suspend fun updateLastPosition(id: Long, position: Int) {
        bookDao.updateLastPosition(id, position)
    }

    suspend fun updateShelves(id: Long, shelves: List<String>) {
        val converters = Converters()
        bookDao.updateShelves(id, converters.fromList(shelves))
    }

    suspend fun deleteBook(book: Book) {
        bookDao.deleteBook(book)
    }

    suspend fun insertLink(link: Link): Long {
        return linkDao.insertLink(link)
    }

    suspend fun deleteLinkById(id: Long) {
        linkDao.deleteLinkById(id)
    }

    suspend fun deleteLink(link: Link) {
        linkDao.deleteLink(link)
    }

    fun getHighlightsForBook(bookId: Long): Flow<List<Highlight>> {
        return highlightDao.getHighlightsForBook(bookId)
    }

    suspend fun insertHighlight(highlight: Highlight): Long {
        return highlightDao.insertHighlight(highlight)
    }

    suspend fun deleteSpecificHighlight(bookId: Long, pageIndex: Int, sentenceText: String) {
        highlightDao.deleteSpecificHighlight(bookId, pageIndex, sentenceText)
    }

    suspend fun deleteHighlightById(id: Long) {
        highlightDao.deleteHighlightById(id)
    }
}
