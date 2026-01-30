package com.oakiha.audia.presentation.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.oakiha.audia.data.model.Book
import com.oakiha.audia.data.model.Author
import com.oakiha.audia.data.model.Booklist
import com.oakiha.audia.data.model.SearchFilterType
import com.oakiha.audia.data.model.SearchHistoryItem
import com.oakiha.audia.data.model.SearchResultItem
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.presentation.components.SmartImage
import com.oakiha.audia.presentation.components.TrackInfoBottomSheet
import com.oakiha.audia.presentation.viewmodel.PlayerViewModel
import android.util.Log
import com.oakiha.audia.ui.theme.LocalAudioBookPlayerDarkTheme
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.BooklistPlay
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
// import androidx.compose.runtime.derivedStateOf // Already imported
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import com.oakiha.audia.R
import com.oakiha.audia.data.repository.AudiobookRepository
import com.oakiha.audia.presentation.components.MiniPlayerHeight
import com.oakiha.audia.presentation.components.NavBarContentHeight
import com.oakiha.audia.presentation.components.BooklistBottomSheet
import com.oakiha.audia.presentation.navigation.Screen // Required for Screen.CategoryDetail.createRoute
import com.oakiha.audia.presentation.screens.search.components.CategoryCategoriesGrid
import com.oakiha.audia.presentation.viewmodel.BooklistViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import timber.log.Timber


