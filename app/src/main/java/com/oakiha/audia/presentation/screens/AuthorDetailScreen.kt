@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.oakiha.audia.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SurroundSound
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.oakiha.audia.ui.theme.LocalAudioBookPlayerDarkTheme
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.oakiha.audia.data.model.Author
import com.oakiha.audia.presentation.components.MiniPlayerHeight
import com.oakiha.audia.presentation.components.NavBarContentHeight
import com.oakiha.audia.presentation.components.PlaylistBottomSheet
import com.oakiha.audia.presentation.components.TrackInfoBottomSheet
import com.oakiha.audia.presentation.navigation.Screen
import com.oakiha.audia.presentation.viewmodel.AuthorDetailViewModel
import com.oakiha.audia.presentation.viewmodel.AuthorBookSection
import com.oakiha.audia.presentation.viewmodel.PlayerViewModel
import com.oakiha.audia.presentation.viewmodel.PlayerSheetState
import com.oakiha.audia.presentation.viewmodel.PlaylistViewModel
import com.oakiha.audia.utils.shapes.RoundedStarShape
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AuthorDetailScreen(
    authorId: String,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    viewModel: AuthorDetailViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
    val playerSheetState by playerViewModel.sheetState.collectAsState()
    val lazyListState = rememberLazyListState()
    val favoriteIds by playerViewModel.favoriteTrackIds.collectAsState()
    var showTrackInfoBottomSheet by remember { mutableStateOf(false) }
    val selectedTrackForInfo by playerViewModel.selectedTrackForInfo.collectAsState()
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeightDp = NavBarContentHeight + systemNavBarInset
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        playerViewModel.collapsePlayerSheet()
    }

    // --- LÃ³gica del Header Colapsable ---
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val minTopBarHeight = 64.dp + statusBarHeight
    val maxTopBarHeight = 300.dp

    val minTopBarHeightPx = with(density) { minTopBarHeight.toPx() }
    val maxTopBarHeightPx = with(density) { maxTopBarHeight.toPx() }

    val topBarHeight = remember { Animatable(maxTopBarHeightPx) }
    var collapseFraction by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(topBarHeight.value) {
        collapseFraction = 1f - ((topBarHeight.value - minTopBarHeightPx) / (maxTopBarHeightPx - minTopBarHeightPx)).coerceIn(0f, 1f)
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                val isScrollingDown = delta < 0

                // Si estamos scrolleando hacia arriba y no estamos en el tope de la lista,
                // el scroll es para la lista, no para la TopBar.
                if (!isScrollingDown && (lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 0)) {
                    return Offset.Zero
                }

                val previousHeight = topBarHeight.value
                val newHeight = (previousHeight + delta).coerceIn(minTopBarHeightPx, maxTopBarHeightPx)
                val consumed = newHeight - previousHeight

                if (consumed.roundToInt() != 0) {
                    coroutineScope.launch {
                        topBarHeight.snapTo(newHeight)
                    }
                }

                // Si estamos en el tope y scrolleamos hacia arriba, la lista no debe moverse.
                val canConsumeScroll = !(isScrollingDown && newHeight == minTopBarHeightPx)
                return if (canConsumeScroll) Offset(0f, consumed) else Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                return super.onPostFling(consumed, available)
            }
        }
    }

    LaunchedEffect(lazyListState.isScrollInProgress) {
        if (!lazyListState.isScrollInProgress) {
            val shouldExpand = topBarHeight.value > (minTopBarHeightPx + maxTopBarHeightPx) / 2
            val canExpand = lazyListState.firstVisibleItemIndex == 0 && lazyListState.firstVisibleItemScrollOffset == 0

            val targetValue = if (shouldExpand && canExpand) {
                maxTopBarHeightPx
            } else {
                minTopBarHeightPx
            }

            if (topBarHeight.value != targetValue) {
                coroutineScope.launch {
                    topBarHeight.animateTo(targetValue, spring(stiffness = Spring.StiffnessMedium))
                }
            }
        }
    }
    // --- Fin de la lÃ³gica del Header ---

    BackHandler(enabled = playerSheetState == PlayerSheetState.EXPANDED) {
        playerViewModel.collapsePlayerSheet()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface // Asegura que el fondo coincida con el gradiente
    ) {
        Box(modifier = Modifier.nestedScroll(nestedScrollConnection)) {
            when {
                uiState.isLoading && uiState.author == null -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ContainedLoadingIndicator()
                    }
                }
                uiState.error != null && uiState.author == null -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
                uiState.author != null -> {
                    val author = uiState.author!!
                    val songs = uiState.tracks
                    val currentTopBarHeightDp = with(density) { topBarHeight.value.toDp() }

                    val bookSections = uiState.bookSections
                    LazyColumn(
                        state = lazyListState,
                        contentPadding = PaddingValues(
                            top = currentTopBarHeightDp,
                            bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp
                        ),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 0.dp)
                    ) {
                        bookSections.forEachIndexed { index, section ->
                            if (section.tracks.isEmpty()) return@forEachIndexed

                            stickyHeader(key = "artist_album_${section.bookId}_${section.title}_header") {
                                AlbumSectionHeader(
                                    section = section,
                                    onPlayAlbum = {
                                        section.tracks.firstOrNull()?.let { firstSong ->
                                            playerViewModel.showAndPlaySong(firstSong, section.tracks)
                                        }
                                    }
                                )
                            }

                            item(key = "artist_album_${section.bookId}_${section.title}_header_spacing") {
                                Spacer(modifier = Modifier.height(12.dp))
                            }

                            itemsIndexed(
                                items = section.tracks,
                                key = { _, song -> "artist_album_${section.bookId}_song_${song.id}" }
                            ) { songIndex, song ->
                                EnhancedTrackListItem(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    song = song,
                                    isCurrentSong = stablePlayerState.currentTrack?.id == song.id,
                                    isPlaying = stablePlayerState.isPlaying,
                                    onMoreOptionsClick = {
                                        playerViewModel.selectSongForInfo(song)
                                        showTrackInfoBottomSheet = true
                                    },
                                    onClick = { playerViewModel.showAndPlaySong(song, section.tracks) }
                                )

                                if (songIndex != section.tracks.lastIndex) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }

                            item(key = "artist_album_${section.bookId}_${section.title}_footer_spacing") {
                                Spacer(
                                    modifier = Modifier.height(
                                        if (index == bookSections.lastIndex) 24.dp else 20.dp
                                    )
                                )
                            }
                        }


                    }

                    CustomCollapsingTopBar(
                        artist = artist,
                        songsCount = songs.size,
                        collapseFraction = collapseFraction,
                        headerHeight = currentTopBarHeightDp,
                        onBackPressed = { navController.popBackStack() },
                        onPlayClick = {
                            if (songs.isNotEmpty()) {
                                val randomSong = songs.random()
                                playerViewModel.showAndPlaySong(randomSong, songs) }
                        }
                    )
                }
            }
        }
    }
    if (showTrackInfoBottomSheet && selectedTrackForInfo != null) {
        val currentTrack = selectedTrackForInfo
        val isFavorite = remember(currentTrack?.id, favoriteIds) {
            derivedStateOf { currentTrack?.let { favoriteIds.contains(it.id) } }
        }.value ?: false

        if (currentTrack != null) {
            val removeFromListTrigger = remember(uiState.tracks) {
                {
                    viewModel.removeTrackFromBookSection(currentTrack.id)
                }
            }
            TrackInfoBottomSheet(
                song = currentTrack,
                isFavorite = isFavorite,
                onToggleFavorite = {
                    playerViewModel.toggleFavoriteSpecificSong(currentTrack)
                },
                onDismiss = { showTrackInfoBottomSheet = false },
                onPlaySong = {
                    playerViewModel.showAndPlaySong(currentTrack)
                    showTrackInfoBottomSheet = false
                },
                onAddToQueue = {
                    playerViewModel.addSongToQueue(currentTrack)
                    showTrackInfoBottomSheet = false
                },
                onAddNextToQueue = {
                    playerViewModel.addSongNextToQueue(currentTrack)
                    showTrackInfoBottomSheet = false
                },
                onAddToPlayList = {
                    showPlaylistBottomSheet = true;
                },
                onDeleteFromDevice = playerViewModel::deleteFromDevice,
                onNavigateToAlbum = {
                    navController.navigate(Screen.BookDetail.createRoute(currentTrack.bookId))
                    showTrackInfoBottomSheet = false
                },
                onNavigateToArtist = {
                    navController.navigate(Screen.AuthorDetail.createRoute(currentTrack.authorId))
                    showTrackInfoBottomSheet = false
                },
                onEditSong = { newTitle, newArtist, newAlbum, newGenre, newLyrics, newTrackNumber, coverArtUpdate ->
                    playerViewModel.editSongMetadata(currentTrack, newTitle, newArtist, newAlbum, newGenre, newLyrics, newTrackNumber, coverArtUpdate)
                },
                generateAiMetadata = { fields ->
                    playerViewModel.generateAiMetadata(currentTrack, fields)
                },
                removeFromListTrigger = removeFromListTrigger
            )
            if (showPlaylistBottomSheet) {
                val playlistUiState by playlistViewModel.uiState.collectAsState()

                PlaylistBottomSheet(
                    playlistUiState = playlistUiState,
                    song = currentTrack,
                    onDismiss = { showPlaylistBottomSheet = false },
                    bottomBarHeight = bottomBarHeightDp,
                    playerViewModel = playerViewModel,
                )
            }
        }
    }
}

