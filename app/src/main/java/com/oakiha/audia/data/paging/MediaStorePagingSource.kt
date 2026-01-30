package com.oakiha.audia.data.paging

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.utils.normalizeMetadataText
import com.oakiha.audia.utils.normalizeMetadataTextOrEmpty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

/**
 * PagingSource that loads Tracks from MediaStore based on a pre-filtered list of IDs.
 * This ensures complex directory filtering is applied correctly before paging.
 */
class MediaStorePagingSource(
    private val context: Context,
    private val filteredIds: List<Long>,
    private val TrackIdToCategoryMap: Map<Long, String>
) : PagingSource<Int, Track>() {

    override fun getRefreshKey(state: PagingState<Int, Track>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Track> = withContext(Dispatchers.IO) {
        val pageIndex = params.key ?: 0
        
        if (filteredIds.isEmpty()) {
            return@withContext LoadResult.Page(
                data = emptyList(),
                prevKey = null,
                nextKey = null
            )
        }

        val start = pageIndex * params.loadSize
        if (start >= filteredIds.size) {
             return@withContext LoadResult.Page(
                data = emptyList(),
                prevKey = if (pageIndex > 0) pageIndex - 1 else null,
                nextKey = null
            )
        }
        
        val end = min(start + params.loadSize, filteredIds.size)
        val idsToLoad = filteredIds.subList(start, end)
        
        // Query MediaStore for details of these IDs
        val Tracks = fetchTrackDetails(idsToLoad)

        // Sort Tracks to match the order of idsToLoad (because "IN" query doesn't guarantee order)
        val TracksMap = Tracks.associateBy { it.id.toLong() }
        val orderedTracks = idsToLoad.mapNotNull { TracksMap[it] }

        val nextKey = if (end < filteredIds.size) pageIndex + 1 else null
        val prevKey = if (pageIndex > 0) pageIndex - 1 else null

        LoadResult.Page(
            data = orderedTracks,
            prevKey = prevKey,
            nextKey = nextKey
        )
    }

    private fun fetchTrackDetails(ids: List<Long>): List<Track> {
        val Tracks = mutableListOf<Track>()
        if (ids.isEmpty()) return Tracks

        val selection = "${MediaStore.Audio.Media._ID} IN (${ids.joinToString(",")})"
        
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.Author,
            MediaStore.Audio.Media.Author_ID,
            MediaStore.Audio.Media.Book,
            MediaStore.Audio.Media.Book_ID,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.Book_Author
        )

        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null // Order doesn't matter here, we sort in memory
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val AuthorCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.Author)
                val AuthorIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.Author_ID)
                val BookCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.Book)
                val BookIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.Book_ID)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val BookAuthorCol = cursor.getColumnIndex(MediaStore.Audio.Media.Book_Author)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val BookId = cursor.getLong(BookIdCol)
                    val path = cursor.getString(pathCol)

                    val Track = Track(
                        id = id.toString(),
                        title = cursor.getString(titleCol).normalizeMetadataTextOrEmpty(),
                        Author = cursor.getString(AuthorCol).normalizeMetadataTextOrEmpty(),
                        AuthorId = cursor.getLong(AuthorIdCol),
                        Authors = emptyList(),
                        Book = cursor.getString(BookCol).normalizeMetadataTextOrEmpty(),
                        BookId = BookId,
                        BookAuthor = if (BookAuthorCol != -1) cursor.getString(BookAuthorCol).normalizeMetadataText() else null,
                        path = path,
                        contentUriString = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString(),
                        BookArtUriString = ContentUris.withAppendedId(
                            android.net.Uri.parse("content://media/external/audio/Bookart"),
                            BookId
                        ).toString(),
                        duration = cursor.getLong(durationCol),
                        Category = TrackIdToCategoryMap[id],
                        Transcript = null,
                        isFavorite = false, // Not critical for paging source display usually, or passed in?
                        trackNumber = cursor.getInt(trackCol),
                        year = cursor.getInt(yearCol),
                        dateAdded = cursor.getLong(dateAddedCol),
                        dateModified = cursor.getLong(dateModifiedCol),
                        mimeType = null,
                        bitrate = null,
                        sampleRate = null
                    )
                    Tracks.add(Track)
                }
            }
        } catch (e: Exception) {
            // Log error
        }
        return Tracks
    }
}
