package com.oakiha.audia.data.playlist

import android.content.Context
import android.net.Uri
import com.oakiha.audia.data.model.Playlist
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
    private val audiobookRepository: AudiobookRepository
) {

    suspend fun parseM3u(uri: Uri): Pair<String, List<String>> {
        val trackIds = mutableListOf<String>()
        var playlistName = "Imported Playlist"

        // Pre-load all tracks once for efficient lookup (fixes performance issue with large M3U files)
        val allTracks = audiobookRepository.getTracks().first()
        
        // Build lookup maps for fast matching
        val tracksByPath = allTracks.associateBy { it.path }
        val tracksByFileName = allTracks.groupBy { it.path.substringAfterLast("/") }
        val tracksByContentUriFileName = allTracks.groupBy { it.contentUriString.substringAfterLast("/") }

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
                    // We need to find a track in our database that matches this path
                    
                    // First try exact path match from pre-loaded map
                    val trackByPath = tracksByPath[trimmedLine]
                    if (trackByPath != null) {
                        trackIds.add(trackByPath.id)
                    } else {
                        // Try to match by filename if path doesn't match exactly
                        val fileName = trimmedLine.substringAfterLast("/")
                        val matchedTrack = tracksByFileName[fileName]?.firstOrNull()
                            ?: tracksByContentUriFileName[fileName]?.firstOrNull()
                        if (matchedTrack != null) {
                            trackIds.add(matchedTrack.id)
                        }
                    }
                }
            }
        }

        // Try to get the filename as playlist name
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1 && cursor.moveToFirst()) {
                playlistName = cursor.getString(nameIndex).removeSuffix(".m3u").removeSuffix(".m3u8")
            }
        }

        return Pair(playlistName, trackIds)
    }

    fun generateM3u(playlist: Playlist, tracks: List<Track>): String {
        val sb = StringBuilder()
        sb.append("#EXTM3U\n")
        for (track in tracks) {
            sb.append("#EXTINF:${track.duration / 1000},${track.authorName} - ${track.title}\n")
            sb.append("${track.path}\n")
        }
        return sb.toString()
    }
}

