package com.oakiha.audia.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.oakiha.audia.data.model.Author
import com.oakiha.audia.utils.normalizeMetadataTextOrEmpty

@Entity(
    tableName = "authors",
    indices = [Index(value = ["name"], unique = false)] // ÃƒÂndice en el nombre para bÃƒÂºsquedas rÃƒÂ¡pidas
)
data class AuthorEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "track_count") val trackCount: Int,
    @ColumnInfo(name = "image_url") val imageUrl: String? = null
)

fun AuthorEntity.toAuthor(): Author {
    return Author(
        id = this.id,
        name = this.name.normalizeMetadataTextOrEmpty(),
        trackCount = this.trackCount, // El modelo Artist usa trackCount, MediaStore usa NUMBER_OF_TRACKS
        imageUrl = this.imageUrl
    )
}

fun List<AuthorEntity>.toAuthors(): List<Author> {
    return this.map { it.toAuthor() }
}

fun Author.toEntity(): AuthorEntity {
    return AuthorEntity(
        id = this.id,
        name = this.name,
        trackCount = this.trackCount,
        imageUrl = this.imageUrl
    )
}

