package com.oakiha.audia.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        BookArtThemeEntity::class,
        SearchHistoryEntity::class,
        TrackEntity::class,
        BookEntity::class,
        AuthorEntity::class,
        TransitionRuleEntity::class,
        TrackAuthorCrossRef::class,
        TrackEngagementEntity::class,
        FavoritesEntity::class,
        TranscriptEntity::class
    ],
    version = 14, // Incremented version for Transcript table
    exportSchema = false
)
abstract class AudioBookDatabase : RoomDatabase() {
    abstract fun BookArtThemeDao(): BookArtThemeDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun AudiobookDao(): AudiobookDao
    abstract fun transitionDao(): TransitionDao
    abstract fun engagementDao(): EngagementDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun TranscriptDao(): TranscriptDao // Added FavoritesDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Tracks ADD COLUMN parent_directory_path TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Tracks ADD COLUMN Transcript TEXT")
            }
        }

//        val MIGRATION_6_7 = object : Migration(6, 7) {
//            override fun migrate(db: SupportSQLiteDatabase) {
//                db.execSQL("ALTER TABLE Tracks ADD COLUMN date_added INTEGER NOT NULL DEFAULT 0")
//            }
//        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE Tracks ADD COLUMN mime_type TEXT")
                db.execSQL("ALTER TABLE Tracks ADD COLUMN bitrate INTEGER")
                db.execSQL("ALTER TABLE Tracks ADD COLUMN sample_rate INTEGER")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add Book_Author column to Tracks table
                db.execSQL("ALTER TABLE Tracks ADD COLUMN Book_Author TEXT DEFAULT NULL")
                
                // Create Track_Author_cross_ref junction table for many-to-many relationship
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS Track_Author_cross_ref (
                        Track_id INTEGER NOT NULL,
                        Author_id INTEGER NOT NULL,
                        is_primary INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (Track_id, Author_id),
                        FOREIGN KEY (Track_id) REFERENCES Tracks(id) ON DELETE CASCADE,
                        FOREIGN KEY (Author_id) REFERENCES Authors(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                
                // Create indices for efficient queries
                db.execSQL("CREATE INDEX IF NOT EXISTS index_Track_Author_cross_ref_Track_id ON Track_Author_cross_ref(Track_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_Track_Author_cross_ref_Author_id ON Track_Author_cross_ref(Author_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_Track_Author_cross_ref_is_primary ON Track_Author_cross_ref(is_primary)")
                
                // Migrate existing Track-Author relationships to junction table
                // Each existing Track gets its current Author as the primary Author
                db.execSQL("""
                    INSERT OR REPLACE INTO Track_Author_cross_ref (Track_id, Author_id, is_primary)
                    SELECT id, Author_id, 1 FROM Tracks WHERE Author_id IS NOT NULL
                """.trimIndent())
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add image_url column to Authors table for Deezer Author images
                db.execSQL("ALTER TABLE Authors ADD COLUMN image_url TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create Track_engagements table for tracking play statistics
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS Track_engagements (
                        Track_id TEXT NOT NULL PRIMARY KEY,
                        play_count INTEGER NOT NULL DEFAULT 0,
                        total_play_duration_ms INTEGER NOT NULL DEFAULT 0,
                        last_played_timestamp INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS favorites (
                        TrackId INTEGER NOT NULL PRIMARY KEY,
                        isFavorite INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())
                
                // Migrate existing favorites from Tracks table if possible
                // Note: We need to cast is_favorite (boolean/int) to ensure compatibility
                db.execSQL("""
                    INSERT OR IGNORE INTO favorites (TrackId, isFavorite, timestamp)
                    SELECT id, is_favorite, ? FROM Tracks WHERE is_favorite = 1
                """, arrayOf(System.currentTimeMillis()))
            }
        }
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `Transcript` (`TrackId` INTEGER NOT NULL, `content` TEXT NOT NULL, `isSynced` INTEGER NOT NULL DEFAULT 0, `source` TEXT, PRIMARY KEY(`TrackId`))"
                )
                database.execSQL(
                    "INSERT INTO Transcript (TrackId, content) SELECT id, Transcript FROM Tracks WHERE Transcript IS NOT NULL AND Transcript != ''"
                )
            }
        }
    }
}
