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
        LyricsEntity::class
    ],
    version = 14, // Incremented version for lyrics table
    exportSchema = false
)
abstract class AudioBookDatabase : RoomDatabase() {
    abstract fun bookArtThemeDao(): BookArtThemeDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun audiobookDao(): AudiobookDao
    abstract fun transitionDao(): TransitionDao
    abstract fun engagementDao(): EngagementDao
    abstract fun favoritesDao(): FavoritesDao
    abstract fun lyricsDao(): LyricsDao // Added FavoritesDao

    companion object {
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN parent_directory_path TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN lyrics TEXT")
            }
        }

//        val MIGRATION_6_7 = object : Migration(6, 7) {
//            override fun migrate(db: SupportSQLiteDatabase) {
//                db.execSQL("ALTER TABLE tracks ADD COLUMN date_added INTEGER NOT NULL DEFAULT 0")
//            }
//        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracks ADD COLUMN mime_type TEXT")
                db.execSQL("ALTER TABLE tracks ADD COLUMN bitrate INTEGER")
                db.execSQL("ALTER TABLE tracks ADD COLUMN sample_rate INTEGER")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add book_author column to tracks table
                db.execSQL("ALTER TABLE tracks ADD COLUMN book_author TEXT DEFAULT NULL")
                
                // Create track_author_cross_ref junction table for many-to-many relationship
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS track_author_cross_ref (
                        track_id INTEGER NOT NULL,
                        author_id INTEGER NOT NULL,
                        is_primary INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (track_id, author_id),
                        FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE,
                        FOREIGN KEY (author_id) REFERENCES authors(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                
                // Create indices for efficient queries
                db.execSQL("CREATE INDEX IF NOT EXISTS index_track_author_cross_ref_track_id ON track_author_cross_ref(track_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_track_author_cross_ref_author_id ON track_author_cross_ref(author_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_track_author_cross_ref_is_primary ON track_author_cross_ref(is_primary)")
                
                // Migrate existing track-author relationships to junction table
                // Each existing track gets its current author as the primary author
                db.execSQL("""
                    INSERT OR REPLACE INTO track_author_cross_ref (track_id, author_id, is_primary) SELECT id, author_id, 1 FROM tracks WHERE author_id IS NOT NULL
                """.trimIndent())
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add image_url column to authors table for Deezer author images
                db.execSQL("ALTER TABLE authors ADD COLUMN image_url TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create track_engagements table for tracking play statistics
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS track_engagements (
                        track_id TEXT NOT NULL PRIMARY KEY,
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
                        trackId INTEGER NOT NULL PRIMARY KEY,
                        isFavorite INTEGER NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())
                
                // Migrate existing favorites from tracks table if possible
                // Note: We need to cast is_favorite (boolean/int) to ensure compatibility
                db.execSQL("""
                    INSERT OR IGNORE INTO favorites (trackId, isFavorite, timestamp)
                    SELECT id, is_favorite, ? FROM tracks WHERE is_favorite = 1
                """, arrayOf(System.currentTimeMillis()))
            }
        }
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE TABLE IF NOT EXISTS `lyrics` (`trackId` INTEGER NOT NULL, `content` TEXT NOT NULL, `isSynced` INTEGER NOT NULL DEFAULT 0, `source` TEXT, PRIMARY KEY(`trackId`))"
                )
                database.execSQL(
                    "INSERT INTO lyrics (trackId, content) SELECT id, lyrics FROM tracks WHERE lyrics IS NOT NULL AND lyrics != ''"
                )
            }
        }
    }
}




