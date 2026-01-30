package com.oakiha.audia.data.Booklist

import android.content.Context
import android.net.Uri
import com.oakiha.audia.data.model.Booklist
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.data.repository.AudiobookRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class M3uManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val AudiobookRepository: AudiobookRepository
) {

    suspend fun parseM3u(uri: Uri): Pair<String, List<String>> {
        val TrackIds = mutableListOf<String>()
        var BooklistName = "Imported Booklist"

        // Pre-load all Tracks once for efficient lookup (fixes performance issue with large M3U files)
        val allTracks = AudiobookRepository.getAudioFiles().first()
        
        // Build lookup maps for fast matching
        val TracksByPath = allTracks.associateBy { it.path }
        val TracksByFileName = allTracks.groupBy { it.path.substringAfterLast("/") }
        val TracksByContentUriFileName = allTracks.groupBy { it.contentUriString.substringAfterLast("/") }

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val trimmedLine = line?.trim() ?: continue
                    if (trimmedLine.isEmpty() || trimmedLine.startsWith("#")) {
                        // Handle metadata if needed, e.g., #EXTINF
                        continue
                    }
                    
                    // trimmedLine is likely a file path or URI
                    // We need to find a Track in our database that matches this path
                    
                    // First try exact path match from pre-loaded map
                    val TrackByPath = TracksByPath[trimmedLine]
                    if (TrackByPath != null) {
                        TrackIds.add(TrackByPath.id)
                    } else {
                        // Try to match by filename if path doesn't match exactly
                        val fileName = trimmedLine.substringAfterLast("/")
                        val matchedTrack = TracksByFileName[fileName]?.firstOrNull()
                            ?: TracksByContentUriFileName[fileName]?.firstOrNull()
                        if (matchedTrack != null) {
                            TrackIds.add(matchedTrack.id)
                        }
                    }
                }
            }
        }

        // Try to get the filename as Booklist name
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                BooklistName = cursor.getString(nameIndex).removeSuffix(".m3u").removeSuffix(".m3u8")
            }
        }

        return Pair(BooklistName, TrackIds)
    }

    fun generateM3u(Booklist: Booklist, Tracks: List<Track>): String {
        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        for (Track in Tracks) {
            sb.append("#EXTINF:${Track.duration / 1000},${Track.Author} - ${Track.title}\n")
            sb.append("${Track.path}\n")
        }
        return sb.toString()
    }
}
