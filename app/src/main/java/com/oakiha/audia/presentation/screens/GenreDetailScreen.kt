package com.oakiha.audia.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.filled.Shuffle // Import Shuffle icon
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MediumExtendedFloatingActionButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
// Removed TopAppBar and TopAppBarDefaults as GradientTopBar will be used
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oakiha.audia.ui.theme.LocalAudioBookPlayerDarkTheme
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.oakiha.audia.data.model.Track // Import Track
import com.oakiha.audia.presentation.components.GenreGradientTopBar
// Attempt to import ExpressiveTrackListItem. If this fails, a local one will be used.
// import com.oakiha.audia.presentation.screens.ExpressiveTrackListItem // Path might vary
import com.oakiha.audia.presentation.components.MiniPlayerHeight // For MiniPlayerHeight if needed for padding
import com.oakiha.audia.presentation.components.SmartImage // For a simple track item
import com.oakiha.audia.presentation.viewmodel.GenreDetailViewModel
import com.oakiha.audia.presentation.viewmodel.GroupedTrackListItem // Import the new sealed interface
import com.oakiha.audia.presentation.viewmodel.PlayerSheetState
import com.oakiha.audia.presentation.viewmodel.PlayerViewModel // Assuming PlayerViewModel might be needed
import com.oakiha.audia.utils.formatDuration
import com.oakiha.audia.utils.hexToColor // Import hexToColor
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GenreDetailScreen(
    navController: NavHostController,
    genreId: String,
    decodedGenreId: String = java.net.URLDecoder.decode(genreId, "UTF-8"),
    playerViewModel: PlayerViewModel,
    viewModel: GenreDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val playerSheetState by playerViewModel.sheetState.collectAsState()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()

    val darkMode = LocalAudioBookPlayerDarkTheme.current

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(
        rememberTopAppBarState()
    )

    BackHandler(enabled = playerSheetState == PlayerSheetState.EXPANDED) {
        playerViewModel.collapsePlayerSheet()
    }

    val isMiniPlayerVisible = stablePlayerState.isPlaying || stablePlayerState.currentTrack != null

    val fabBottomPadding = animateDpAsState(
        targetValue = if (isMiniPlayerVisible) {
            MiniPlayerHeight + 8.dp
        } else {
            16.dp
        },
        label = "fabBottomPaddingAnimation"
    ).value

    val fabShape = AbsoluteSmoothCornerShape(
        cornerRadiusBL = 24.dp,
        smoothnessAsPercentBR = 70,
        cornerRadiusBR = 24.dp,
        smoothnessAsPercentBL = 70,
        cornerRadiusTL = 24.dp,
        smoothnessAsPercentTR = 70,
        cornerRadiusTR = 24.dp,
        smoothnessAsPercentTL = 70
    )

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            val startColor = hexToColor(
                hex = if (darkMode) uiState.genre?.darkColorHex else uiState.genre?.lightColorHex,
                defaultColor = MaterialTheme.colorScheme.surfaceVariant
            )
            val endColor = MaterialTheme.colorScheme.background

            val onColor = hexToColor(
                hex = if (darkMode) uiState.genre?.onDarkColorHex else uiState.genre?.onLightColorHex,
                defaultColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            GenreGradientTopBar(
                title = uiState.genre?.name ?: "Genre Details",
                startColor = startColor,
                endColor = endColor,
                contentColor = onColor,
                scrollBehavior = scrollBehavior,
                onNavigationIconClick = { navController.popBackStack() }
            )
        },
        floatingActionButton = {
            if (uiState.tracks.isNotEmpty()) {
                MediumExtendedFloatingActionButton(
                    modifier = Modifier
                        .padding(
                            end = 10.dp,
                            bottom = fabBottomPadding
                        ),
                    shape = fabShape,
                    onClick = {
                        if (uiState.tracks.isNotEmpty()) {
                            val randomSong = uiState.tracks.random()
                            playerViewModel.showAndPlaySong(randomSong, uiState.tracks, uiState.genre?.name ?: "Genre Shuffle")
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = "Play Random")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            if (uiState.isLoadingGenreName && uiState.genre == null) {
                ContainedLoadingIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.error != null && uiState.genre == null) {
                Text(
                    text = "Error: ${uiState.error}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            } else {
                if (uiState.isLoadingSongs) {
                    ContainedLoadingIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (uiState.tracks.isEmpty()) {
                    Text(
                        if (uiState.error != null) "Error loading tracks: ${uiState.error}" else "No tracks found for this genre.",
                        modifier = Modifier.align(Alignment.Center).padding(16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 8.dp)
                            .padding(horizontal = 16.dp)
                            .clip(
                                shape = AbsoluteSmoothCornerShape(
                                    cornerRadiusTR = 28.dp,
                                    smoothnessAsPercentTL = 60,
                                    cornerRadiusTL = 28.dp,
                                    smoothnessAsPercentTR = 60,
                                    cornerRadiusBL = 0.dp,
                                    cornerRadiusBR = 0.dp
                                )
                            )
                        ,
                        contentPadding = PaddingValues(bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        val sections = buildSections(uiState.groupedSongs)

                        items(sections, key = { it.id }) { section ->
                            when (section) {
                                is SectionData.AuthorSection -> {
                                    AuthorSectionCard(
                                        authorName = section.authorName,
                                        books = section.books,
                                        onSongClick = { track ->
                                            playerViewModel.showAndPlaySong(
                                                track,
                                                uiState.tracks,
                                                uiState.genre?.name ?: "Genre"
                                            )
                                        }
                                    )
                                }
                            }
                        }


                    }
                }
            }
        }
    }
}

// Data classes for better organization
private sealed class SectionData {
    abstract val id: String

    data class AuthorSection(
        override val id: String,
        val authorName: String,
        val books: List<BookData>
    ) : SectionData()
}

private data class BookData(
    val name: String,
    val artUri: String?,
    val tracks: List<Track>
)

// Helper function to build sections from grouped tracks
private fun buildSections(groupedSongs: List<GroupedTrackListItem>): List<SectionData> {
    val sections = mutableListOf<SectionData>()
    var currentArtist: String? = null
    var currentAlbums = mutableListOf<BookData>()
    var currentAlbumSongs = mutableListOf<Track>()
    var currentAlbumName: String? = null
    var currentBookArt: String? = null

    for (item in groupedSongs) {
        when (item) {
            is GroupedTrackListItem.AuthorHeader -> {
                // Save previous author section if exists
                if (currentArtist != null) {
                    // Save current book if exists
                    if (currentAlbumName != null && currentAlbumSongs.isNotEmpty()) {
                        currentAlbums.add(
                            BookData(currentAlbumName!!, currentBookArt, currentAlbumSongs.toList())
                        )
                    }
                    sections.add(
                        SectionData.AuthorSection(
                            id = "author_${currentArtist}",
                            authorName = currentArtist!!,
                            books = currentAlbums.toList()
                        )
                    )
                }

                // Start new author
                currentArtist = item.name
                currentAlbums.clear()
                currentAlbumSongs.clear()
                currentAlbumName = null
                currentBookArt = null
            }

            is GroupedTrackListItem.BookHeader -> {
                // Save previous book if exists
                if (currentAlbumName != null && currentAlbumSongs.isNotEmpty()) {
                    currentAlbums.add(
                        BookData(currentAlbumName!!, currentBookArt, currentAlbumSongs.toList())
                    )
                }

                // Start new book
                currentAlbumName = item.name
                currentBookArt = item.bookArtUri
                currentAlbumSongs.clear()
            }

            is GroupedTrackListItem.TrackItem -> {
                currentAlbumSongs.add(item.track)
            }
        }
    }

    // Save last author section
    if (currentArtist != null) {
        if (currentAlbumName != null && currentAlbumSongs.isNotEmpty()) {
            currentAlbums.add(
                BookData(currentAlbumName!!, currentBookArt, currentAlbumSongs.toList())
            )
        }
        sections.add(
            SectionData.AuthorSection(
                id = "author_${currentArtist}",
                authorName = currentArtist!!,
                books = currentAlbums.toList()
            )
        )
    }

    return sections
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AuthorSectionCard(
    authorName: String,
    books: List<BookData>,
    onSongClick: (Track) -> Unit
) {
    Column(
        modifier = Modifier
    ) {
        // Author Header with gradient background
        Box(
            modifier = Modifier
                .fillMaxWidth()
                //.padding(horizontal = 16.dp)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset.Infinite
                    ),
                    shape = AbsoluteSmoothCornerShape(
                        cornerRadiusTR = 28.dp,
                        smoothnessAsPercentTL = 60,
                        cornerRadiusTL = 28.dp,
                        smoothnessAsPercentTR = 60
                    )
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = authorName,
                    style = MaterialTheme.typography.bodyLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Albums section with connected background
        Card(
            modifier = Modifier
                .fillMaxWidth()
                //.padding(horizontal = 16.dp)
            ,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            shape = AbsoluteSmoothCornerShape(
                cornerRadiusBR = 28.dp,
                smoothnessAsPercentBL = 60,
                cornerRadiusBL = 28.dp,
                smoothnessAsPercentBR = 60
            ),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                books.forEach { book ->
                    AlbumSection(
                        book = book,
                        onSongClick = onSongClick
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumSection(
    book: BookData,
    onSongClick: (Track) -> Unit
) {
    Column {
        // Book Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
        ) {
            SmartImage(
                model = book.artUri,
                contentDescription = "Book art for ${book.name}",
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "ALBUM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = book.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Horizontal scrolling tracks
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            //contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(book.tracks, key = { it.id }) { track ->
                SquareSongCard(
                    track = track,
                    onClick = { onSongClick(track) }
                )
            }
        }
    }
}

@Composable
private fun SquareSongCard(
    track: Track,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(130.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp,
            pressedElevation = 6.dp
        )
    ) {
        Box {
            // Gradient background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Book Art
                Card(
                    shape = RoundedCornerShape(6.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    SmartImage(
                        model = track.bookArtUriString,
                        contentDescription = "Book art for ${track.title}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Track Info
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Duration or play indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    track.duration.let { duration ->
                        Text(
                            text = formatDuration(duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

