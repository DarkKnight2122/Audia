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
    suspend fun insertSongs(songs: List<TrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbums(albums: List<BookEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtists(artists: List<AuthorEntity>)

    @Transaction
    suspend fun insertMusicData(songs: List<TrackEntity>, albums: List<BookEntity>, artists: List<AuthorEntity>) {
        insertArtists(artists)
        insertAlbums(albums)
        insertSongs(songs)
    }

    @Transaction
    suspend fun clearAllMusicData() {
        clearAllTracks()
        clearAllAlbums()
        clearAllArtists()
    }

    // --- Clear Operations ---
    @Query("DELETE FROM tracks")
    suspend fun clearAllTracks()

    @Query("DELETE FROM books")
    suspend fun clearAllAlbums()

    @Query("DELETE FROM authors")
    suspend fun clearAllArtists()

    // --- Incremental Sync Operations ---
    @Query("SELECT id FROM tracks")
    suspend fun getAllSongIds(): List<Long>

    @Query("DELETE FROM tracks WHERE id IN (:trackIds)")
    suspend fun deleteSongsByIds(trackIds: List<Long>)

    @Query("DELETE FROM track_author_cross_ref WHERE track_id IN (:trackIds)")
    suspend fun deleteCrossRefsBySongIds(trackIds: List<Long>)

    /**
     * Incrementally sync music data: upsert new/modified songs and remove deleted ones.
     * More efficient than clear-and-replace for large libraries with few changes.
     */
    @Transaction
    suspend fun incrementalSyncMusicData(
        songs: List<TrackEntity>,
        albums: List<BookEntity>,
        artists: List<AuthorEntity>,
        crossRefs: List<TrackAuthorCrossRef>,
        deletedSongIds: List<Long>
    ) {
        // Delete removed songs and their cross-refs
        if (deletedSongIds.isNotEmpty()) {
            deletedSongIds.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
                deleteCrossRefsBySongIds(chunk)
                deleteSongsByIds(chunk)
            }
        }

        // Upsert artists, albums, and songs (REPLACE strategy handles updates)
        insertArtists(artists)
        insertAlbums(albums)

        // Insert songs in chunks to allow concurrent reads
        songs.chunked(SONG_BATCH_SIZE).forEach { chunk ->
            insertSongs(chunk)
        }

        // Delete old cross-refs for updated songs and insert new ones
        val updatedSongIds = songs.map { it.id }
        updatedSongIds.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
            deleteCrossRefsBySongIds(chunk)
        }
        crossRefs.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
            insertTrackAuthorCrossRefs(chunk)
        }

        // Clean up orphaned albums and artists
        deleteOrphanedAlbums()
        deleteOrphanedArtists()
    }

    // --- Song Queries ---
    @Query("""
        SELECT * FROM tracks
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        ORDER BY title ASC
    """)
    fun getTracks(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :trackId")
    fun getTrackById(trackId: Long): Flow<TrackEntity?>

    @Query("SELECT * FROM tracks WHERE file_path = :path LIMIT 1")
    suspend fun getTrackByPath(path: String): TrackEntity?

    @Query("""
        SELECT * FROM tracks
        WHERE id IN (:trackIds)
        AND (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
    """)
    fun getTracksByIds(
        trackIds: List<Long>,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE book_id = :bookId ORDER BY title ASC")
    fun getTracksByAlbumId(bookId: Long): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE author_id = :authorId ORDER BY title ASC")
    fun getTracksByArtistId(authorId: Long): Flow<List<TrackEntity>>

    @Query("""
        SELECT * FROM tracks
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        AND (title LIKE '%' || :query || '%' OR author_name LIKE '%' || :query || '%')
        ORDER BY title ASC
    """)
    fun searchTracks(
        query: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<TrackEntity>>

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
    suspend fun getRandomSongs(
        limit: Int,
        allowedParentDirs: List<String> = emptyList(),
        applyDirectoryFilter: Boolean = false
    ): List<TrackEntity>

    @Query("""
        SELECT * FROM tracks
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
    """)
    fun getAllTracks(
        allowedParentDirs: List<String> = emptyList(),
        applyDirectoryFilter: Boolean = false
    ): Flow<List<TrackEntity>>

    @Query("""
        SELECT * FROM tracks
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        ORDER BY title ASC
    """)
    fun getTracksPaginated(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): PagingSource<Int, TrackEntity>

    // --- Album Queries ---
    @Query("""
        SELECT DISTINCT books.* FROM books
        INNER JOIN tracks ON books.id = tracks.book_id
        WHERE (:applyDirectoryFilter = 0 OR tracks.parent_directory_path IN (:allowedParentDirs))
        ORDER BY books.title ASC
    """)
    fun getAlbums(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :bookId")
    fun getAlbumById(bookId: Long): Flow<BookEntity?>

    @Query("SELECT * FROM books WHERE title LIKE '%' || :query || '%' ORDER BY title ASC")
    fun searchAlbums(query: String): Flow<List<BookEntity>>

    @Query("SELECT COUNT(*) FROM books")
    fun getAlbumCount(): Flow<Int>

    @Query("""
        SELECT DISTINCT books.* FROM books
        INNER JOIN tracks ON books.id = tracks.book_id
        WHERE (:applyDirectoryFilter = 0 OR tracks.parent_directory_path IN (:allowedParentDirs))
        ORDER BY books.title ASC
    """)
    suspend fun getAllAlbumsList(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): List<BookEntity>

    @Query("SELECT * FROM books WHERE author_id = :authorId ORDER BY title ASC")
    fun getAlbumsByArtistId(authorId: Long): Flow<List<BookEntity>>

    @Query("""
        SELECT DISTINCT books.* FROM books
        INNER JOIN tracks ON books.id = tracks.book_id
        WHERE (:applyDirectoryFilter = 0 OR tracks.parent_directory_path IN (:allowedParentDirs))
        AND (books.title LIKE '%' || :query || '%' OR books.author_name LIKE '%' || :query || '%')        
        ORDER BY books.title ASC
    """)
    fun searchAlbums(
        query: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<BookEntity>>

    // --- Artist Queries ---
    @Query("""
        SELECT DISTINCT authors.* FROM authors
        INNER JOIN tracks ON authors.id = tracks.author_id
        WHERE (:applyDirectoryFilter = 0 OR tracks.parent_directory_path IN (:allowedParentDirs))
        ORDER BY authors.name ASC
    """)
    fun getArtists(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<AuthorEntity>>

    @Query("SELECT * FROM authors ORDER BY name ASC")
    fun getAllArtistsRaw(): Flow<List<AuthorEntity>>

    @Query("SELECT * FROM authors WHERE id = :authorId")
    fun getArtistById(authorId: Long): Flow<AuthorEntity?>

    @Query("SELECT * FROM authors WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    fun searchArtists(query: String): Flow<List<AuthorEntity>>

    @Query("SELECT COUNT(*) FROM authors")
    fun getArtistCount(): Flow<Int>

    @Query("""
        SELECT DISTINCT authors.* FROM authors
        INNER JOIN tracks ON authors.id = tracks.author_id
        WHERE (:applyDirectoryFilter = 0 OR tracks.parent_directory_path IN (:allowedParentDirs))
        ORDER BY authors.name ASC
    """)
    suspend fun getAllArtistsList(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): List<AuthorEntity>

    @Query("SELECT * FROM authors ORDER BY name ASC")
    suspend fun getAllArtistsListRaw(): List<AuthorEntity>

    @Query("""
        SELECT DISTINCT authors.* FROM authors
        INNER JOIN tracks ON authors.id = tracks.author_id
        WHERE (:applyDirectoryFilter = 0 OR tracks.parent_directory_path IN (:allowedParentDirs))
        AND authors.name LIKE '%' || :query || '%'
        ORDER BY authors.name ASC
    """)
    fun searchArtists(
        query: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<AuthorEntity>>

    // --- Artist Image Operations ---
    @Query("SELECT image_url FROM authors WHERE id = :authorId")
    suspend fun getArtistImageUrl(authorId: Long): String?

    @Query("UPDATE authors SET image_url = :imageUrl WHERE id = :authorId")
    suspend fun updateArtistImageUrl(authorId: Long, imageUrl: String)

    @Query("SELECT id FROM authors WHERE name = :name LIMIT 1")
    suspend fun getArtistIdByName(name: String): Long?

    @Query("SELECT MAX(id) FROM authors")
    suspend fun getMaxArtistId(): Long?

    // --- Genre Queries ---
    @Query("""
        SELECT * FROM tracks
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        AND genre LIKE :genreName
        ORDER BY title ASC
    """)
    fun getTracksByGenre(
        genreName: String,
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<TrackEntity>>

    @Query("""
        SELECT * FROM tracks
        WHERE (:applyDirectoryFilter = 0 OR parent_directory_path IN (:allowedParentDirs))
        AND (genre IS NULL OR genre = '')
        ORDER BY title ASC
    """)
    fun getTracksWithNullGenre(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<TrackEntity>>

    @Query("SELECT DISTINCT genre FROM tracks WHERE genre IS NOT NULL AND genre != '' ORDER BY genre ASC")
    fun getUniqueGenres(): Flow<List<String>>

    @Query("SELECT DISTINCT book_art_uri_string FROM tracks WHERE book_art_uri_string IS NOT NULL")
    fun getAllUniqueAlbumArtUrisFromSongs(): Flow<List<String>>

    @Query("DELETE FROM books WHERE id NOT IN (SELECT DISTINCT book_id FROM tracks)")
    suspend fun deleteOrphanedAlbums()

    @Query("DELETE FROM authors WHERE id NOT IN (SELECT DISTINCT author_id FROM tracks)")
    suspend fun deleteOrphanedArtists()

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

    @Query("UPDATE tracks SET title = :title, author_name = :artist, book_name = :album, genre = :genre, lyrics = :lyrics, track_number = :trackNumber WHERE id = :trackId")
    suspend fun updateTrackMetadata(
        trackId: Long,
        title: String,
        artist: String,
        album: String,
        genre: String?,
        lyrics: String?,
        trackNumber: Int
    )

    @Query("UPDATE tracks SET book_art_uri_string = :bookArtUri WHERE id = :trackId")
    suspend fun updateSongAlbumArt(trackId: Long, bookArtUri: String?)

    @Query("UPDATE tracks SET lyrics = :lyrics WHERE id = :trackId")
    suspend fun updateLyrics(trackId: Long, lyrics: String)

    @Query("UPDATE tracks SET lyrics = NULL WHERE id = :trackId")
    suspend fun resetLyrics(trackId: Long)

    @Query("UPDATE tracks SET lyrics = NULL")
    suspend fun resetAllLyrics()

    @Query("SELECT * FROM tracks")
    suspend fun getAllTracksList(): List<TrackEntity>

    @Query("SELECT book_art_uri_string FROM tracks WHERE id=:id")
    suspend fun getAlbumArtUriById(id: Long) : String?

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
    suspend fun getAllTrackAuthorCrossRefsList(): List<TrackAuthorCrossRef>

    @Query("DELETE FROM track_author_cross_ref")
    suspend fun clearAllTrackAuthorCrossRefs()

    @Query("DELETE FROM track_author_cross_ref WHERE track_id = :trackId")
    suspend fun deleteCrossRefsForSong(trackId: Long)

    @Query("DELETE FROM track_author_cross_ref WHERE author_id = :authorId")
    suspend fun deleteCrossRefsForArtist(authorId: Long)

    @Query("""
        SELECT authors.* FROM authors
        INNER JOIN track_author_cross_ref ON authors.id = track_author_cross_ref.author_id
        WHERE track_author_cross_ref.track_id = :trackId
        ORDER BY track_author_cross_ref.is_primary DESC, authors.name ASC
    """)
    fun getArtistsForSong(trackId: Long): Flow<List<AuthorEntity>>

    @Query("""
        SELECT authors.* FROM authors
        INNER JOIN track_author_cross_ref ON authors.id = track_author_cross_ref.author_id
        WHERE track_author_cross_ref.track_id = :trackId
        ORDER BY track_author_cross_ref.is_primary DESC, authors.name ASC
    """)
    suspend fun getArtistsForSongList(trackId: Long): List<AuthorEntity>

    @Query("""
        SELECT tracks.* FROM tracks
        INNER JOIN track_author_cross_ref ON tracks.id = track_author_cross_ref.track_id
        WHERE track_author_cross_ref.author_id = :authorId
        ORDER BY tracks.title ASC
    """)
    fun getTracksForArtist(authorId: Long): Flow<List<TrackEntity>>

    @Query("""
        SELECT tracks.* FROM tracks
        INNER JOIN track_author_cross_ref ON tracks.id = track_author_cross_ref.track_id
        WHERE track_author_cross_ref.author_id = :authorId
        ORDER BY tracks.title ASC
    """)
    suspend fun getTracksForArtistList(authorId: Long): List<TrackEntity>

    @Query("SELECT * FROM track_author_cross_ref WHERE track_id = :trackId")
    suspend fun getCrossRefsForSong(trackId: Long): List<TrackAuthorCrossRef>

    @Query("""
        SELECT authors.id AS author_id, authors.name FROM authors
        INNER JOIN track_author_cross_ref ON authors.id = track_author_cross_ref.author_id
        WHERE track_author_cross_ref.track_id = :trackId AND track_author_cross_ref.is_primary = 1
        LIMIT 1
    """)
    suspend fun getPrimaryArtistForSong(trackId: Long): PrimaryArtistInfo?

    @Query("SELECT COUNT(*) FROM track_author_cross_ref WHERE author_id = :authorId")
    suspend fun getTrackCountForArtist(authorId: Long): Int

    @Query("""
        SELECT authors.id, authors.name, authors.image_url,
               (SELECT COUNT(*) FROM track_author_cross_ref WHERE track_author_cross_ref.author_id = authors.id) AS track_count
        FROM authors
        ORDER BY authors.name ASC
    """)
    fun getArtistsWithSongCounts(): Flow<List<AuthorEntity>>

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
    fun getArtistsWithSongCountsFiltered(
        allowedParentDirs: List<String>,
        applyDirectoryFilter: Boolean
    ): Flow<List<AuthorEntity>>

    @Transaction
    suspend fun clearAllMusicDataWithCrossRefs() {
        clearAllTrackAuthorCrossRefs()
        clearAllTracks()
        clearAllAlbums()
        clearAllArtists()
    }

    @Transaction
    suspend fun insertMusicDataWithCrossRefs(
        songs: List<TrackEntity>,
        albums: List<BookEntity>,
        artists: List<AuthorEntity>,
        crossRefs: List<TrackAuthorCrossRef>
    ) {
        insertArtists(artists)
        insertAlbums(albums)
        insertSongs(songs)
        crossRefs.chunked(CROSS_REF_BATCH_SIZE).forEach { chunk ->
            insertTrackAuthorCrossRefs(chunk)
        }
    }

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrackAuthorCrossRefs(crossRefs: List<TrackAuthorCrossRef>)

    companion object {
        private const val SQLITE_MAX_VARIABLE_NUMBER = 999 
        private const val CROSS_REF_FIELDS_PER_OBJECT = 3
        val CROSS_REF_BATCH_SIZE: Int = SQLITE_MAX_VARIABLE_NUMBER / CROSS_REF_FIELDS_PER_OBJECT
        const val SONG_BATCH_SIZE = 500
    }
}
