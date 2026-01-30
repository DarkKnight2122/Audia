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
    tableName = "Tracks",
    indices = [
        Index(value = ["title"], unique = false),
        Index(value = ["Book_id"], unique = false),
        Index(value = ["Author_id"], unique = false),
        Index(value = ["Author_name"], unique = false), // Nuevo ÃƒÂ­ndice para bÃƒÂºsquedas por nombre de Authora
        Index(value = ["Category"], unique = false),
        Index(value = ["parent_directory_path"], unique = false) // ÃƒÂndice para filtrado por directorio
    ],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["Book_id"],
            onDelete = ForeignKey.CASCADE // Si un ÃƒÂ¡lbum se borra, sus canciones tambiÃƒÂ©n
        ),
        ForeignKey(
            entity = AuthorEntity::class,
            parentColumns = ["id"],
            childColumns = ["Author_id"],
            onDelete = ForeignKey.SET_NULL // Si un Authora se borra, el Author_id de la canciÃƒÂ³n se pone a null
                                          // o podrÃƒÂ­as elegir CASCADE si las canciones no deben existir sin Authora.
                                          // SET_NULL es mÃƒÂ¡s flexible si las canciones pueden ser de "Authora Desconocido".
        )
    ]
)
data class TrackEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "Author_name") val AuthorName: String, // Display string (combined or primary)
    @ColumnInfo(name = "Author_id") val AuthorId: Long, // Primary Author ID for backward compatibility
    @ColumnInfo(name = "Book_Author") val BookAuthor: String? = null, // Book Author from metadata
    @ColumnInfo(name = "Book_name") val BookName: String,
    @ColumnInfo(name = "Book_id") val BookId: Long, // index = true eliminado
    @ColumnInfo(name = "content_uri_string") val contentUriString: String,
    @ColumnInfo(name = "Book_art_uri_string") val BookArtUriString: String?,
    @ColumnInfo(name = "duration") val duration: Long,
    @ColumnInfo(name = "Category") val Category: String?,
    @ColumnInfo(name = "file_path") val filePath: String, // Added filePath
    @ColumnInfo(name = "parent_directory_path") val parentDirectoryPath: String, // Added for directory filtering
    @ColumnInfo(name = "is_favorite", defaultValue = "0") val isFavorite: Boolean = false,
    @ColumnInfo(name = "Transcript", defaultValue = "null") val Transcript: String? = null,
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
        Author = this.AuthorName.normalizeMetadataTextOrEmpty(),
        AuthorId = this.AuthorId,
        Authors = emptyList(), // Will be populated from junction table when needed
        Book = this.BookName.normalizeMetadataTextOrEmpty(),
        BookId = this.BookId,
        BookAuthor = this.BookAuthor?.normalizeMetadataText(),
        path = this.filePath, // Map the file path
        contentUriString = this.contentUriString,
        BookArtUriString = this.BookArtUriString,
        duration = this.duration,
        Category = this.Category.normalizeMetadataText(),
        Transcript = this.Transcript?.normalizeMetadataText(),
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
 * Converts a TrackEntity to Track with Authors from the junction table.
 */
fun TrackEntity.toTrackWithAuthorRefs(Authors: List<AuthorEntity>, crossRefs: List<TrackAuthorCrossRef>): Track {
    val crossRefByAuthorId = crossRefs.associateBy { it.AuthorId }
    val AuthorRefs = Authors.map { Author ->
        val crossRef = crossRefByAuthorId[Author.id]
        AuthorRef(
            id = Author.id,
            name = Author.name.normalizeMetadataTextOrEmpty(),
            isPrimary = crossRef?.isPrimary ?: false
        )
    }.sortedByDescending { it.isPrimary }
    
    return Track(
        id = this.id.toString(),
        title = this.title.normalizeMetadataTextOrEmpty(),
        Author = this.AuthorName.normalizeMetadataTextOrEmpty(),
        AuthorId = this.AuthorId,
        Authors = AuthorRefs,
        Book = this.BookName.normalizeMetadataTextOrEmpty(),
        BookId = this.BookId,
        BookAuthor = this.BookAuthor?.normalizeMetadataText(),
        path = this.filePath,
        contentUriString = this.contentUriString,
        BookArtUriString = this.BookArtUriString,
        duration = this.duration,
        Category = this.Category.normalizeMetadataText(),
        Transcript = this.Transcript?.normalizeMetadataText(),
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

// El modelo Track usa id como String, pero la entidad lo necesita como Long (de MediaStore)
// El modelo Track no tiene filePath, asÃƒÂ­ que no se puede mapear desde ahÃƒÂ­ directamente.
// filePath y parentDirectoryPath se poblarÃƒÂ¡n desde MediaStore en el SyncWorker.
fun Track.toEntity(filePathFromMediaStore: String, parentDirFromMediaStore: String): TrackEntity {
    return TrackEntity(
        id = this.id.toLong(), // Asumiendo que el ID del modelo Track puede convertirse a Long
        title = this.title,
        AuthorName = this.Author,
        AuthorId = this.AuthorId,
        BookAuthor = this.BookAuthor,
        BookName = this.Book,
        BookId = this.BookId,
        contentUriString = this.contentUriString,
        BookArtUriString = this.BookArtUriString,
        duration = this.duration,
        Category = this.Category,
        Transcript = this.Transcript,
        filePath = filePathFromMediaStore,
        parentDirectoryPath = parentDirFromMediaStore,
        dateAdded = this.dateAdded,
        year = this.year,
        mimeType = this.mimeType,
        bitrate = this.bitrate,
        sampleRate = this.sampleRate
    )
}

// Sobrecarga o alternativa si los paths no estÃƒÂ¡n disponibles o no son necesarios al convertir de Modelo a Entidad
// (menos probable que se use si la entidad siempre requiere los paths)
fun Track.toEntityWithoutPaths(): TrackEntity {
    return TrackEntity(
        id = this.id.toLong(),
        title = this.title,
        AuthorName = this.Author,
        AuthorId = this.AuthorId,
        BookAuthor = this.BookAuthor,
        BookName = this.Book,
        BookId = this.BookId,
        contentUriString = this.contentUriString,
        BookArtUriString = this.BookArtUriString,
        duration = this.duration,
        Category = this.Category,
        Transcript = this.Transcript,
        filePath = "", // Default o manejar como no disponible
        parentDirectoryPath = "", // Default o manejar como no disponible
        dateAdded = this.dateAdded,
        year = this.year,
        mimeType = this.mimeType,
        bitrate = this.bitrate,
        sampleRate = this.sampleRate
    )
}
