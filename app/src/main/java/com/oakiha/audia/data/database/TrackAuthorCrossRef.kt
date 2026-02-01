package com.oakiha.audia.data.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.Relation

/**
 * Junction table for many-to-many relationship between tracks and authors.
 * Enables multi-author support where a track can have multiple authors
 * and an author can have multiple tracks.
 */
@Entity(
    tableName = "track_author_cross_ref",
    primaryKeys = ["track_id", "author_id"],
    indices = [
        Index(value = ["track_id"]),
        Index(value = ["author_id"]),
        Index(value = ["is_primary"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AuthorEntity::class,
            parentColumns = ["id"],
            childColumns = ["author_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TrackAuthorCrossRef(
    @ColumnInfo(name = "track_id") val trackId: Long,
    @ColumnInfo(name = "author_id") val authorId: Long,
    @ColumnInfo(name = "is_primary", defaultValue = "0") val isPrimary: Boolean = false
)

/**
 * Data class representing a track with all its associated authors.
 * Used for queries that need to retrieve a track along with its authors.
 */
data class TrackWithArtists(
    @Embedded val track: TrackEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TrackAuthorCrossRef::class,
            parentColumn = "track_id",
            entityColumn = "author_id"
        )
    )
    val artists: List<AuthorEntity>
)

/**
 * Data class representing an author with all their tracks.
 * Used for queries that need to retrieve an author along with their tracks.
 */
data class AuthorWithSongs(
    @Embedded val author: AuthorEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TrackAuthorCrossRef::class,
            parentColumn = "author_id",
            entityColumn = "track_id"
        )
    )
    val songs: List<TrackEntity>
)

/**
 * Data class for retrieving the primary author of a track efficiently.
 */
data class PrimaryAuthorInfo(
    @ColumnInfo(name = "author_id") val authorId: Long,
    @ColumnInfo(name = "name") val authorName: String
)