@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SearchScreen(
    paddingValues: PaddingValues,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    BooklistViewModel: BooklistViewModel = hiltViewModel(),
    navController: NavHostController,
    onSearchBarActiveChange: (Boolean) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(false) }
    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeightDp = NavBarContentHeight + systemNavBarInset
    var showBooklistBottomSheet by remember { mutableStateOf(false) }
    val uiState by playerViewModel.playerUiState.collectAsState()
    val currentFilter by remember { derivedStateOf { uiState.selectedSearchFilter } }
    val searchHistory = uiState.searchHistory
    val Categories by playerViewModel.Categories.collectAsState()
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
    val favoriteTrackIds by playerViewModel.favoriteTrackIds.collectAsState()
    var showTrackInfoBottomSheet by remember { mutableStateOf(false) }
    var selectedTrackForInfo by remember { mutableStateOf<Track?>(null) }

    // Perform search whenever searchQuery, active state, or filter changes
    LaunchedEffect(searchQuery, active, currentFilter) {
        if (searchQuery.isNotBlank()) {
            playerViewModel.performSearch(searchQuery)
        } else if (active) {
            playerViewModel.performSearch("")
        }
    }
    val searchResults = uiState.searchResults
    val handleTrackMoreOptionsClick: (Track) -> Unit = { Track ->
        selectedTrackForInfo = Track
        playerViewModel.selectTrackForInfo(Track)
        showTrackInfoBottomSheet = true
    }

    val searchbarHorizontalPadding by animateDpAsState(
        targetValue = if (!active) 24.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh),
        label = "searchbarHorizontalPadding"
    )

    val searchbarCornerRadius = 28.dp

    val dm = LocalAudioBookPlayerDarkTheme.current

    val gradientColorsDark = listOf(
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        Color.Transparent
    ).toImmutableList()

    val gradientColorsLight = listOf(
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
        Color.Transparent
    ).toImmutableList()

    val gradientColors = if (dm) gradientColorsDark else gradientColorsLight

    val gradientBrush = remember(gradientColors) {
        Brush.verticalGradient(colors = gradientColors)
    }

    val colorScheme = MaterialTheme.colorScheme

    LaunchedEffect(active) {
        onSearchBarActiveChange(active)
    }

    DisposableEffect(Unit) {
        onDispose {
            active = false  // Reset immediately to prevent animation conflicts during navigation
            onSearchBarActiveChange(false)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    gradientBrush
                )
        )

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // CORREGIDO: Agregamos un padding mÃƒÂ­nimo para evitar crashes
            val safePadding = maxOf(0.dp, searchbarHorizontalPadding)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = safePadding) // Usar padding seguro
            ) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onSearch = {
                        if (searchQuery.isNotBlank()) {
                            playerViewModel.onSearchQuerySubmitted(searchQuery)
                        }
                        active = false
                    },
                    active = active,
                    onActiveChange = {
                        if (!it) {
                            if (searchQuery.isNotBlank()) {
                                playerViewModel.onSearchQuerySubmitted(searchQuery)
                            }
                        }
                        active = it
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize()
                        .clip(RoundedCornerShape(searchbarCornerRadius)),
                    placeholder = {
                        Text(
                            "Search...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Buscar",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier
                                    .size(48.dp)
                                    .padding(end = 10.dp)
                                    .clip(CircleShape)
                                    .background(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Limpiar",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    colors = SearchBarDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        dividerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        inputFieldColors = TextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary
                        )
                    ),
                    content = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            // Filter chips
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                //verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                SearchFilterChip(SearchFilterType.ALL, currentFilter, playerViewModel)
                                SearchFilterChip(SearchFilterType.Tracks, currentFilter, playerViewModel)
                                SearchFilterChip(SearchFilterType.Books, currentFilter, playerViewModel)
                                SearchFilterChip(SearchFilterType.Authors, currentFilter, playerViewModel)
                                SearchFilterChip(SearchFilterType.Booklists, currentFilter, playerViewModel)
                            }

                            if (searchQuery.isBlank() && active && searchHistory.isNotEmpty()) {
                                val rememberedOnHistoryClick: (String) -> Unit = remember(playerViewModel) {
                                    { query -> searchQuery = query }
                                }
                                val rememberedOnHistoryDelete: (String) -> Unit = remember(playerViewModel) {
                                    { query -> playerViewModel.deleteSearchHistoryItem(query) }
                                }
                                val rememberedOnClearAllHistory: () -> Unit = remember(playerViewModel) {
                                    { playerViewModel.clearSearchHistory() }
                                }

                                SearchHistoryList(
                                    historyItems = searchHistory,
                                    onHistoryClick = rememberedOnHistoryClick,
                                    onHistoryDelete = rememberedOnHistoryDelete,
                                    onClearAllHistory = rememberedOnClearAllHistory
                                )
                            } else if (searchQuery.isNotBlank() && searchResults.isEmpty()) {
                                EmptySearchResults(
                                    searchQuery = searchQuery,
                                    colorScheme = colorScheme
                                )
                            } else if (searchResults.isNotEmpty()) {
                                val rememberedOnItemSelected = remember(searchQuery, playerViewModel) {
                                    {
                                        if (searchQuery.isNotBlank()) {
                                            playerViewModel.onSearchQuerySubmitted(searchQuery)
                                        }
                                        active = false
                                    }
                                }
                                SearchResultsList(
                                    results = searchResults,
                                    playerViewModel = playerViewModel,
                                    onItemSelected = rememberedOnItemSelected,
                                    currentPlayingTrackId = stablePlayerState.currentTrack?.id,
                                    isPlaying = stablePlayerState.isPlaying,
                                    onTrackMoreOptionsClick = handleTrackMoreOptionsClick,
                                    navController = navController
                                )
                            } else if (searchQuery.isBlank() && active && searchHistory.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No recent searches", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                )
            }

            // Content to show when SearchBar is not active
            if (!active) {
                if (searchQuery.isBlank()) {
                    Box {
                        CategoryCategoriesGrid(
                            Categories = Categories,
                            onCategoryClick = { Category ->
                                Timber.tag("SearchScreen")
                                    .d("Category clicked: ${Category.name} (ID: ${Category.id})")
                                val encodedCategoryId = java.net.URLEncoder.encode(Category.id, "UTF-8")
                                navController.navigate(Screen.CategoryDetail.createRoute(encodedCategoryId))
                            },
                            playerViewModel = playerViewModel,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .height(80.dp)
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.surfaceContainerLowest.copy(
                                                0.5f
                                            ),
                                            MaterialTheme.colorScheme.surfaceContainerLowest
                                        )
                                    )
                                )
                        ) {

                        }
                    }
                } else {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SearchFilterChip(SearchFilterType.ALL, currentFilter, playerViewModel)
                            SearchFilterChip(SearchFilterType.Tracks, currentFilter, playerViewModel)
                            SearchFilterChip(SearchFilterType.Books, currentFilter, playerViewModel)
                            SearchFilterChip(SearchFilterType.Authors, currentFilter, playerViewModel)
                            SearchFilterChip(SearchFilterType.Booklists, currentFilter, playerViewModel)
                        }
                        SearchResultsList(
                            results = searchResults,
                            playerViewModel = playerViewModel,
                            onItemSelected = { },
                            currentPlayingTrackId = stablePlayerState.currentTrack?.id,
                            isPlaying = stablePlayerState.isPlaying,
                            onTrackMoreOptionsClick = handleTrackMoreOptionsClick,
                            navController = navController
                        )
                    }
                }
            }
        }
    }

    if (showTrackInfoBottomSheet && selectedTrackForInfo != null) {
        val currentTrack = selectedTrackForInfo
        val isFavorite = remember(currentTrack?.id, favoriteTrackIds) {
            derivedStateOf {
                currentTrack?.let { favoriteTrackIds.contains(it.id) }
            }
        }.value ?: false
        val removeFromListTrigger = remember(currentTrack) {
            {
                searchQuery = "$searchQuery "
            }
        }

        if (currentTrack != null) {
            TrackInfoBottomSheet(
                Track = currentTrack,
                isFavorite = isFavorite,
                removeFromListTrigger = removeFromListTrigger,
                onToggleFavorite = {
                    playerViewModel.toggleFavoriteSpecificTrack(currentTrack)
                },
                onDismiss = { showTrackInfoBottomSheet = false },
                onPlayTrack = {
                    playerViewModel.showAndPlayTrack(currentTrack)
                    showTrackInfoBottomSheet = false
                },
                onAddToQueue = {
                    playerViewModel.addTrackToQueue(currentTrack)
                    showTrackInfoBottomSheet = false
                },
                onAddNextToQueue = {
                    playerViewModel.addTrackNextToQueue(currentTrack)
                    showTrackInfoBottomSheet = false
                },
                onAddToBooklist = {
                    showBooklistBottomSheet = true;
                },
                onDeleteFromDevice = playerViewModel::deleteFromDevice,
                onNavigateToBook = {
                    navController.navigate(Screen.BookDetail.createRoute(currentTrack.BookId))
                    showTrackInfoBottomSheet = false
                },
                onNavigateToAuthor = {
                    navController.navigate(Screen.AuthorDetail.createRoute(currentTrack.AuthorId))
                    showTrackInfoBottomSheet = false
                },
                onEditTrack = { newTitle, newAuthor, newBook, newCategory, newTranscript, newTrackNumber, coverArtUpdate ->
                    playerViewModel.editTrackMetadata(
                        currentTrack,
                        newTitle,
                        newAuthor,
                        newBook,
                        newCategory,
                        newTranscript,
                        newTrackNumber,
                        coverArtUpdate
                    )
                },
                generateAiMetadata = { fields ->
                    playerViewModel.generateAiMetadata(currentTrack, fields)
                },
            )
            if (showBooklistBottomSheet) {
                val BooklistUiState by BooklistViewModel.uiState.collectAsState()

                BooklistBottomSheet(
                    BooklistUiState = BooklistUiState,
                    Track = currentTrack,
                    onDismiss = { showBooklistBottomSheet = false },
                    bottomBarHeight = bottomBarHeightDp,
                    playerViewModel = playerViewModel,
                )
            }
        }
    }
}

