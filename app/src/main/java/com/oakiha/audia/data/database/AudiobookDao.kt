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
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBooks(books: List<BookEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuthors(authors: List<AuthorEntity>)

    @Transaction
    suspend fun insertAudiobookData(tracks: List<TrackEntity>, books: List<BookEntity>, authors: List<AuthorEntity>) {
        insertAuthors(authors)
        insertBooks(books)
        insertTracks(tracks)
    }

    // --- Clear Operations ---
    @Query("DELETE FROM tracks")
    suspend fun clearAllTracks()

    @Query("DELETE FROM books")
    suspend fun clearAllBooks()

    @Query("DELETE FROM authors")
    suspend fun clearAllAuthors()

    @Transaction
    suspend fun clearAllAudiobookData() {
        clearAllTracks()
        clearAllBooks()
        clearAllAuthors()
    }

    // --- Incremental Sync Operations ---
    @Query("SELECT id FROM tracks")
    suspend fun getAllTrackIds(): List<Long>

    @Query("DELETE FROM tracks WHERE id IN (:trackIds)")
    suspend fun deleteTracksByIds(trackIds: List<Long>)

    @Query("DELETE FROM track_author_cross_ref WHERE track_id IN (:trackIds)")
    suspend fun deleteCrossRefsByTrackIds(trackIds: List<Long>)

    /**
     * Incrementally sync music data: upsert new/modified songs and remove deleted ones.
     */
    @Transaction
    suspend fun incrementalSyncAudiobookData(
        tracks: List<TrackEntity>,
        books: List<BookEntity>,
        authors: List<AuthorEntity>,
        crossRefs: List<TrackAuthorCrossRef>,
        deletedTrackIds: List<Long>
    ) {
        if (deletedTrackIds.isNotEmpty()) {
            deletedTrackIds.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
                deleteCrossRefsByTrackIds(chunk)
                deleteTracksByIds(chunk)
            }
        }
        insertAuthors(authors)
        insertBooks(books)
        tracks.chunked(TRACK_BATCH_SIZE).forEach { chunk ->
            insertTracks(chunk)
        }
        val updatedTrackIds = tracks.map { it.id }
        updatedTrackIds.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
            deleteCrossRefsByTrackIds(chunk)
        }
        crossRefs.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
            insertTrackAuthorCrossRefs(chunk)
        }
        deleteOrphanedBooks()
        deleteOrphanedAuthors()
    }

    // --- Track Queries ---
    @Query("""
        SELECT * FROM tracks
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        ORDER BY title ASC
    """)
    fun getTracks(allowedParentDirs: List<String>, applyDirectoryFilter: Boolean): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :trackId")
    fun getTrackById(trackId: Long): Flow<TrackEntity?>

    @Query("SELECT * FROM tracks WHERE file_path = :path LIMIT 1")
    suspend fun getTrackByPath(path: String): TrackEntity?

    @Query("""
        SELECT * FROM tracks
        WHERE id IN (:trackIds)
        AND (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
    """)
    fun getTracksByIds(trackIds: List<Long>, allowedParentDirs: List<String>, applyDirectoryFilter: Boolean): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE book_id = :bookId ORDER BY title ASC")
    fun getTracksByBookId(bookId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE author_id = :authorId ORDER BY title ASC")
    fun getTracksByAuthorId(authorId: Long): Flow<List<TrackEntity>>

    @Query("""
        SELECT * FROM tracks
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        AND (title LIKE '%' || :query || '%' OR author_name LIKE '%' || :query || '%')
        ORDER BY title ASC
    """)
    fun searchTracks(query: String, allowedParentDirs: List<String>, applyDirectoryFilter: Boolean): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks")
    fun getTrackCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun getTrackCountOnce(): Int

    @Query("""
        SELECT * FROM tracks
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    suspend fun getRandomTracks(limit: Int, allowedParentDirs: List<String> = emptyList(), applyDirectoryFilter: Boolean = false): List<TrackEntity>

    @Query("""
        SELECT * FROM tracks
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
    """)
    fun getAllTracksFlow(allowedParentDirs: List<String> = emptyList(), applyDirectoryFilter: Boolean = false): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks")
    suspend fun getAllTracksOnce(): List<TrackEntity>

    @Query("""
        SELECT * FROM tracks
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        ORDER BY title ASC
    """)
    fun getTracksPaginated(allowedParentDirs: List<String>, applyDirectoryFilter: Boolean): PagingSource<Int, TrackEntity>

    // --- Book Queries ---
    @Query("""
        SELECT DISTINCT books.* FROM books
        INNER JOIN tracks ON books.id = tracks.book_id
        WHERE (:applyDirectoryFilter = 0 OR tracks.parent_directory_path IN (:allowedParentDirs))
        ORDER BY books.title ASC
    """)
    fun getBooks(allowedParentDirs: List<String>, applyDirectoryFilter: Boolean): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :bookId")
    fun getBookById(bookId: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE title LIKE '%' || :query || '%' ORDER BY title ASC")
    fun searchBooks(query: String): Flow<List<BookEntity>>

    @Query("SELECT COUNT(*) FROM books")
    fun getBookCount(): Flow<Int>

    @Query("""
        SELECT DISTINCT books.* FROM books
        INNER JOIN tracks ON books.id = tracks.book_id
        WHERE (:applyDirectoryFilter = 0 OR tracks.parent_directory_path IN (:allowedParentDirs))
        ORDER BY books.title ASC
    """)
    suspend fun getAllBooksOnce(allowedParentDirs: List<String>, applyDirectoryFilter: Boolean): List<BookEntity>

    @Query("SELECT * FROM books WHERE author_id = :authorId ORDER BY title ASC")
    fun getBooksByAuthorId(authorId: Long): Flow<List<BookEntity>>

    // --- Author Queries ---
    @Query("""
        SELECT DISTINCT authors.* FROM authors
        INNER JOIN tracks ON authors.id = tracks.author_id
        WHERE (:applyDirectoryFilter = 0 OR tracks.parent_directory_path IN (:allowedParentDirs))
        ORDER BY authors.name ASC
    """)
    fun getAuthors(allowedParentDirs: List<String>, applyDirectoryFilter: Boolean): Flow<List<AuthorEntity>>

    @Query("SELECT * FROM authors ORDER BY name ASC")
    fun getAllAuthorsRaw(): Flow<List<AuthorEntity>>

    @Query("SELECT * FROM authors WHERE id = :authorId")
    fun getAuthorById(authorId: Long): Flow<AuthorEntity?>

    @Query("SELECT * FROM authors WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchAuthors(query: String): Flow<List<AuthorEntity>>

    @Query("SELECT COUNT(*) FROM authors")
    fun getAuthorCount(): Flow<Int>

    @Query("""
        SELECT DISTINCT authors.* FROM authors
        INNER JOIN tracks ON authors.id = tracks.author_id
        WHERE (:applyDirectoryFilter = 0 OR tracks.parent_directory_path IN (:allowedParentDirs))
        ORDER BY authors.name ASC
    """)
    suspend fun getAllAuthorsOnce(allowedParentDirs: List<String>, applyDirectoryFilter: Boolean): List<AuthorEntity>

    @Query("SELECT * FROM authors ORDER BY name ASC")
    suspend fun getAllAuthorsRawOnce(): List<AuthorEntity>

    // --- Author Image Operations ---
    @Query("SELECT image_url FROM authors WHERE id = :authorId")
    suspend fun getAuthorImageUrl(authorId: Long): String?

    @Query("UPDATE authors SET image_url = :imageUrl WHERE id = :authorId")
    suspend fun updateAuthorImageUrl(authorId: Long, imageUrl: String)

    @Query("SELECT id FROM authors WHERE name = :name LIMIT 1")
    suspend fun getAuthorIdByName(name: String): Long?

    @Query("SELECT MAX(id) FROM authors")
    suspend fun getMaxAuthorId(): Long?

    // --- Genre Queries ---
    @Query("""
        SELECT * FROM tracks
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        AND genre LIKE :genreName
        ORDER BY title ASC
    """)
    fun getTracksByGenre(genreName: String, allowedParentDirs: List<String>, applyDirectoryFilter: Boolean): Flow<List<TrackEntity>>

    @Query("""
        SELECT * FROM tracks
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        AND (genre IS NULL OR genre = '')
        ORDER BY title ASC
    """)
    fun getTracksWithNullGenre(allowedParentDirs: List<String>, applyDirectoryFilter: Boolean): Flow<List<TrackEntity>>

    @Query("SELECT DISTINCT genre FROM tracks WHERE genre IS NOT NULL AND genre != '' ORDER BY genre ASC")
    fun getUniqueGenres(): Flow<List<String>>

    @Query("SELECT DISTINCT book_art_uri_string FROM tracks WHERE book_art_uri_string IS NOT NULL")
    fun getAllUniqueBookArtUris(): Flow<List<String>>

    @Query("DELETE FROM books WHERE id NOT IN (SELECT DISTINCT book_id FROM tracks)")
    suspend fun deleteOrphanedBooks()

    @Query("DELETE FROM authors WHERE id NOT IN (SELECT DISTINCT author_id FROM tracks)")
    suspend fun deleteOrphanedAuthors()

    // --- Favorite Operations ---
    @Query("UPDATE tracks SET is_favorite = :isFavorite WHERE id = :trackId")
    suspend fun setFavoriteStatus(trackId: Long, isFavorite: Boolean)

    @Query("SELECT is_favorite FROM tracks WHERE id = :trackId")
    suspend fun getFavoriteStatus(trackId: Long): Boolean?

    @Transaction
    suspend fun toggleFavoriteStatus(trackId: Long): Boolean {
        val currentStatus = getFavoriteStatus(trackId) ?: false
        val newStatus = !currentStatus
        setFavoriteStatus(trackId, newStatus)
        return newStatus
    }

    @Query("UPDATE tracks SET title = :title, author_name = :author, book_name = :book, genre = :genre, lyrics = :lyrics, track_number = :trackNumber WHERE id = :trackId")
    suspend fun updateTrackMetadata(trackId: Long, title: String, author: String, book: String, genre: String?, lyrics: String?, trackNumber: Int)

    @Query("UPDATE tracks SET book_art_uri_string = :bookArtUri WHERE id = :trackId")
    suspend fun updateTrackBookArt(trackId: Long, bookArtUri: String?)

    @Query("UPDATE tracks SET lyrics = :lyrics WHERE id = :trackId")
    suspend fun updateLyrics(trackId: Long, lyrics: String)

    @Query("UPDATE tracks SET lyrics = NULL WHERE id = :trackId")
    suspend fun resetLyrics(trackId: Long)

    @Query("UPDATE tracks SET lyrics = NULL")
    suspend fun resetAllLyrics()

    @Query("SELECT book_art_uri_string FROM tracks WHERE id=:id")
    suspend fun getBookArtUriById(id: Long) : String?

    @Query("DELETE FROM tracks WHERE id=:id")
    suspend fun deleteById(id: Long)

    @Query("""
    SELECT mime_type AS mimeType,
           bitrate,
           sample_rate AS sampleRate
    FROM tracks
    WHERE id = :id
    """)
    suspend fun getAudioMetadataById(id: Long): AudioMeta?

    // ===== Track-Author Cross Reference (Junction Table) Operations =====
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrackAuthorCrossRefs(crossRefs: List<TrackAuthorCrossRef>)

    @Query("SELECT * FROM track_author_cross_ref")
    fun getAllTrackAuthorCrossRefs(): Flow<List<TrackAuthorCrossRef>>

    @Query("SELECT * FROM track_author_cross_ref")
    suspend fun getAllTrackAuthorCrossRefsOnce(): List<TrackAuthorCrossRef>

    @Query("DELETE FROM track_author_cross_ref")
    suspend fun clearAllTrackAuthorCrossRefs()

    @Query("DELETE FROM track_author_cross_ref WHERE track_id = :trackId")
    suspend fun deleteCrossRefsForTrack(trackId: Long)

    @Query("DELETE FROM track_author_cross_ref WHERE author_id = :authorId")
    suspend fun deleteCrossRefsForAuthor(authorId: Long)

    @Query("""
        SELECT authors.* FROM authors
        INNER JOIN track_author_cross_ref ON authors.id = track_author_cross_ref.author_id
        WHERE track_author_cross_ref.track_id = :trackId
        ORDER BY track_author_cross_ref.is_primary DESC, authors.name ASC
    """)
    fun getAuthorsForTrack(trackId: Long): Flow<List<AuthorEntity>>

    @Query("""
        SELECT authors.* FROM authors
        INNER JOIN track_author_cross_ref ON authors.id = track_author_cross_ref.author_id
        WHERE track_author_cross_ref.track_id = :trackId
        ORDER BY track_author_cross_ref.is_primary DESC, authors.name ASC
    """)
    suspend fun getAuthorsForTrackOnce(trackId: Long): List<AuthorEntity>

    @Query("""
        SELECT tracks.* FROM tracks
        INNER JOIN track_author_cross_ref ON tracks.id = track_author_cross_ref.track_id
        WHERE track_author_cross_ref.author_id = :authorId
        ORDER BY tracks.title ASC
    """)
    fun getTracksForAuthor(authorId: Long): Flow<List<TrackEntity>>

    @Query("""
        SELECT tracks.* FROM tracks
        INNER JOIN track_author_cross_ref ON tracks.id = track_author_cross_ref.track_id
        WHERE track_author_cross_ref.author_id = :authorId
        ORDER BY tracks.title ASC
    """)
    suspend fun getTracksForAuthorOnce(authorId: Long): List<TrackEntity>

    @Query("SELECT * FROM track_author_cross_ref WHERE track_id = :trackId")
    suspend fun getCrossRefsForTrack(trackId: Long): List<TrackAuthorCrossRef>

    @Query("""
        SELECT authors.id AS author_id, authors.name FROM authors
        INNER JOIN track_author_cross_ref ON authors.id = track_author_cross_ref.author_id
        WHERE track_author_cross_ref.track_id = :trackId AND track_author_cross_ref.is_primary = 1
        LIMIT 1
    """)
    suspend fun getPrimaryAuthorForTrack(trackId: Long): PrimaryArtistInfo?

    @Query("SELECT COUNT(*) FROM track_author_cross_ref WHERE author_id = :authorId")
    suspend fun getTrackCountForAuthor(authorId: Long): Int

    @Query("""
        SELECT authors.id, authors.name, authors.image_url,
               (SELECT COUNT(*) FROM track_author_cross_ref WHERE track_author_cross_ref.author_id = authors.id) AS track_count
        FROM authors
        ORDER BY authors.name ASC
    """)
    fun getAuthorsWithTrackCounts(): Flow<List<AuthorEntity>>

    @Query("""
        SELECT DISTINCT authors.id, authors.name, authors.image_url,
               (SELECT COUNT(*) FROM track_author_cross_ref
                INNER JOIN tracks ON track_author_cross_ref.track_id = tracks.id
                WHERE track_author_cross_ref.author_id = authors.id
                AND (:applyDirectoryFilter = 0 OR tracks.parent_directory_path IN (:allowedParentDirs))) AS track_count
        FROM authors
        INNER JOIN track_author_cross_ref ON authors.id = track_author_cross_ref.author_id
        INNER JOIN tracks ON track_author_cross_ref.track_id = tracks.id
        WHERE (:applyDirectoryFilter = 0 OR tracks.parent_directory_path IN (:allowedParentDirs))
        ORDER BY authors.name ASC
    """)
    fun getAuthorsWithTrackCountsFiltered(allowedParentDirs: List<String>, applyDirectoryFilter: Boolean): Flow<List<AuthorEntity>>

    @Transaction
    suspend fun clearAllAudiobookDataWithCrossRefs() {
        clearAllTrackAuthorCrossRefs()
        clearAllTracks()
        clearAllBooks()
        clearAllAuthors()
    }

    @Transaction
    suspend fun insertAudiobookDataWithCrossRefs(tracks: List<TrackEntity>, books: List<BookEntity>, authors: List<AuthorEntity>, crossRefs: List<TrackAuthorCrossRef>) {
        insertAuthors(authors)
        insertBooks(books)
        insertTracks(tracks)
        crossRefs.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
            insertTrackAuthorCrossRefs(chunk)
        }
    }

    companion object {
        private const val SQLITE_MAX_VARIABLE_NUMBER = 999 
        private const val CROSS_REF_FIELDS_PER_OBJECT = 3
        val CROSS_REF_BATCH_SIZE: Int = SQLITE_MAX_VARIABLE_NUMBER / CROSS_REF_FIELDS_PER_OBJECT
        const val TRACK_BATCH_SIZE = 500
    }
}
