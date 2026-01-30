package com.oakiha.audia.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oakiha.audia.R
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.presentation.components.MiniPlayerHeight
import com.oakiha.audia.presentation.viewmodel.PlayerViewModel
import com.oakiha.audia.presentation.viewmodel.StablePlayerState
import kotlinx.collections.immutable.ImmutableList

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryTracksTab(
    Tracks: ImmutableList<Track>,
    isLoading: Boolean,
    stablePlayerState: StablePlayerState,
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    onMoreOptionsClick: (Track) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()

    // Handle different loading states
    when {
        isLoading && Tracks.isEmpty() -> {
            // Initial loading - show skeleton placeholders
            LazyColumn(
                modifier = Modifier
                    .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 26.dp,
                            topEnd = 26.dp,
                            bottomStart = PlayerSheetCollapsedCornerRadius,
                            bottomEnd = PlayerSheetCollapsedCornerRadius
                        )
                    )
                    .fillMaxSize(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap)
            ) {
                items(12) { // Show 12 skeleton items
                    EnhancedTrackListItem(
                        Track = Track.emptyTrack(),
                        isPlaying = false,
                        isLoading = true,
                        isCurrentTrack = false,
                        onMoreOptionsClick = {},
                        onClick = {}
                    )
                }
            }
        }
        !isLoading && Tracks.isEmpty() -> {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_Audiobook_off_24),
                        contentDescription = "No Tracks found",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("No Tracks found in your library.", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Try rescanning your library in settings if you have Audiobook on your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        else -> {
            // Tracks loaded
            Box(modifier = Modifier.fillMaxSize()) {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    state = pullToRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = pullToRefreshState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 26.dp,
                                    topEnd = 26.dp,
                                    bottomStart = PlayerSheetCollapsedCornerRadius,
                                    bottomEnd = PlayerSheetCollapsedCornerRadius
                                )
                            ),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + 30.dp)
                    ) {
                        item(key = "Tracks_top_spacer") { Spacer(Modifier.height(0.dp)) }

                        items(
                            items = Tracks,
                            key = { "Track_${it.id}" },
                            contentType = { "Track" }
                        ) { Track ->
                            val isPlayingThisTrack = Track.id == stablePlayerState.currentTrack?.id && stablePlayerState.isPlaying
                            
                            val rememberedOnMoreOptionsClick: (Track) -> Unit = remember(onMoreOptionsClick) {
                                { TrackFromListItem -> onMoreOptionsClick(TrackFromListItem) }
                            }
                            val rememberedOnClick: () -> Unit = remember(Track) {
                                { 
                                  // Play using showAndPlayTrack but passing the SORTED list as queue
                                  // Important: We should pass 'Tracks' as the queue context
                                  // But showAndPlayTrack might expect paginated logic in VM?
                                  // PlayerViewModel logic: showAndPlayTrack(Track, queue, name).
                                  // Usually calls playTracks(queue, Track). If we pass 'Tracks', it plays in sorted order!
                                  playerViewModel.showAndPlayTrack(Track, Tracks, "Library") 
                                }
                            }

                            EnhancedTrackListItem(
                                Track = Track,
                                isPlaying = isPlayingThisTrack,
                                isCurrentTrack = stablePlayerState.currentTrack?.id == Track.id,
                                isLoading = false,
                                onMoreOptionsClick = rememberedOnMoreOptionsClick,
                                onClick = rememberedOnClick
                            )
                        }
                    }
                }
                // Top gradient fade effect
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface, Color.Transparent
                                )
                            )
                        )
                )
            }
        }
    }
}