@Composable
fun SearchResultSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

@Composable
fun SearchHistoryList(
    historyItems: List<SearchHistoryItem>,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit,
    onClearAllHistory: () -> Unit
) {
    val localDensity = LocalDensity.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Recent Searches",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            if (historyItems.isNotEmpty()) {
                TextButton(onClick = onClearAllHistory) {
                    Text("Clear All")
                }
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(
                top = 8.dp,
            )
        ) {
            items(historyItems, key = { "history_${it.id ?: it.query}" }) { item ->
                SearchHistoryListItem(
                    item = item,
                    onHistoryClick = onHistoryClick,
                    onHistoryDelete = onHistoryDelete
                )
            }
        }
    }
}

@Composable
fun SearchHistoryListItem(
    item: SearchHistoryItem,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) { detectTapGestures(onTap = { onHistoryClick(item.query) }) }
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = "History Icon",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = item.query,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = { onHistoryDelete(item.query) }) {
            Icon(
                imageVector = Icons.Rounded.DeleteForever,
                contentDescription = "Delete history item",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}


@Composable
fun EmptySearchResults(searchQuery: String, colorScheme: ColorScheme) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.Search, // More generic icon
            contentDescription = "No results",
            modifier = Modifier
                .size(80.dp)
                .padding(bottom = 16.dp),
            tint = colorScheme.primary.copy(alpha = 0.6f)
        )

        Text(
            text = if (searchQuery.isNotBlank()) "No results for \"$searchQuery\"" else "Nothing found",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Try a different search term or check your filters.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}


