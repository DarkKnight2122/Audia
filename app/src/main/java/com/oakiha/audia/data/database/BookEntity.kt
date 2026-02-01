package com.oakiha.audia.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.oakiha.audia.data.model.Book
import com.oakiha.audia.utils.normalizeMetadataTextOrEmpty

@Entity(
    tableName = "books",
    indices = [
        Index(value = ["title"], unique = false),
        Index(value = ["author_id"], unique = false), // Para buscar ÃƒÂ¡lbumes por artista
        Index(value = ["author_name"], unique = false) // Nuevo ÃƒÂ­ndice para bÃƒÂºsquedas por nombre de artista del ÃƒÂ¡lbum
    ]
)
data class BookEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "author_name") val artistName: String, // Nombre del artista del ÃƒÂ¡lbum
    @ColumnInfo(name = "author_id") val authorId: Long, // ID del artista principal del ÃƒÂ¡lbum (si aplica)
    @ColumnInfo(name = "book_art_uri_string") val bookArtUriString: String?,
    @ColumnInfo(name = "song_count") val trackCount: Int,
    @ColumnInfo(name = "year") val year: Int
)

fun BookEntity.toBook(): Book {
    return Book(
        id = this.id,
        title = this.title.normalizeMetadataTextOrEmpty(),
        author = this.artistName.normalizeMetadataTextOrEmpty(),
        bookArtUriString = this.bookArtUriString, // El modelo Album usa bookArtUrl
        trackCount = this.trackCount,
        year = this.year
    )
}

fun List<BookEntity>.toBooks(): List<Book> {
    return this.map { it.toBook() }
}

fun Book.toEntity(authorIdForBook: Long): BookEntity { // Necesitamos pasar el authorId si el modelo Album no lo tiene directamente
    return BookEntity(
        id = this.id,
        title = this.title,
        artistName = this.author,
        authorId = authorIdForBook, // Asignar el ID del artista
        bookArtUriString = this.bookArtUriString,
        trackCount = this.trackCount,
        year = this.year
    )
}



