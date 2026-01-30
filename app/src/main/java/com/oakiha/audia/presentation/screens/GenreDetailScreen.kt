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
import com.oakiha.audia.presentation.components.CategoryGradientTopBar
// Attempt to import ExpressiveTrackListItem. If this fails, a local one will be used.
// import com.oakiha.audia.presentation.screens.ExpressiveTrackListItem // Path might vary
import com.oakiha.audia.presentation.components.MiniPlayerHeight // For MiniPlayerHeight if needed for padding
import com.oakiha.audia.presentation.components.SmartImage // For a simple Track item
import com.oakiha.audia.presentation.viewmodel.CategoryDetailViewModel
import com.oakiha.audia.presentation.viewmodel.GroupedTrackListItem // Import the new sealed interface
import com.oakiha.audia.presentation.viewmodel.PlayerSheetState
import com.oakiha.audia.presentation.viewmodel.PlayerViewModel // Assuming PlayerViewModel might be needed
import com.oakiha.audia.utils.formatDuration
import com.oakiha.audia.utils.hexToColor // Import hexToColor
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CategoryDetailScreen(
    navController: NavHostController,
    CategoryId: String,
    decodedCategoryId: String = java.net.URLDecoder.decode(CategoryId, "UTF-8"),
    playerViewModel: PlayerViewModel,
    viewModel: CategoryDetailViewModel = hiltViewModel()
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
                hex = if (darkMode) uiState.Category?.darkColorHex else uiState.Category?.lightColorHex,
                defaultColor = MaterialTheme.colorScheme.surfaceVariant
            )
            val endColor = MaterialTheme.colorScheme.background

            val onColor = hexToColor(
                hex = if (darkMode) uiState.Category?.onDarkColorHex else uiState.Category?.onLightColorHex,
                defaultColor = MaterialTheme.colorScheme.onSurfaceVariant
            )

            CategoryGradientTopBar(
                title = uiState.Category?.name ?: "Category Details",
                startColor = startColor,
                endColor = endColor,
                contentColor = onColor,
                scrollBehavior = scrollBehavior,
                onNavigationIconClick = { navController.popBackStack() }
            )
        },
        floatingActionButton = {
            if (uiState.Tracks.isNotEmpty()) {
                MediumExtendedFloatingActionButton(
                    modifier = Modifier
                        .padding(
                            end = 10.dp,
                            bottom = fabBottomPadding
                        ),
                    shape = fabShape,
                    onClick = {
                        if (uiState.Tracks.isNotEmpty()) {
                            val randomTrack = uiState.Tracks.random()
                            playerViewModel.showAndPlayTrack(randomTrack, uiState.Tracks, uiState.Category?.name ?: "Category Shuffle")
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
            if (uiState.isLoadingCategoryName && uiState.Category == null) {
                ContainedLoadingIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.error != null && uiState.Category == null) {
                Text(
                    text = "Error: ${uiState.error}",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(16.dp)
                )
            } else {
                if (uiState.isLoadingTracks) {
                    ContainedLoadingIndicator(modifier = Modifier.align(Alignment.Center))
                } else if (uiState.Tracks.isEmpty()) {
                    Text(
                        if (uiState.error != null) "Error loading Tracks: ${uiState.error}" else "No Tracks found for this Category.",
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
                        val sections = buildSections(uiState.groupedTracks)

                        items(sections, key = { it.id }) { section ->
                            when (section) {
                                is SectionData.Authorsection -> {
                                    AuthorsectionCard(
                                        AuthorName = section.AuthorName,
                                        Books = section.Books,
                                        onTrackClick = { Track ->
                                            playerViewModel.showAndPlayTrack(
                                                Track,
                                                uiState.Tracks,
                                                uiState.Category?.name ?: "Category"
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

    data class Authorsection(
        override val id: String,
        val AuthorName: String,
        val Books: List<BookData>
    ) : SectionData()
}

private data class BookData(
    val name: String,
    val artUri: String?,
    val Tracks: List<Track>
)

// Helper function to build sections from grouped Tracks
private fun buildSections(groupedTracks: List<GroupedTrackListItem>): List<SectionData> {
    val sections = mutableListOf<SectionData>()
    var currentAuthor: String? = null
    var currentBooks = mutableListOf<BookData>()
    var currentBookTracks = mutableListOf<Track>()
    var currentBookName: String? = null
    var currentBookArt: String? = null

    for (item in groupedTracks) {
        when (item) {
            is GroupedTrackListItem.AuthorHeader -> {
                // Save previous Author section if exists
                if (currentAuthor != null) {
                    // Save current Book if exists
                    if (currentBookName != null && currentBookTracks.isNotEmpty()) {
                        currentBooks.add(
                            BookData(currentBookName!!, currentBookArt, currentBookTracks.toList())
                        )
                    }
                    sections.add(
                        SectionData.Authorsection(
                            id = "Author_${currentAuthor}",
                            AuthorName = currentAuthor!!,
                            Books = currentBooks.toList()
                        )
                    )
                }

                // Start new Author
                currentAuthor = item.name
                currentBooks.clear()
                currentBookTracks.clear()
                currentBookName = null
                currentBookArt = null
            }

            is GroupedTrackListItem.BookHeader -> {
                // Save previous Book if exists
                if (currentBookName != null && currentBookTracks.isNotEmpty()) {
                    currentBooks.add(
                        BookData(currentBookName!!, currentBookArt, currentBookTracks.toList())
                    )
                }

                // Start new Book
                currentBookName = item.name
                currentBookArt = item.BookArtUri
                currentBookTracks.clear()
            }

            is GroupedTrackListItem.TrackItem -> {
                currentBookTracks.add(item.Track)
            }
        }
    }

    // Save last Author section
    if (currentAuthor != null) {
        if (currentBookName != null && currentBookTracks.isNotEmpty()) {
            currentBooks.add(
                BookData(currentBookName!!, currentBookArt, currentBookTracks.toList())
            )
        }
        sections.add(
            SectionData.Authorsection(
                id = "Author_${currentAuthor}",
                AuthorName = currentAuthor!!,
                Books = currentBooks.toList()
            )
        )
    }

    return sections
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AuthorsectionCard(
    AuthorName: String,
    Books: List<BookData>,
    onTrackClick: (Track) -> Unit
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
                    text = AuthorName,
                    style = MaterialTheme.typography.bodyLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Books section with connected background
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
                Books.forEach { Book ->
                    Booksection(
                        Book = Book,
                        onTrackClick = onTrackClick
                    )
                }
            }
        }
    }
}

@Composable
private fun Booksection(
    Book: BookData,
    onTrackClick: (Track) -> Unit
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
                model = Book.artUri,
                contentDescription = "Book art for ${Book.name}",
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = "Book",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = Book.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Horizontal scrolling Tracks
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            //contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(Book.Tracks, key = { it.id }) { Track ->
                SquareTrackCard(
                    Track = Track,
                    onClick = { onTrackClick(Track) }
                )
            }
        }
    }
}

@Composable
private fun SquareTrackCard(
    Track: Track,
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
                        model = Track.BookArtUriString,
                        contentDescription = "Book art for ${Track.title}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Track Info
                Text(
                    text = Track.title,
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
                    Track.duration.let { duration ->
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
