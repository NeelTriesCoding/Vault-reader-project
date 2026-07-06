package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val summary: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val tags: List<String> = emptyList(),
    val source: String? = null, // e.g., "Philosophy Book, Page 42"
    val origin: String = "user", // "user", "ai", or "atomic"
    // Persisted Visual Map coordinates. Null until the node has been laid out
    // (see MapLayoutEngine); once positioned they survive app restarts.
    val posX: Float? = null,
    val posY: Float? = null
)

@Entity(tableName = "books")
data class Book(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val filePath: String,
    val lastPosition: Int = 0,
    val shelves: List<String> = emptyList(),
    // Cached page count from the first parse, used for reading-progress display.
    // 0 means "not yet known".
    val totalPages: Int = 0
)

@Entity(tableName = "links")
data class Link(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteIdA: Long,
    val noteIdB: Long,
    val relationship: String,
    val reasoning: String
)

@Entity(tableName = "highlights")
data class Highlight(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bookId: Long,
    val pageIndex: Int,
    val sentenceText: String,
    val colorHex: String, // e.g. "yellow", "green", "blue", "pink"
    val createdAt: Long = System.currentTimeMillis(),
    // Index of the sentence within the page. Preferred over matching on
    // sentenceText, which is fragile when a page has duplicate sentences.
    // -1 means "unknown" (legacy rows migrated from before this column).
    val sentenceIndex: Int = -1
)

class Converters {
    @TypeConverter
    fun fromString(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }

    @TypeConverter
    fun fromList(list: List<String>?): String {
        return list?.joinToString(",") ?: ""
    }
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Long): Note?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Query("UPDATE notes SET tags = :tags WHERE id = :id")
    suspend fun updateNoteTags(id: Long, tags: String)

    @Query("UPDATE notes SET posX = :posX, posY = :posY WHERE id = :id")
    suspend fun updateNotePosition(id: Long, posX: Float, posY: Float)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: Long)

    @Delete
    suspend fun deleteNote(note: Note)
}

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY id DESC")
    fun getAllBooks(): Flow<List<Book>>

    @Query("SELECT * FROM books WHERE id = :id LIMIT 1")
    suspend fun getBookById(id: Long): Book?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: Book): Long

    @Query("UPDATE books SET lastPosition = :position WHERE id = :id")
    suspend fun updateLastPosition(id: Long, position: Int)

    @Query("UPDATE books SET totalPages = :totalPages WHERE id = :id")
    suspend fun updateTotalPages(id: Long, totalPages: Int)

    @Query("UPDATE books SET shelves = :shelves WHERE id = :id")
    suspend fun updateShelves(id: Long, shelves: String)

    @Delete
    suspend fun deleteBook(book: Book)
}

@Dao
interface LinkDao {
    @Query("SELECT * FROM links ORDER BY id DESC")
    fun getAllLinks(): Flow<List<Link>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLink(link: Link): Long

    @Query("DELETE FROM links WHERE id = :id")
    suspend fun deleteLinkById(id: Long)

    @Delete
    suspend fun deleteLink(link: Link)
}

@Dao
interface HighlightDao {
    @Query("SELECT * FROM highlights ORDER BY id DESC")
    fun getAllHighlights(): Flow<List<Highlight>>

    @Query("SELECT * FROM highlights WHERE bookId = :bookId")
    fun getHighlightsForBook(bookId: Long): Flow<List<Highlight>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHighlight(highlight: Highlight): Long

    @Query("DELETE FROM highlights WHERE bookId = :bookId AND pageIndex = :pageIndex AND sentenceText = :sentenceText")
    suspend fun deleteSpecificHighlight(bookId: Long, pageIndex: Int, sentenceText: String)

    @Query("DELETE FROM highlights WHERE bookId = :bookId AND pageIndex = :pageIndex AND sentenceIndex = :sentenceIndex")
    suspend fun deleteHighlightAtIndex(bookId: Long, pageIndex: Int, sentenceIndex: Int)

    @Query("DELETE FROM highlights WHERE id = :id")
    suspend fun deleteHighlightById(id: Long)
}

@Database(entities = [Note::class, Book::class, Link::class, Highlight::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun bookDao(): BookDao
    abstract fun linkDao(): LinkDao
    abstract fun highlightDao(): HighlightDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Additive migration from v2 → v3: persisted map coordinates on notes,
         * cached page count on books, and a per-page sentence index on
         * highlights. All columns are additive so no data is lost.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN posX REAL")
                db.execSQL("ALTER TABLE notes ADD COLUMN posY REAL")
                db.execSQL("ALTER TABLE books ADD COLUMN totalPages INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE highlights ADD COLUMN sentenceIndex INTEGER NOT NULL DEFAULT -1")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vault_database"
                )
                    .addMigrations(MIGRATION_2_3)
                    // Only wipe as an absolute last resort for pre-v2 dev installs
                    // that predate proper migrations; real user data (v2+) is
                    // always migrated, never destroyed.
                    .fallbackToDestructiveMigrationFrom(1)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