@Composable
private fun AlbumSectionHeader(
    section: AuthorBookSection,
    modifier: Modifier = Modifier,
    onPlayAlbum: () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color.Transparent,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                val subtitle = buildString {
                    section.year?.takeIf { it > 0 }?.let {
                        append(it.toString())
                        append(" â€¢ ")
                    }
                    append("${section.tracks.size} songs")
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FilledIconButton(
                onClick = onPlayAlbum,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play ${section.title}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CustomCollapsingTopBar(
    artist: Author,
    songsCount: Int,
    collapseFraction: Float, // 0.0 = expandido, 1.0 = colapsado
    headerHeight: Dp,
    onBackPressed: () -> Unit,
    onPlayClick: () -> Unit
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val statusBarColor = if (LocalAudioBookPlayerDarkTheme.current) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.4f)

    // --- Animation Values ---
    val fabScale = 1f - collapseFraction
    val backgroundAlpha = collapseFraction
    val headerContentAlpha = 1f - (collapseFraction * 2).coerceAtMost(1f)

    // Title animation
    val titleScale = lerp(1f, 0.75f, collapseFraction)
    val titlePaddingStart = lerp(24.dp, 58.dp, collapseFraction)
    val titleMaxLines = if(collapseFraction < 0.5f) 2 else 1
    val titleVerticalBias = lerp(1f, -1f, collapseFraction)
    val animatedTitleAlignment = BiasAlignment(horizontalBias = -1f, verticalBias = titleVerticalBias)
    val titleContainerHeight = lerp(88.dp, 56.dp, collapseFraction)
    val yOffsetCorrection = lerp( (titleContainerHeight / 2) - 64.dp, 0.dp, collapseFraction)


    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .background(surfaceColor.copy(alpha = backgroundAlpha))
    ) {
        // --- Contenido del Header (visible cuando estÃ¡ expandido) ---
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = headerContentAlpha }
        ) {
            // Artist artwork or fallback pattern
            if (!artist.imageUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(artist.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = artist.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AudiobookIconPattern(
                    modifier = Modifier.fillMaxSize(),
                    collapseFraction = collapseFraction
                )
            }

            // Gradient overlay for text readability
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.3f to Color.Transparent,
                                1f to MaterialTheme.colorScheme.surface
                            )
                        )
                    )
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(Brush.verticalGradient(colors = listOf(statusBarColor, Color.Transparent)))
                .align(Alignment.TopCenter)
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            FilledIconButton(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 4.dp),
                onClick = onBackPressed,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }

            // Box contenedor para el tÃ­tulo
            Box(
                modifier = Modifier
                    .align(animatedTitleAlignment)
                    .height(titleContainerHeight)
                    .fillMaxWidth()
                    .offset(y = yOffsetCorrection)
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = titlePaddingStart, end = 120.dp)
                        .graphicsLayer {
                            scaleX = titleScale
                            scaleY = titleScale
                        },
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 26.sp,
                            textGeometricTransform = TextGeometricTransform(scaleX = 1.2f),
                        ),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = titleMaxLines,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = "$songsCount songs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // BotÃ³n de Play
            LargeExtendedFloatingActionButton(
                onClick = onPlayClick,
                shape = RoundedStarShape(sides = 8, curve = 0.05, rotation = 0f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .graphicsLayer {
                        scaleX = fabScale
                        scaleY = fabScale
                        alpha = fabScale
                    }
            ) {
                Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle play book")
            }
        }
    }
}

