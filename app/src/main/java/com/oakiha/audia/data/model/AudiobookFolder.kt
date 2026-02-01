package com.oakiha.audia.data.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class AudiobookFolder(
    val path: String,
    val name: String,
    val songs: ImmutableList<Track> = persistentListOf(),
    val subFolders: ImmutableList<AudiobookFolder> = persistentListOf()
) {
    val totalTrackCount: Int by lazy {
        songs.size + subFolders.sumOf { it.totalTrackCount }
    }
}

