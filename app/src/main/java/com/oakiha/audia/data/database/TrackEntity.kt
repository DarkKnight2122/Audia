package com.oakiha.audia.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.oakiha.audia.data.model.AuthorRef
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.utils.normalizeMetadataText
import com.oakiha.audia.utils.normalizeMetadataTextOrEmpty

@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["title"], unique = false),
        Index(value = ["album_id"], unique = false),
        Index(value = ["artist_id"], unique = false),
        Index(value = ["artist_name"], unique = false), // Nuevo Ã­ndice para bÃºsquedas por nombre de artista
        Index(value = ["genre"], unique = false),
        Index(value = ["parent_directory_path"], unique = false) // Ãndice para filtrado por directorio
    ],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["album_id"],
            onDelete = ForeignKey.CASCADE // Si un Ã¡lbum se borra, sus canciones tambiÃ©n
        ),
        ForeignKey(
            entity = AuthorEntity::class,
            parentColumns = ["id"],
            childColumns = ["artist_id"],
            onDelete = ForeignKey.SET_NULL // Si un artista se borra, el artist_id de la canciÃ³n se pone a null
                                          // o podrÃ­as elegir CASCADE si las canciones no deben existir sin artista.
                                          // SET_NULL es mÃ¡s flexible si las canciones pueden ser de "Artista Desconocido".
        )
    ]
)
data class TrackEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist_name") val artistName: String, // Display string (combined or primary)
    @ColumnInfo(name = "artist_id") val authorId: Long, // Primary artist ID for backward compatibility
    @ColumnInfo(name = "album_artist") val bookArtist: String? = null, // Album artist from metadata
    @ColumnInfo(name = "album_name") val albumName: String,
    @ColumnInfo(name = "album_id") val bookId: Long, // index = true eliminado
    @ColumnInfo(name = "content_uri_string") val contentUriString: String,
    @ColumnInfo(name = "album_art_uri_string") val bookArtUriString: String?,
    @ColumnInfo(name = "duration") val duration: Long,
    @ColumnInfo(name = "genre") val genre: String?,
    @ColumnInfo(name = "file_path") val filePath: String, // Added filePath
    @ColumnInfo(name = "parent_directory_path") val parentDirectoryPath: String, // Added for directory filtering
    @ColumnInfo(name = "is_favorite", defaultValue = "0") val isFavorite: Boolean = false,
    @ColumnInfo(name = "lyrics", defaultValue = "null") val lyrics: String? = null,
    @ColumnInfo(name = "track_number", defaultValue = "0") val trackNumber: Int = 0,
    @ColumnInfo(name = "year", defaultValue = "0") val year: Int = 0,
    @ColumnInfo(name = "date_added", defaultValue = "0") val dateAdded: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "mime_type") val mimeType: String? = null,
    @ColumnInfo(name = "bitrate") val bitrate: Int? = null, // bits per second
    @ColumnInfo(name = "sample_rate") val sampleRate: Int? = null // Hz
)

fun TrackEntity.toTrack(): Track {
    return Track(
        id = this.id.toString(),
        title = this.title.normalizeMetadataTextOrEmpty(),
        artist = this.authorName.normalizeMetadataTextOrEmpty(),
        authorId = this.authorId,
        artists = emptyList(), // Will be populated from junction table when needed
        album = this.bookName.normalizeMetadataTextOrEmpty(),
        bookId = this.bookId,
        bookArtist = this.bookArtist?.normalizeMetadataText(),
        path = this.filePath, // Map the file path
        contentUriString = this.contentUriString,
        bookArtUriString = this.bookArtUriString,
        duration = this.duration,
        genre = this.genre.normalizeMetadataText(),
        lyrics = this.lyrics?.normalizeMetadataText(),
        isFavorite = this.isFavorite,
        trackNumber = this.trackNumber,
        dateAdded = this.dateAdded,
        year = this.year,
        mimeType = this.mimeType,
        bitrate = this.bitrate,
        sampleRate = this.sampleRate
    )
}

