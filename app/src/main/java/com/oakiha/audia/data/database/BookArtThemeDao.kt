package com.oakiha.audia.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BookArtThemeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTheme(theme: BookArtThemeEntity)

    @Query("SELECT * FROM album_art_themes WHERE albumArtUriString = :uriString")
    suspend fun getThemeByUri(uriString: String): BookArtThemeEntity?

    @Query("DELETE FROM album_art_themes WHERE albumArtUriString IN (:uriStrings)")
    suspend fun deleteThemesByUris(uriStrings: List<String>)
}
