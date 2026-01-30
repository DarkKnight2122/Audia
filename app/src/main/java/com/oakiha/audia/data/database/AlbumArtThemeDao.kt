package com.oakiha.audia.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BookArtThemeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTheme(theme: BookArtThemeEntity)

    @Query("SELECT * FROM Book_art_themes WHERE BookArtUriString = :uriString")
    suspend fun getThemeByUri(uriString: String): BookArtThemeEntity?

    @Query("DELETE FROM Book_art_themes WHERE BookArtUriString IN (:uriStrings)")
    suspend fun deleteThemesByUris(uriStrings: List<String>)
}
