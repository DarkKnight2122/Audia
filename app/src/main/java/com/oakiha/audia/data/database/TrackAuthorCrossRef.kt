package com.oakiha.audia.data.database

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.Relation

/**
 * Junction table for many-to-many relationship between tracks and artists.
 * Enables multi-artist support where a song can have multiple artists
 * and an artist can have multiple songs.
 */
@Entity(
    tableName = "song_artist_cross_ref",
    primaryKeys = ["song_id", "artist_id"],
    indices = [
        Index(value = ["song_id"]),
        Index(value = ["artist_id"]),
        Index(value = ["is_primary"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = AuthorEntity::class,
            parentColumns = ["id"],
            childColumns = ["artist_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TrackAuthorCrossRef(
    @ColumnInfo(name = "song_id") val songId: Long,
    @ColumnInfo(name = "artist_id") val artistId: Long,
    @ColumnInfo(name = "is_primary", defaultValue = "0") val isPrimary: Boolean = false
)

/**
 * Data class representing a song with all its associated artists.
 * Used for queries that need to retrieve a song along with its artists.
 */
data class SongWithArtists(
    @Embedded val song: TrackEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TrackAuthorCrossRef::class,
            parentColumn = "song_id",
            entityColumn = "artist_id"
        )
    )
    val artists: List<AuthorEntity>
)

/**
 * Data class representing an artist with all their songs.
 * Used for queries that need to retrieve an artist along with their songs.
 */
data class ArtistWithSongs(
    @Embedded val artist: AuthorEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = TrackAuthorCrossRef::class,
            parentColumn = "artist_id",
            entityColumn = "song_id"
        )
    )
    val songs: List<TrackEntity>
)

/**
 * Data class for retrieving the primary artist of a song efficiently.
 */
data class PrimaryArtistInfo(
    @ColumnInfo(name = "artist_id") val artistId: Long,
    @ColumnInfo(name = "name") val artistName: String
)
