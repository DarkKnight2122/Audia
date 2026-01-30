package com.oakiha.audia.data.model

import androidx.compose.runtime.Immutable

@Immutable
enum class SearchFilterType {
    ALL,
    SONGS,
    ALBUMS,
    ARTISTS,
    PLAYLISTS
}
