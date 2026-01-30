package com.oakiha.audia.data.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SimpleSQLiteQuery
import com.oakiha.audia.utils.AudioMeta
import kotlinx.coroutines.flow.Flow

@Dao
interface AudiobookDao {

    // --- Insert Operations ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(Tracks: List<TrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(Books: List<BookEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuthors(Authors: List<AuthorEntity>)

    @Transaction
    suspend fun insertAudiobookData(Tracks: List<TrackEntity>, Books: List<BookEntity>, Authors: List<AuthorEntity>) {
        insertAuthors(Authors)
        insertBooks(Books)
        insertTracks(Tracks)
    }

    @Transaction
    suspend fun clearAllAudiobookData() {
        clearAllTracks()
        clearAllBooks()
        clearAllAuthors()
    }

    // --- Clear Operations ---
    @Query("DELETE FROM Tracks")
    suspend fun clearAllTracks()

    @Query("DELETE FROM Books")
    suspend fun clearAllBooks()

    @Query("DELETE FROM Authors")
    suspend fun clearAllAuthors()

    // --- Incremental Sync Operations ---
    @Query("SELECT id FROM Tracks")
    suspend fun getAllTrackIds(): List<Long>

    @Query("DELETE FROM Tracks WHERE id IN (:TrackIds)")
    suspend fun deleteTracksByIds(TrackIds: List<Long>)

    @Query("DELETE FROM Track_Author_cross_ref WHERE Track_id IN (:TrackIds)")
    suspend fun deleteCrossRefsByTrackIds(TrackIds: List<Long>)

    /**
     * Incrementally sync Audiobook data: upsert new/modified Tracks and remove deleted ones.
     * More efficient than clear-and-replace for large libraries with few changes.
     */
    @Transaction
    suspend fun incrementalSyncAudiobookData(
        Tracks: List<TrackEntity>,
        Books: List<BookEntity>,
        Authors: List<AuthorEntity>,
        crossRefs: List<TrackAuthorCrossRef>,
        deletedTrackIds: List<Long>
    ) {
        // Delete removed Tracks and their cross-refs
        if (deletedTrackIds.isNotEmpty()) {
            deletedTrackIds.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
                deleteCrossRefsByTrackIds(chunk)
                deleteTracksByIds(chunk)
            }
        }
        
        // Upsert Authors, Books, and Tracks (REPLACE strategy handles updates)
        insertAuthors(Authors)
        insertBooks(Books)
        
        // Insert Tracks in chunks to allow concurrent reads
        Tracks.chunked(Track_BATCH_SIZE).forEach { chunk ->
            insertTracks(chunk)
        }
        