@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SearchResultsList(
    results: List<SearchResultItem>,
    playerViewModel: PlayerViewModel,
    onItemSelected: () -> Unit,
    currentPlayingTrackId: String?,
    isPlaying: Boolean,
    onTrackMoreOptionsClick: (Track) -> Unit,
    navController: NavHostController
) {
    val localDensity = LocalDensity.current
    val playerStableState by playerViewModel.stablePlayerState.collectAsState()

    if (results.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No results found.", style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    val groupedResults = results.groupBy { item ->
        when (item) {
            is SearchResultItem.TrackItem -> SearchFilterType.Tracks
            is SearchResultItem.BookItem -> SearchFilterType.Books
            is SearchResultItem.AuthorItem -> SearchFilterType.Authors
            is SearchResultItem.BooklistItem -> SearchFilterType.Booklists
        }
    }

    val sectionOrder = listOf(
        SearchFilterType.Tracks,
        SearchFilterType.Books,
        SearchFilterType.Authors,
        SearchFilterType.Booklists
    )

    val imePadding = WindowInsets.ime.getBottom(localDensity).dp
    val systemBarPaddingBottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 94.dp

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = if (imePadding <= 8.dp) (MiniPlayerHeight + systemBarPaddingBottom) else imePadding
        )
    ) {
        sectionOrder.forEach { filterType ->
            val itemsForSection = groupedResults[filterType] ?: emptyList()

            if (itemsForSection.isNotEmpty()) {
                item(key = "header_${filterType.name}") {
                    SearchResultSectionHeader(
                        title = when (filterType) {
                            SearchFilterType.Tracks -> "Tracks"
                            SearchFilterType.Books -> "Books"
                            SearchFilterType.Authors -> "Authors"
                            SearchFilterType.Booklists -> "Booklists"
                            else -> "Results"
                        }
                    )
                }

                items(
                    count = itemsForSection.size,
                    key = { index ->
                        val item = itemsForSection[index]
                        when (item) {
                            is SearchResultItem.TrackItem -> "Track_${item.Track.id}"
                            is SearchResultItem.BookItem -> "Book_${item.Book.id}"
                            is SearchResultItem.AuthorItem -> "Author_${item.Author.id}"
                            is SearchResultItem.BooklistItem -> "Booklist_${item.Booklist.id}_${index}"
                        }
                    }
                ) { index ->
                    val item = itemsForSection[index]
                    Box(modifier = Modifier.padding(bottom = 12.dp)) {
                        when (item) {
                            is SearchResultItem.TrackItem -> {
                                val rememberedOnClick = remember(item.Track, playerViewModel, onItemSelected) {
                                    {
                                        playerViewModel.showAndPlayTrack(item.Track)
                                        onItemSelected()
                                    }
                                }
                                EnhancedTrackListItem(
                                    Track = item.Track,
                                    isPlaying = isPlaying,
                                    isCurrentTrack = currentPlayingTrackId == item.Track.id,
                                    onMoreOptionsClick = onTrackMoreOptionsClick,
                                    onClick = rememberedOnClick
                                )
                            }

                            is SearchResultItem.BookItem -> {
                                val onPlayClick = remember(item.Book, playerViewModel, onItemSelected) {
                                    {
                                        Timber.tag("SearchScreen")
                                            .d("Book clicked: ${item.Book.title}")
                                        playerViewModel.playBook(item.Book)
                                        onItemSelected()
                                    }
                                }
                                val onOpenClick = remember (
                                    item.Book,
                                    playerViewModel, onItemSelected ) {
                                    {
                                        navController.navigate(Screen.BookDetail.createRoute(item.Book.id))
                                        onItemSelected()
                                    }
                                }
                                SearchResultBookItem(
                                    Book = item.Book,
                                    onPlayClick = onPlayClick,
                                    onOpenClick = onOpenClick
                                )
                            }

                            is SearchResultItem.AuthorItem -> {
                                val onPlayClick = remember(item.Author, playerViewModel, onItemSelected) {
                                    {
                                        Timber.tag("SearchScreen")
                                            .d("Author clicked: ${item.Author.name}")
                                        playerViewModel.playAuthor(item.Author)
                                        onItemSelected()
                                    }
                                }
                                val onOpenClick = remember (
                                    item.Author,
                                    playerViewModel, onItemSelected ) {
                                    {
                                        navController.navigate(Screen.AuthorDetail.createRoute(item.Author.id))
                                        onItemSelected()
                                    }
                                }
                                SearchResultAuthorItem(
                                    Author = item.Author,
                                    onPlayClick = onPlayClick,
                                    onOpenClick = onOpenClick
                                )
                            }

                            is SearchResultItem.BooklistItem -> {
                                var TracksInBooklist by remember { mutableStateOf<List<Track>>(emptyList()) }
                                var fetchTracks by remember { mutableStateOf(false) }
                                LaunchedEffect(fetchTracks) {
                                    TracksInBooklist = playerViewModel.getTracks( item.Booklist.TrackIds)
                                }
                                val onPlayClick = remember(item.Booklist, playerViewModel, onItemSelected) {
                                    {
                                        fetchTracks = true
                                        if (TracksInBooklist.isNotEmpty()) {
                                            playerViewModel.playTracks(
                                                TracksInBooklist,
                                                TracksInBooklist.first(),
                                                item.Booklist.name
                                            )
                                            if (playerStableState.isShuffleEnabled) playerViewModel.toggleShuffle()
                                        } else
                                            playerViewModel.sendToast("Empty Booklist")
                                        onItemSelected()
                                    }
                                }
                                val onOpenClick = remember (
                                    item.Booklist,
                                    playerViewModel, onItemSelected ) {
                                    {
                                        navController.navigate(Screen.BooklistDetail.createRoute(item.Booklist.id))
                                        onItemSelected()
                                    }
                                }
                                SearchResultBooklistItem(
                                    Booklist = item.Booklist,
                                    onPlayClick = onPlayClick,
                                    onOpenClick = onOpenClick
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultBookItem(
    Book: Book,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    Card(
        onClick = onOpenClick,
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SmartImage(
                model = Book.BookArtUriString,
                contentDescription = "Book Art: ${Book.title}",
                modifier = Modifier
                    .size(56.dp)
                    .clip(itemShape)
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = Book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = Book.Author,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledIconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play Book", modifier = Modifier.size(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultAuthorItem(
    Author: Author,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    Card(
        onClick = onOpenClick,
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = R.drawable.rounded_Author_24),
                contentDescription = "Author",
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape)
                    .padding(12.dp),
                tint = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = Author.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${Author.TrackCount} Tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledIconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play Author", modifier = Modifier.size(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchResultBooklistItem(
    Booklist: Booklist,
    onOpenClick: () -> Unit,
    onPlayClick: () -> Unit
) {
    val itemShape = remember {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = 26.dp,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = 26.dp,
            smoothnessAsPercentBR = 60,
            cornerRadiusBR = 26.dp,
            smoothnessAsPercentBL = 60,
            cornerRadiusBL = 26.dp,
            smoothnessAsPercentTL = 60
        )
    }

    Card(
        onClick = onOpenClick,
        shape = itemShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.BooklistPlay,
                contentDescription = "Booklist",
                modifier = Modifier
                    .size(56.dp)
                    .clip(itemShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .padding(8.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = Booklist.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            FilledIconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play Booklist", modifier = Modifier.size(24.dp))
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun SearchFilterChip(
    filterType: SearchFilterType,
    currentFilter: SearchFilterType, // Este valor deberÃƒÂ­a provenir del estado de tu PlayerViewModel
    playerViewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    val selected = filterType == currentFilter

    FilterChip(
        selected = selected, // FilterChip tiene un parÃƒÂ¡metro 'selected'
        onClick = { playerViewModel.updateSearchFilter(filterType) },
        label = { Text(filterType.name.lowercase().replaceFirstChar { it.titlecase() }) },
        modifier = modifier,
        shape = CircleShape, // Expressive shape
        border = BorderStroke(
            width = 0.dp,
            color = Color.Transparent
        ),
        colors = FilterChipDefaults.filterChipColors(
            // Expressive colors for unselected state
            containerColor =  MaterialTheme.colorScheme.secondaryContainer,
            labelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            // Expressive colors for selected state
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
         leadingIcon = if (selected) {
             {
                 Icon(
                     painter = painterResource(R.drawable.rounded_check_circle_24),
                     contentDescription = "Selected",
                     tint = MaterialTheme.colorScheme.onPrimary,
                     modifier = Modifier.size(FilterChipDefaults.IconSize)
                 )
             }
         } else {
             null
         }
    )
}
