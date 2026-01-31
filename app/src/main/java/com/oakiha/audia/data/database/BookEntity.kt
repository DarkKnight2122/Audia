package com.oakiha.audia.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.oakiha.audia.data.model.Book
import com.oakiha.audia.utils.normalizeMetadataTextOrEmpty

@Entity(
    tableName = "albums",
    indices = [
        Index(value = ["title"], unique = false),
        Index(value = ["artist_id"], unique = false), // Para buscar Ã¡lbumes por artista
        Index(value = ["artist_name"], unique = false) // Nuevo Ã­ndice para bÃºsquedas por nombre de artista del Ã¡lbum
    ]
)
data class BookEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist_name") val artistName: String, // Nombre del artista del Ã¡lbum
    @ColumnInfo(name = "artist_id") val authorId: Long, // ID del artista principal del Ã¡lbum (si aplica)
    @ColumnInfo(name = "album_art_uri_string") val bookArtUriString: String?,
    @ColumnInfo(name = "song_count") val trackCount: Int,
    @ColumnInfo(name = "year") val year: Int
)

fun BookEntity.toAlbum(): Book {
    return Book(
        id = this.id,
        title = this.title.normalizeMetadataTextOrEmpty(),
        artist = this.authorName.normalizeMetadataTextOrEmpty(),
        bookArtUriString = this.bookArtUriString, // El modelo Album usa bookArtUrl
        trackCount = this.trackCount,
        year = this.year
    )
}

fun List<BookEntity>.toAlbums(): List<Book> {
    return this.map { it.toAlbum() }
}

fun Album.toEntity(authorIdForAlbum: Long): BookEntity { // Necesitamos pasar el authorId si el modelo Album no lo tiene directamente
    return BookEntity(
        id = this.id,
        title = this.title,
        artistName = this.author,
        authorId = authorIdForAlbum, // Asignar el ID del artista
        bookArtUriString = this.bookArtUriString,
        trackCount = this.trackCount,
        year = this.year
    )
}