        // Delete old cross-refs for updated Tracks and insert new ones
        val updatedTrackIds = Tracks.map { it.id }
        updatedTrackIds.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
            deleteCrossRefsByTrackIds(chunk)
        }
        crossRefs.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
            insertTrackAuthorCrossRefs(chunk)
        }
        
        // Clean up orphaned Books and Authors
        deleteOrphanedBooks()
        deleteOrphanedAuthors()
    }

    // --- Track Queries ---
    // Updated getTracks to potentially filter by parent_directory_path
    @Query("""
        SELECT * FROM Tracks
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        ORDER BY title ASC
    """)
    fun getTracks(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<TrackEntity>>

    @Query("SELECT * FROM Tracks WHERE id = :TrackId")
    fun getTrackById(TrackId: Long): Flow<TrackEntity?>

    @Query("SELECT * FROM Tracks WHERE file_path = :path LIMIT 1")
    suspend fun getTrackByPath(path: String): TrackEntity?

    //@Query("SELECT * FROM Tracks WHERE id IN (:TrackIds)")
    @Query("""
        SELECT * FROM Tracks
        WHERE id IN (:TrackIds)
        AND (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
    """)
    fun getTracksByIds(
        TrackIds: List<Long>,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<TrackEntity>>

    @Query("SELECT * FROM Tracks WHERE Book_id = :BookId ORDER BY title ASC")
    fun getTracksByBookId(BookId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM Tracks WHERE Author_id = :AuthorId ORDER BY title ASC")
    fun getTracksByAuthorId(AuthorId: Long): Flow<List<TrackEntity>>

    @Query("""
        SELECT * FROM Tracks
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        AND (title LIKE '%' || :query || '%' OR Author_name LIKE '%' || :query || '%')
        ORDER BY title ASC
    """)
    fun searchTracks(
        query: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM Tracks")
    fun getTrackCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM Tracks")
    suspend fun getTrackCountOnce(): Int

    /**
     * Returns random Tracks for efficient shuffle without loading all Tracks into memory.
     * Uses SQLite RANDOM() for true randomness.
     */
    @Query("""
        SELECT * FROM Tracks
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    suspend fun getRandomTracks(
        limit: Int,
        allowedParentDirs: List<String> = emptyList(),
        applyDirectoryFilter: Boolean = false
    ): List<TrackEntity>

    @Query("""
        SELECT * FROM Tracks
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
    """)
    fun getAllTracks(
        allowedParentDirs: List<String> = emptyList(),
        applyDirectoryFilter: Boolean = false
    ): Flow<List<TrackEntity>>
    
    // --- Paginated Queries for Large Libraries ---
    /**
     * Returns a PagingSource for Tracks, enabling efficient pagination for large libraries.
     * Room auto-generates the PagingSource implementation.
     */
    @Query("""
        SELECT * FROM Tracks
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        ORDER BY title ASC
    """)
    fun getTracksPaginated(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): PagingSource<Int, TrackEntity>

    // --- Book Queries ---
    @Query("""
        SELECT DISTINCT Books.* FROM Books
        INNER JOIN Tracks ON Books.id = Tracks.Book_id
        WHERE (:applyDirectoryFilter = 0 OR Tracks.parent_directory_path IN (:allowedParentDirs))
        ORDER BY Books.title ASC
    """)
    fun getBooks(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<BookEntity>>

    @Query("SELECT * FROM Books WHERE id = :BookId")
    fun getBookById(BookId: Long): Flow<BookEntity?>

    @Query("SELECT * FROM Books WHERE title LIKE '%' || :query || '%' ORDER BY title ASC")
    fun searchBooks(query: String): Flow<List<BookEntity>>

    @Query("SELECT COUNT(*) FROM Books")
    fun getBookCount(): Flow<Int>

    // Version of getBooks that returns a List for one-shot reads
    @Query("""
        SELECT DISTINCT Books.* FROM Books
        INNER JOIN Tracks ON Books.id = Tracks.Book_id
        WHERE (:applyDirectoryFilter = 0 OR Tracks.parent_directory_path IN (:allowedParentDirs))
        ORDER BY Books.title ASC
    """)
    suspend fun getAllBooksList(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): List<BookEntity>

    @Query("SELECT * FROM Books WHERE Author_id = :AuthorId ORDER BY title ASC")
    fun getBooksByAuthorId(AuthorId: Long): Flow<List<BookEntity>>

    @Query("""
        SELECT DISTINCT Books.* FROM Books
        INNER JOIN Tracks ON Books.id = Tracks.Book_id
        WHERE (:applyDirectoryFilter = 0 OR Tracks.parent_directory_path IN (:allowedParentDirs))
        AND (Books.title LIKE '%' || :query || '%' OR Books.Author_name LIKE '%' || :query || '%')
        ORDER BY Books.title ASC
    """)
    fun searchBooks(
        query: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<BookEntity>>

    // --- Author Queries ---
    @Query("""
        SELECT DISTINCT Authors.* FROM Authors
        INNER JOIN Tracks ON Authors.id = Tracks.Author_id
        WHERE (:applyDirectoryFilter = 0 OR Tracks.parent_directory_path IN (:allowedParentDirs))
        ORDER BY Authors.name ASC
    """)
    fun getAuthors(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<AuthorEntity>>

    /**
     * Unfiltered list of all Authors (including those only reachable via cross-refs).
     */
    @Query("SELECT * FROM Authors ORDER BY name ASC")
    fun getAllAuthorsRaw(): Flow<List<AuthorEntity>>

    @Query("SELECT * FROM Authors WHERE id = :AuthorId")
    fun getAuthorById(AuthorId: Long): Flow<AuthorEntity?>

    @Query("SELECT * FROM Authors WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchAuthors(query: String): Flow<List<AuthorEntity>>

    @Query("SELECT COUNT(*) FROM Authors")
    fun getAuthorCount(): Flow<Int>

    // Version of getAuthors that returns a List for one-shot reads
    @Query("""
        SELECT DISTINCT Authors.* FROM Authors
        INNER JOIN Tracks ON Authors.id = Tracks.Author_id
        WHERE (:applyDirectoryFilter = 0 OR Tracks.parent_directory_path IN (:allowedParentDirs))
        ORDER BY Authors.name ASC
    """)
    suspend fun getAllAuthorsList(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): List<AuthorEntity>

    /**
     * Unfiltered list of all Authors (one-shot).
     */
    @Query("SELECT * FROM Authors ORDER BY name ASC")
    suspend fun getAllAuthorsListRaw(): List<AuthorEntity>

    @Query("""
        SELECT DISTINCT Authors.* FROM Authors
        INNER JOIN Tracks ON Authors.id = Tracks.Author_id
        WHERE (:applyDirectoryFilter = 0 OR Tracks.parent_directory_path IN (:allowedParentDirs))
        AND Authors.name LIKE '%' || :query || '%'
        ORDER BY Authors.name ASC
    """)
    fun searchAuthors(
        query: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<AuthorEntity>>

    // --- Author Image Operations ---
    @Query("SELECT image_url FROM Authors WHERE id = :AuthorId")
    suspend fun getAuthorImageUrl(AuthorId: Long): String?

    @Query("UPDATE Authors SET image_url = :imageUrl WHERE id = :AuthorId")
    suspend fun updateAuthorImageUrl(AuthorId: Long, imageUrl: String)

    @Query("SELECT id FROM Authors WHERE name = :name LIMIT 1")
    suspend fun getAuthorIdByName(name: String): Long?

    @Query("SELECT MAX(id) FROM Authors")
    suspend fun getMaxAuthorId(): Long?

    // --- Category Queries ---
    // Example: Get all Tracks for a specific Category
    @Query("""
        SELECT * FROM Tracks
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        AND Category LIKE :CategoryName
        ORDER BY title ASC
    """)
    fun getTracksByCategory(
        CategoryName: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<TrackEntity>>

    @Query("""
        SELECT * FROM Tracks
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        AND (Category IS NULL OR Category = '')
        ORDER BY title ASC
    """)
    fun getTracksWithNullCategory(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<TrackEntity>>

    // Example: Get all unique Category names
    @Query("SELECT DISTINCT Category FROM Tracks WHERE Category IS NOT NULL AND Category != '' ORDER BY Category ASC")
    fun getUniqueCategories(): Flow<List<String>>

    // --- Combined Queries (Potentially useful for more complex scenarios) ---
    // E.g., Get all Book art URIs from Tracks (could be useful for theme preloading from SSoT)
    @Query("SELECT DISTINCT Book_art_uri_string FROM Tracks WHERE Book_art_uri_string IS NOT NULL")
    fun getAllUniqueBookArtUrisFromTracks(): Flow<List<String>>

    @Query("DELETE FROM Books WHERE id NOT IN (SELECT DISTINCT Book_id FROM Tracks)")
    suspend fun deleteOrphanedBooks()

    @Query("DELETE FROM Authors WHERE id NOT IN (SELECT DISTINCT Author_id FROM Tracks)")
    suspend fun deleteOrphanedAuthors()

    // --- Favorite Operations ---
    @Query("UPDATE Tracks SET is_favorite = :isFavorite WHERE id = :TrackId")
    suspend fun setFavoriteStatus(TrackId: Long, isFavorite: Boolean)

    @Query("SELECT is_favorite FROM Tracks WHERE id = :TrackId")
    suspend fun getFavoriteStatus(TrackId: Long): Boolean?

    // Transaction to toggle favorite status
    @Transaction
    suspend fun toggleFavoriteStatus(TrackId: Long): Boolean {
        val currentStatus = getFavoriteStatus(TrackId) ?: false // Default to false if not found (should not happen for existing Track)
        val newStatus = !currentStatus
        setFavoriteStatus(TrackId, newStatus)
        return newStatus
    }

    @Query("UPDATE Tracks SET title = :title, Author_name = :Author, Book_name = :Book, Category = :Category, Transcript = :Transcript, track_number = :trackNumber WHERE id = :TrackId")
    suspend fun updateTrackMetadata(
        TrackId: Long,
        title: String,
        Author: String,
        Book: String,
        Category: String?,
        Transcript: String?,
        trackNumber: Int
    )

    @Query("UPDATE Tracks SET Book_art_uri_string = :BookArtUri WHERE id = :TrackId")
    suspend fun updateTrackBookArt(TrackId: Long, BookArtUri: String?)

    @Query("UPDATE Tracks SET Transcript = :Transcript WHERE id = :TrackId")
    suspend fun updateTranscript(TrackId: Long, Transcript: String)

    @Query("UPDATE Tracks SET Transcript = NULL WHERE id = :TrackId")
    suspend fun resetTranscript(TrackId: Long)

    @Query("UPDATE Tracks SET Transcript = NULL")
    suspend fun resetAllTranscript()

    @Query("SELECT * FROM Tracks")
    suspend fun getAllTracksList(): List<TrackEntity>

    @Query("SELECT Book_art_uri_string FROM Tracks WHERE id=:id")
    suspend fun getBookArtUriById(id: Long) : String?

    @Query("DELETE FROM Tracks WHERE id=:id")
    suspend fun deleteById(id: Long)

    @Query("""
    SELECT mime_type AS mimeType,
           bitrate,
           sample_rate AS sampleRate
    FROM Tracks
    WHERE id = :id
    """)
    suspend fun getAudioMetadataById(id: Long): AudioMeta?

    // ===== Track-Author Cross Reference (Junction Table) Operations =====

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrackAuthorCrossRefs(crossRefs: List<TrackAuthorCrossRef>)

    @Query("SELECT * FROM Track_Author_cross_ref")
    fun getAllTrackAuthorCrossRefs(): Flow<List<TrackAuthorCrossRef>>

    @Query("SELECT * FROM Track_Author_cross_ref")
    suspend fun getAllTrackAuthorCrossRefsList(): List<TrackAuthorCrossRef>

    @Query("DELETE FROM Track_Author_cross_ref")
    suspend fun clearAllTrackAuthorCrossRefs()

    @Query("DELETE FROM Track_Author_cross_ref WHERE Track_id = :TrackId")
    suspend fun deleteCrossRefsForTrack(TrackId: Long)

    @Query("DELETE FROM Track_Author_cross_ref WHERE Author_id = :AuthorId")
    suspend fun deleteCrossRefsForAuthor(AuthorId: Long)

    /**
     * Get all Authors for a specific Track using the junction table.
     */
    @Query("""
        SELECT Authors.* FROM Authors
        INNER JOIN Track_Author_cross_ref ON Authors.id = Track_Author_cross_ref.Author_id
        WHERE Track_Author_cross_ref.Track_id = :TrackId
        ORDER BY Track_Author_cross_ref.is_primary DESC, Authors.name ASC
    """)
    fun getAuthorsForTrack(TrackId: Long): Flow<List<AuthorEntity>>

    /**
     * Get all Authors for a specific Track (one-shot).
     */
    @Query("""
        SELECT Authors.* FROM Authors
        INNER JOIN Track_Author_cross_ref ON Authors.id = Track_Author_cross_ref.Author_id
        WHERE Track_Author_cross_ref.Track_id = :TrackId
        ORDER BY Track_Author_cross_ref.is_primary DESC, Authors.name ASC
    """)
    suspend fun getAuthorsForTrackList(TrackId: Long): List<AuthorEntity>

    /**
     * Get all Tracks for a specific Author using the junction table.
     */
    @Query("""
        Select books.* FROM Tracks
        INNER JOIN Track_Author_cross_ref ON Tracks.id = Track_Author_cross_ref.Track_id
        WHERE Track_Author_cross_ref.Author_id = :AuthorId
        ORDER BY Tracks.title ASC
    """)
    fun getTracksForAuthor(AuthorId: Long): Flow<List<TrackEntity>>

    /**
     * Get all Tracks for a specific Author (one-shot).
     */
    @Query("""
        Select books.* FROM Tracks
        INNER JOIN Track_Author_cross_ref ON Tracks.id = Track_Author_cross_ref.Track_id
        WHERE Track_Author_cross_ref.Author_id = :AuthorId
        ORDER BY Tracks.title ASC
    """)
    suspend fun getTracksForAuthorList(AuthorId: Long): List<TrackEntity>

    /**
     * Get the cross-references for a specific Track.
     */
    @Query("SELECT * FROM Track_Author_cross_ref WHERE Track_id = :TrackId")
    suspend fun getCrossRefsForTrack(TrackId: Long): List<TrackAuthorCrossRef>

    /**
     * Get the primary Author for a Track.
     */
    @Query("""
        SELECT Authors.id AS Author_id, Authors.name FROM Authors
        INNER JOIN Track_Author_cross_ref ON Authors.id = Track_Author_cross_ref.Author_id
        WHERE Track_Author_cross_ref.Track_id = :TrackId AND Track_Author_cross_ref.is_primary = 1
        LIMIT 1
    """)
    suspend fun getPrimaryAuthorForTrack(TrackId: Long): PrimaryAuthorInfo?

    /**
     * Get Track count for an Author from the junction table.
     */
    @Query("SELECT COUNT(*) FROM Track_Author_cross_ref WHERE Author_id = :AuthorId")
    suspend fun getTrackCountForAuthor(AuthorId: Long): Int

    /**
     * Get all Authors with their Track counts computed from the junction table.
     */
    @Query("""
        SELECT Authors.id, Authors.name, Authors.image_url,
               (SELECT COUNT(*) FROM Track_Author_cross_ref WHERE Track_Author_cross_ref.Author_id = Authors.id) AS track_count
        FROM Authors
        ORDER BY Authors.name ASC
    """)
    fun getAuthorsWithTrackCounts(): Flow<List<AuthorEntity>>

    /**
     * Get all Authors with Track counts, filtered by allowed directories.
     */
    @Query("""
        SELECT DISTINCT Authors.id, Authors.name, Authors.image_url,
               (SELECT COUNT(*) FROM Track_Author_cross_ref 
                INNER JOIN Tracks ON Track_Author_cross_ref.Track_id = Tracks.id
                WHERE Track_Author_cross_ref.Author_id = Authors.id
                AND (:applyDirectoryFilter = 0 OR Tracks.parent_directory_path IN (:allowedParentDirs))) AS track_count
        FROM Authors
        INNER JOIN Track_Author_cross_ref ON Authors.id = Track_Author_cross_ref.Author_id
        INNER JOIN Tracks ON Track_Author_cross_ref.Track_id = Tracks.id
        WHERE (:applyDirectoryFilter = 0 OR Tracks.parent_directory_path IN (:allowedParentDirs))
        ORDER BY Authors.name ASC
    """)
    fun getAuthorsWithTrackCountsFiltered(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<AuthorEntity>>

    /**
     * Clear all Audiobook data including cross-references.
     */
    @Transaction
    suspend fun clearAllAudiobookDataWithCrossRefs() {
        clearAllTrackAuthorCrossRefs()
        clearAllTracks()
        clearAllBooks()
        clearAllAuthors()
    }

    /**
     * Insert Audiobook data with cross-references in a single transaction.
     * Uses chunked inserts for cross-refs to avoid SQLite variable limits.
     */
    @Transaction
    suspend fun insertAudiobookDataWithCrossRefs(
        Tracks: List<TrackEntity>,
        Books: List<BookEntity>,
        Authors: List<AuthorEntity>,
        crossRefs: List<TrackAuthorCrossRef>
    ) {
        insertAuthors(Authors)
        insertBooks(Books)
        insertTracks(Tracks)
        // Insert cross-refs in chunks to avoid SQLite variable limit.
        // Each TrackAuthorCrossRef has 3 fields, so batch size is calculated accordingly.
        crossRefs.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
            insertTrackAuthorCrossRefs(chunk)
        }
    }

    companion object {
        /**
         * SQLite has a limit on the number of variables per statement (default 999, higher in newer versions).
         * Each TrackAuthorCrossRef insert uses 3 variables (TrackId, AuthorId, isPrimary).
         * The batch size is calculated so that batchSize * 3 <= SQLITE_MAX_VARIABLE_NUMBER.
         */
        private const val SQLITE_MAX_VARIABLE_NUMBER = 999 // Increase if you know your SQLite version supports more
        private const val CROSS_REF_FIELDS_PER_OBJECT = 3
        val CROSS_REF_BATCH_SIZE: Int = SQLITE_MAX_VARIABLE_NUMBER / CROSS_REF_FIELDS_PER_OBJECT
        
        /**
         * Batch size for Track inserts during incremental sync.
         * Allows database reads to interleave with writes for better UX.
         */
        const val Track_BATCH_SIZE = 500
    }
}
