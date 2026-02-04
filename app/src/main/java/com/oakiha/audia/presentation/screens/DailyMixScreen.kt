package com.oakiha.audia.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import android.os.Trace // Import Trace
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.oakiha.audia.R
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.presentation.components.AiPlaylistSheet
import com.oakiha.audia.presentation.components.DailyMixMenu
import com.oakiha.audia.presentation.components.MiniPlayerHeight
import com.oakiha.audia.presentation.components.NavBarContentHeight
import com.oakiha.audia.presentation.components.PlaylistBottomSheet
import com.oakiha.audia.presentation.components.SmartImage
import com.oakiha.audia.presentation.components.TrackInfoBottomSheet
import com.oakiha.audia.presentation.components.threeShapeSwitch
import com.oakiha.audia.presentation.navigation.Screen
import com.oakiha.audia.presentation.viewmodel.MainViewModel
import com.oakiha.audia.presentation.viewmodel.PlayerSheetState
import com.oakiha.audia.presentation.viewmodel.PlayerViewModel
import com.oakiha.audia.presentation.viewmodel.PlaylistViewModel
import com.oakiha.audia.utils.formatDuration
import com.oakiha.audia.utils.shapes.RoundedStarShape
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DailyMixScreen(
    mainViewModel: MainViewModel = hiltViewModel(),
    playlistViewModel: PlaylistViewModel = hiltViewModel(),
    playerViewModel: PlayerViewModel,
    navController: NavController,
) {
    Trace.beginSection("DailyMixScreen.Composition")
    val dailyMixSongs: ImmutableList<Track> by playerViewModel.dailyMixSongs.collectAsState()
    val currentTrackId by remember { playerViewModel.stablePlayerState.map { it.currentTrack?.id }.distinctUntilChanged() }.collectAsState(initial = null)
    val isPlaying by remember { playerViewModel.stablePlayerState.map { it.isPlaying }.distinctUntilChanged() }.collectAsState(initial = false)
    val isShuffleEnabled by remember { playerViewModel.stablePlayerState.map { it.isShuffleEnabled }.distinctUntilChanged() }.collectAsState(initial = false)
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeightDp = NavBarContentHeight + systemNavBarInset
    var showPlaylistBottomSheet by remember { mutableStateOf(false) }
    val playerSheetState by playerViewModel.sheetState.collectAsState() // This is a simple enum, less critical but fine
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
    val favoriteTrackIds by playerViewModel.favoriteTrackIds.collectAsState()

    val showAiSheet by playerViewModel.showAiPlaylistSheet.collectAsState()
    val isGeneratingAiPlaylist by playerViewModel.isGeneratingAiPlaylist.collectAsState()
    val aiError by playerViewModel.aiError.collectAsState()
    val lazyListState = rememberLazyListState()

    var showSongInfoSheet by remember { mutableStateOf(false) }
    var selectedTrackForInfo by remember { mutableStateOf<Track?>(null) }
    var showDailyMixMenu by remember { mutableStateOf(false) }

    if (showDailyMixMenu) {
        DailyMixMenu(
            onDismiss = { showDailyMixMenu = false },
            onApplyPrompt = { prompt ->
                playerViewModel.regenerateDailyMixWithPrompt(prompt)
                showDailyMixMenu = false
            },
            isLoading = isGeneratingAiPlaylist
        )
    }

    if (showAiSheet) {
        AiPlaylistSheet(
            onDismiss = { playerViewModel.dismissAiPlaylistSheet() },
            onGenerateClick = { prompt, minLength, maxLength ->
                playerViewModel.generateAiPlaylist(prompt, minLength, maxLength, saveAsPlaylist = false)
            },
            isGenerating = isGeneratingAiPlaylist,
            error = aiError
        )
    }

    val surfaceContainer = MaterialTheme.colorScheme.surface
    val headerColor = MaterialTheme.colorScheme.primary
    val backgroundBrush = remember(surfaceContainer, headerColor) {
        Brush.verticalGradient(
            colors = listOf(
                headerColor.copy(alpha = 0.25f),
                surfaceContainer.copy(alpha = 0.5f),
                surfaceContainer
            ),
            endY = 1200f
        )
    }

    BackHandler(enabled = playerSheetState == PlayerSheetState.EXPANDED) {
        playerViewModel.collapsePlayerSheet()
    }

    if (showSongInfoSheet && selectedTrackForInfo != null) {
        val track = selectedTrackForInfo!!
        val removeFromListTrigger = remember(dailyMixSongs) {
            {
                playerViewModel.removeFromDailyMix(track.id)
            }
        }
        TrackInfoBottomSheet(
            track = track,
            isFavorite = favoriteTrackIds.contains(track.id),
            onToggleFavorite = { playerViewModel.toggleFavoriteSpecificSong(track) },
            onDismiss = { showSongInfoSheet = false },
            onPlaySong = {
                playerViewModel.showAndPlaySong(track, dailyMixSongs, "Daily Mix", isVoluntaryPlay = false)
                showSongInfoSheet = false
            },
            onAddToQueue = {
                playerViewModel.addSongToQueue(track)
                showSongInfoSheet = false
            },
            onAddNextToQueue = {
                playerViewModel.addSongNextToQueue(track)
                showSongInfoSheet = false
            },
            onAddToPlayList = {
                    showPlaylistBottomSheet = true;
            },
            onDeleteFromDevice = playerViewModel::deleteFromDevice,
            onNavigateToAlbum = {
                // Assuming Screen object has a method to create a route
                navController.navigate(Screen.BookDetail.createRoute(track.bookId))
                showSongInfoSheet = false
            },
            onNavigateToArtist = {
                // TODO: Implement navigation to author screen. Might require finding author by name.
                showSongInfoSheet = false
            },
            onEditSong = { newTitle, newArtist, newAlbum, newGenre, newLyrics, newTrackNumber, coverArtUpdate ->
                playerViewModel.editTrackMetadata(track, newTitle, newArtist, newAlbum, newGenre, newLyrics, newTrackNumber, coverArtUpdate)
            },
            generateAiMetadata = { fields ->
                playerViewModel.generateAiMetadata(track, fields)
            },
            removeFromListTrigger = removeFromListTrigger
        )

        if (showPlaylistBottomSheet) {
            val playlistUiState by playlistViewModel.uiState.collectAsState()

            PlaylistBottomSheet(
                playlistUiState = playlistUiState,
                track = track,
                onDismiss = { showPlaylistBottomSheet = false },
                bottomBarHeight = bottomBarHeightDp,
                playerViewModel = playerViewModel,
            )
        }
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        if (dailyMixSongs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                ContainedLoadingIndicator()
            }
        } else {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = MiniPlayerHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "daily_mix_header") {
                    ExpressiveDailyMixHeader(
                        tracks = dailyMixSongs,
                        scrollState = lazyListState,
                        onShowMenu = { playerViewModel.showAiPlaylistSheet() }
                    )
                }

                item(key = "play_shuffle_buttons") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(76.dp)
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (dailyMixSongs.isNotEmpty()) {
                                    playerViewModel.playSongs(dailyMixSongs, dailyMixSongs.first(), "Daily Mix")
                                    if (isShuffleEnabled) playerViewModel.toggleShuffle() // Desactivar shuffle si estaba activo
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(76.dp),
                            enabled = dailyMixSongs.isNotEmpty(),
                            shape = RoundedCornerShape(
                                topStart = 60.dp,
                                topEnd = 14.dp,
                                bottomStart = 60.dp,
                                bottomEnd = 14.dp
                            )
                        ) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", modifier = Modifier.size(
                                ButtonDefaults.IconSize))
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("Play it")
                        }
                        FilledTonalButton(
                            onClick = {
                                if (dailyMixSongs.isNotEmpty()) {
                                    playerViewModel.playSongsShuffled(
                                        songsToPlay = dailyMixSongs,
                                        queueName = "Daily Mix"
                                    )
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(76.dp),
                            enabled = dailyMixSongs.isNotEmpty(),
                            shape = RoundedCornerShape(
                                topStart = 14.dp,
                                topEnd = 60.dp,
                                bottomStart = 14.dp,
                                bottomEnd = 60.dp
                            )
                        ) {
                            Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", modifier = Modifier.size(
                                ButtonDefaults.IconSize))
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("Shuffle")
                        }
                    }
                }

                items(dailyMixSongs, key = { it.id }) { track ->
                    EnhancedTrackListItem(
                        modifier = Modifier
                            .padding(horizontal = 16.dp),
                        track = track,
                        isCurrentSong = stablePlayerState.currentTrack?.id == track.id,
                        isPlaying = currentTrackId == track.id && isPlaying,
                        onClick = { playerViewModel.showAndPlaySong(track, dailyMixSongs, "Daily Mix", isVoluntaryPlay = false) },
                        onMoreOptionsClick = {
                            selectedTrackForInfo = it
                            showSongInfoSheet = true
                        }
                    )
                }
            }
        }

        FilledIconButton(
            onClick = { navController.popBackStack() },
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 10.dp, top = 8.dp)
                .clip(CircleShape)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back"
            )
        }

        // Bottom Gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .height(80.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surfaceContainerLowest.copy(0.5f),
                            MaterialTheme.colorScheme.surfaceContainerLowest
                        )
                    )
                )
        ) {

        }

        //Top Gradient
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .height(50.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceContainerLowest.copy(0.5f),
                            Color.Transparent,
                        )
                    )
                )
        ) {

        }
    }
    Trace.endSection()
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExpressiveDailyMixHeader(
    tracks: List<Track>,
    scrollState: LazyListState,
    onShowMenu: () -> Unit
) {
    Trace.beginSection("ExpressiveDailyMixHeader.Composition")
    val bookArts = remember(tracks) { tracks.map { it.bookArtUriString }.distinct().take(3) }
    val totalDuration = remember(tracks) { tracks.sumOf { it.duration } }

    val parallaxOffset by remember { derivedStateOf { if (scrollState.firstVisibleItemIndex == 0) scrollState.firstVisibleItemScrollOffset * 0.5f else 0f } }

    val headerAlpha by remember {
        derivedStateOf {
            (1f - (scrollState.firstVisibleItemScrollOffset / 600f)).coerceIn(0f, 1f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .graphicsLayer {
                translationY = parallaxOffset
                alpha = headerAlpha
            }
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Row(
                horizontalArrangement = Arrangement.spacedBy((-80).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                bookArts.forEachIndexed { index, artUrl ->
                    val size = when (index) {
                        0 -> 180.dp
                        1 -> 220.dp
                        2 -> 180.dp
                        else -> 150.dp
                    }
                    val rotation = when (index) {
                        0 -> -15f
                        1 -> 0f
                        2 -> 15f
                        else -> 0f
                    }
                    val shape = threeShapeSwitch(index, thirdShapeCornerRadius = 30.dp)

                    // --- INICIO DE LA CORRECCIÃƒÆ’Ã¢â‚¬Å“N ---
                    if (index == 2) {
                        // Para la 3ra imagen, usamos Modifier.layout para controlar la mediciÃƒÆ’Ã‚Â³n y el posicionamiento.
                        Box(
                            modifier = Modifier.layout { measurable, constraints ->
                                // 1. Medimos el contenido (la imagen) para que sea un cuadrado perfecto de `size` x `size`,
                                // ignorando las restricciones de ancho que puedan venir del padre (el Row).
                                val placeable = measurable.measure(
                                    Constraints.fixed(width = size.roundToPx(), height = size.roundToPx())
                                )

                                // 2. Le decimos al Row que nuestro layout ocuparÃƒÆ’Ã‚Â¡ el ancho que ÃƒÆ’Ã‚Â©l nos dio (`constraints.maxWidth`),
                                // de esta forma no empujamos a los otros elementos. La altura serÃƒÆ’Ã‚Â¡ la de nuestro cuadrado.
                                layout(constraints.maxWidth, placeable.height) {
                                    // 3. Colocamos nuestro contenido cuadrado (`placeable`) dentro del espacio asignado.
                                    // Lo centramos horizontalmente para que se desborde por ambos lados si es necesario.
                                    val xOffset = (constraints.maxWidth - placeable.width) / 2
                                    placeable.placeRelative(xOffset, 0)
                                }
                            }
                        ) {
                            // Este es el contenido que se mide y se dibuja.
                            Box(
                                modifier = Modifier
                                    .graphicsLayer { rotationZ = rotation }
                                    .clip(shape)
                            ) {
                                SmartImage(
                                    model = artUrl ?: R.drawable.rounded_book_24,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize() // Llena el tamaÃƒÆ’Ã‚Â±o cuadrado que le dimos.
                                )
                            }
                        }
                    } else {
                        // LÃƒÆ’Ã‚Â³gica original para las otras dos imÃƒÆ’Ã‚Â¡genes
                        Box(
                            modifier = Modifier
                                .size(size)
                                .graphicsLayer { rotationZ = rotation }
                                .clip(shape)
                        ) {
                            SmartImage(
                                model = artUrl ?: R.drawable.rounded_book_24,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    // --- FIN DE LA CORRECCIÃƒÆ’Ã¢â‚¬Å“N ---
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.1f),
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            MaterialTheme.colorScheme.surface
                        ),
                        startY = 0f,
                        endY = 900f
                    )
                )
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.Absolute.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .padding(start = 6.dp),
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "Daily Mix", style = MaterialTheme.typography.headlineLarge.copy(letterSpacing = (-1.2).sp, lineHeight = 38.sp),
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${tracks.size} Songs â€¢ ${formatDuration(totalDuration)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }
            LargeExtendedFloatingActionButton(
                modifier = Modifier,
                onClick = onShowMenu,
                shape = RoundedStarShape(
                    sides = 8,
                    curve = 0.05,
                    rotation = 0f
                )
            ) {
                Icon(
                    modifier = Modifier.size(20.dp),
                    painter = painterResource(R.drawable.gemini_ai),
                    contentDescription = "Play"
                )
            }
        }
    }
    Trace.endSection()
}