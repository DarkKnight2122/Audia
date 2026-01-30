package com.oakiha.audia.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.oakiha.audia.data.model.Book
import com.oakiha.audia.utils.normalizeMetadataTextOrEmpty

@Entity(
    tableName = "Books",
    indices = [
        Index(value = ["title"], unique = false),
        Index(value = ["Author_id"], unique = false), // Para buscar Ã¡lbumes por Authora
        Index(value = ["Author_name"], unique = false) // Nuevo Ã­ndice para bÃºsquedas por nombre de Authora del Ã¡lbum
    ]
)
data class BookEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "Author_name") val AuthorName: String, // Nombre del Authora del Ã¡lbum
    @ColumnInfo(name = "Author_id") val AuthorId: Long, // ID del Authora principal del Ã¡lbum (si aplica)
    @ColumnInfo(name = "Book_art_uri_string") val BookArtUriString: String?,
    @ColumnInfo(name = "Track_count") val TrackCount: Int,
    @ColumnInfo(name = "year") val year: Int
)

fun BookEntity.toBook(): Book {
    return Book(
        id = this.id,
        title = this.title.normalizeMetadataTextOrEmpty(),
        Author = this.AuthorName.normalizeMetadataTextOrEmpty(),
        BookArtUriString = this.BookArtUriString, // El modelo Book usa BookArtUrl
        TrackCount = this.TrackCount,
        year = this.year
    )
}

fun List<BookEntity>.toBooks(): List<Book> {
    return this.map { it.toBook() }
}

fun Book.toEntity(AuthorIdForBook: Long): BookEntity { // Necesitamos pasar el AuthorId si el modelo Book no lo tiene directamente
    return BookEntity(
        id = this.id,
        title = this.title,
        AuthorName = this.Author,
        AuthorId = AuthorIdForBook, // Asignar el ID del Authora
        BookArtUriString = this.BookArtUriString,
        TrackCount = this.TrackCount,
        year = this.year
    )
}