@Composable
private fun AudiobookIconPattern(modifier: Modifier = Modifier, collapseFraction: Float) {
    val color1 = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
    val color2 = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)

    Box(modifier = modifier.background(MaterialTheme.colorScheme.primaryContainer)) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null, tint = color1,
            modifier = Modifier.align(Alignment.TopStart).offset(x = lerp(60.dp, 100.dp, collapseFraction), y = lerp(100.dp, 10.dp, collapseFraction)).size(60.dp).graphicsLayer { rotationZ = lerp(-15f, 30f, collapseFraction); scaleX = 1f - collapseFraction; scaleY = 1f - collapseFraction }
        )
        Icon(
            imageVector = Icons.Default.GraphicEq,
            contentDescription = null, tint = color1,
            modifier = Modifier.align(Alignment.CenterStart).offset(x = lerp(20.dp,
                (-40).dp, collapseFraction), y = lerp(50.dp, 90.dp, collapseFraction)).size(50.dp).graphicsLayer { rotationZ = lerp(5f, 45f, collapseFraction); scaleX = 1f - collapseFraction; scaleY = 1f - collapseFraction }
        )
        Icon(
            imageVector = Icons.Rounded.Album,
            contentDescription = null, tint = color2,
            modifier = Modifier.align(Alignment.CenterEnd).offset(x = lerp((-40).dp, 20.dp, collapseFraction), y = lerp(-50.dp, -90.dp, collapseFraction)).size(70.dp).graphicsLayer { rotationZ = lerp(20f, -10f, collapseFraction); scaleX = 1f - collapseFraction; scaleY = 1f - collapseFraction }
        )
        Icon(
            imageVector = Icons.Rounded.Mic,
            contentDescription = null, tint = color1,
            modifier = Modifier.align(Alignment.BottomCenter).offset(x = lerp(20.dp, 10.dp, collapseFraction),y = lerp((-40).dp, 20.dp, collapseFraction)).size(60.dp).graphicsLayer { rotationZ = lerp(-5f, 35f, collapseFraction); scaleX = 1f - collapseFraction; scaleY = 1f - collapseFraction }
        )
        Icon(
            imageVector = Icons.Rounded.SurroundSound,
            contentDescription = null, tint = color2,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = lerp(60.dp, 10.dp, collapseFraction), x = lerp(0.dp, -50.dp, collapseFraction)).size(80.dp).graphicsLayer { rotationZ = lerp(-10f, 20f, collapseFraction); scaleX = 1f - collapseFraction; scaleY = 1f - collapseFraction }
        )
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null, tint = color1,
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = lerp((-30).dp, (-10).dp, collapseFraction), y = lerp(-120.dp, -150.dp, collapseFraction)).size(45.dp).graphicsLayer { rotationZ = lerp(15f, -30f, collapseFraction); scaleX = 1f - collapseFraction; scaleY = 1f - collapseFraction }
        )
        Icon(
            imageVector = Icons.Rounded.Headphones,
            contentDescription = null, tint = color2,
            modifier = Modifier.align(Alignment.Center).offset(x = lerp(60.dp, 80.dp, collapseFraction), y = lerp(20.dp, 60.dp, collapseFraction)).size(45.dp).graphicsLayer { rotationZ = lerp(-25f, 15f, collapseFraction); scaleX = 1f - collapseFraction; scaleY = 1f - collapseFraction }
        )
    }
}