/**
 * Converts a TrackEntity to Song with artists from the junction table.
 */
fun TrackEntity.toTrackWithArtistRefs(artists: List<AuthorEntity>, crossRefs: List<TrackAuthorCrossRef>): Track {
    val crossRefByArtistId = crossRefs.associateBy { it.authorId }
    val artistRefs = artists.map { artist ->
        val crossRef = crossRefByArtistId[artist.id]
        AuthorRef(
            id = artist.id,
            name = artist.name.normalizeMetadataTextOrEmpty(),
            isPrimary = crossRef?.isPrimary ?: false
        )
    }.sortedByDescending { it.isPrimary }
    
    return Track(
        id = this.id.toString(),
        title = this.title.normalizeMetadataTextOrEmpty(),
        artist = this.authorName.normalizeMetadataTextOrEmpty(),
        authorId = this.authorId,
        artists = artistRefs,
        album = this.bookName.normalizeMetadataTextOrEmpty(),
        bookId = this.bookId,
        bookArtist = this.bookArtist?.normalizeMetadataText(),
        path = this.filePath,
        contentUriString = this.contentUriString,
        bookArtUriString = this.bookArtUriString,
        duration = this.duration,
        genre = this.genre.normalizeMetadataText(),
        lyrics = this.lyrics?.normalizeMetadataText(),
        isFavorite = this.isFavorite,
        trackNumber = this.trackNumber,
        dateAdded = this.dateAdded,
        year = this.year,
        mimeType = this.mimeType,
        bitrate = this.bitrate,
        sampleRate = this.sampleRate
    )
}

fun List<TrackEntity>.toTracks(): List<Track> {
    return this.map { it.toTrack() }
}

// El modelo Song usa id como String, pero la entidad lo necesita como Long (de MediaStore)
// El modelo Song no tiene filePath, asÃ­ que no se puede mapear desde ahÃ­ directamente.
// filePath y parentDirectoryPath se poblarÃ¡n desde MediaStore en el SyncWorker.
fun Song.toEntity(filePathFromMediaStore: String, parentDirFromMediaStore: String): TrackEntity {
    return TrackEntity(
        id = this.id.toLong(), // Asumiendo que el ID del modelo Song puede convertirse a Long
        title = this.title,
        artistName = this.author,
        authorId = this.authorId,
        bookArtist = this.bookArtist,
        albumName = this.book,
        bookId = this.bookId,
        contentUriString = this.contentUriString,
        bookArtUriString = this.bookArtUriString,
        duration = this.duration,
        genre = this.genre,
        lyrics = this.lyrics,
        filePath = filePathFromMediaStore,
        parentDirectoryPath = parentDirFromMediaStore,
        dateAdded = this.dateAdded,
        year = this.year,
        mimeType = this.mimeType,
        bitrate = this.bitrate,
        sampleRate = this.sampleRate
    )
}

// Sobrecarga o alternativa si los paths no estÃ¡n disponibles o no son necesarios al convertir de Modelo a Entidad
// (menos probable que se use si la entidad siempre requiere los paths)
fun Song.toEntityWithoutPaths(): TrackEntity {
    return TrackEntity(
        id = this.id.toLong(),
        title = this.title,
        artistName = this.author,
        authorId = this.authorId,
        bookArtist = this.bookArtist,
        albumName = this.book,
        bookId = this.bookId,
        contentUriString = this.contentUriString,
        bookArtUriString = this.bookArtUriString,
        duration = this.duration,
        genre = this.genre,
        lyrics = this.lyrics,
        filePath = "", // Default o manejar como no disponible
        parentDirectoryPath = "", // Default o manejar como no disponible
        dateAdded = this.dateAdded,
        year = this.year,
        mimeType = this.mimeType,
        bitrate = this.bitrate,
        sampleRate = this.sampleRate
    )
}
