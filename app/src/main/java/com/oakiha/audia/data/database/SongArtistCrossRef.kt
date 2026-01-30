package com.oakiha.audia.data.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.Relation

/**
 * Junction table for many-to-many relationship between books and Authors.
 * Enables multi-Author support where a Track can have multiple Authors
 * and an Author can have multiple Tracks.
 */
@Entity(
    tableName = "Track_Author_cross_ref",
    primaryKeys = ["Track_id", "Author_id"],
    indices = [
        Index(value = ["Track_id"]),
        Index(value = ["Author_id"]),
        Index(value = ["is_primary"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["Track_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AuthorEntity::class,
            parentColumns = ["id"],
            childColumns = ["Author_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TrackAuthorCrossRef(
    @ColumnInfo(name = "Track_id") val TrackId: Long,
    @ColumnInfo(name = "Author_id") val AuthorId: Long,
    @ColumnInfo(name = "is_primary", defaultValue = "0") val isPrimary: Boolean = false
)

/**
 * Data class representing a Track with all its associated Authors.
 * Used for queries that need to retrieve a Track along with its Authors.
 */
data class TrackWithAuthors(
    @Embedded val Track: TrackEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TrackAuthorCrossRef::class,
            parentColumn = "Track_id",
            entityColumn = "Author_id"
        )
    )
    val Authors: List<AuthorEntity>
)

/**
 * Data class representing an Author with all their Tracks.
 * Used for queries that need to retrieve an Author along with their Tracks.
 */
data class AuthorWithTracks(
    @Embedded val Author: AuthorEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TrackAuthorCrossRef::class,
            parentColumn = "Author_id",
            entityColumn = "Track_id"
        )
    )
    val Tracks: List<TrackEntity>
)

/**
 * Data class for retrieving the primary Author of a Track efficiently.
 */
data class PrimaryAuthorInfo(
    @ColumnInfo(name = "Author_id") val AuthorId: Long,
    @ColumnInfo(name = "name") val AuthorName: String
)
