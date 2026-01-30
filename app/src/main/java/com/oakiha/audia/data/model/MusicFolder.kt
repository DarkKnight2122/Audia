package com.oakiha.audia.data.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class AudiobookFolder(
    val path: String,
    val name: String,
    val Tracks: ImmutableList<Track> = persistentListOf(),
    val subFolders: ImmutableList<AudiobookFolder> = persistentListOf()
) {
    val totalTrackCount: Int by lazy {
        Tracks.size + subFolders.sumOf { it.totalTrackCount }
    }
}
