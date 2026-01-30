@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.oakiha.audia.presentation.screens

import android.os.Trace
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oakiha.audia.ui.theme.LocalAudioBookPlayerDarkTheme
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.oakiha.audia.R
import com.oakiha.audia.presentation.components.ShimmerBox
import com.oakiha.audia.data.model.Book
import com.oakiha.audia.data.model.Author
import com.oakiha.audia.data.model.AudiobookFolder
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.data.model.SortOption
import com.oakiha.audia.presentation.components.MiniPlayerHeight
import com.oakiha.audia.presentation.components.NavBarContentHeight
import com.oakiha.audia.presentation.components.SmartImage
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.ui.res.stringResource
import com.oakiha.audia.presentation.components.AiBooklistsheet
import com.oakiha.audia.presentation.components.BooklistArtCollage
import com.oakiha.audia.presentation.components.ReorderTabsSheet
import com.oakiha.audia.presentation.components.TrackInfoBottomSheet
import com.oakiha.audia.presentation.components.subcomps.LibraryActionRow
import com.oakiha.audia.presentation.navigation.Screen
import com.oakiha.audia.presentation.viewmodel.ColorSchemePair
import com.oakiha.audia.presentation.viewmodel.PlayerViewModel
import com.oakiha.audia.presentation.viewmodel.StablePlayerState
import com.oakiha.audia.presentation.viewmodel.BooklistUiState
import com.oakiha.audia.presentation.viewmodel.BooklistViewModel
import com.oakiha.audia.data.model.LibraryTabId
import com.oakiha.audia.data.model.toLibraryTabIdOrNull
import com.oakiha.audia.data.preferences.LibraryNavigationMode
import com.oakiha.audia.data.worker.SyncProgress
import com.oakiha.audia.presentation.components.LibrarySortBottomSheet
import com.oakiha.audia.presentation.components.SyncProgressBar
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ButtonDefaults
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ripple
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.pointerInput
import com.oakiha.audia.presentation.components.AutoScrollingTextOnDemand
import com.oakiha.audia.presentation.screens.CreateBooklistDialog
import com.oakiha.audia.presentation.components.BooklistBottomSheet
import com.oakiha.audia.presentation.components.BooklistContainer
import com.oakiha.audia.presentation.components.subcomps.PlayingEqIcon
import com.oakiha.audia.ui.theme.GoogleSansRounded
import java.util.Locale
import androidx.compose.ui.platform.LocalContext
import android.widget.Toast
import com.oakiha.audia.data.model.BooklistshapeType
import kotlinx.coroutines.flow.first
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import androidx.paging.LoadState

val ListExtraBottomGap = 30.dp
val PlayerSheetCollapsedCornerRadius = 32.dp

@RequiresApi(Build.VERSION_CODES.R)
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LibraryScreen(
    navController: NavController,
    playerViewModel: PlayerViewModel = hiltViewModel(),
    BooklistViewModel: BooklistViewModel = hiltViewModel()
) {
    // La recolecciÃƒÂ³n de estados de alto nivel se mantiene mÃƒÂ­nima.
    val context = LocalContext.current // Added context
    val lastTabIndex by playerViewModel.lastLibraryTabIndexFlow.collectAsState()
    val favoriteIds by playerViewModel.favoriteTrackIds.collectAsState() // Reintroducir favoriteIds aquÃƒÂ­
    val scope = rememberCoroutineScope() // Mantener si se usa para acciones de UI
    val syncManager = playerViewModel.syncManager
    var isRefreshing by remember { mutableStateOf(false) }
    val isSyncing by syncManager.isSyncing.collectAsState(initial = false)
    val syncProgress by syncManager.syncProgress.collectAsState(initial = SyncProgress())

    var showTrackInfoBottomSheet by remember { mutableStateOf(false) }
    var showBooklistBottomSheet by remember { mutableStateOf(false) }
    val selectedTrackForInfo by playerViewModel.selectedTrackForInfo.collectAsState()
    val tabTitles by playerViewModel.libraryTabsFlow.collectAsState()
    val pagerState = rememberPagerState(initialPage = lastTabIndex) { tabTitles.size }
    val currentTabId by playerViewModel.currentLibraryTabId.collectAsState()
    val libraryNavigationMode by playerViewModel.libraryNavigationMode.collectAsState()
    val isSortSheetVisible by playerViewModel.isSortingSheetVisible.collectAsState()
    var showCreateBooklistDialog by remember { mutableStateOf(false) }

    val m3uImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { BooklistViewModel.importM3u(it) }
    }

    var showReorderTabsSheet by remember { mutableStateOf(false) }
    var showTabSwitcherSheet by remember { mutableStateOf(false) }

    val stableOnMoreOptionsClick: (Track) -> Unit = remember {
        { Track ->
            playerViewModel.selectTrackForInfo(Track)
            showTrackInfoBottomSheet = true
        }
    }
    // Pull-to-refresh uses incremental sync for speed
    // We enforce a minimum duration of 3.5s for the animation as requested by the user.
    var isMinDelayActive by remember { mutableStateOf(false) }

    val onRefresh: () -> Unit = remember {
        {
            isMinDelayActive = true
            isRefreshing = true
            syncManager.incrementalSync()
            scope.launch {
                kotlinx.coroutines.delay(3500)
                isMinDelayActive = false
                // If sync finished during the delay, the LaunchedEffect blocked the update.
                // We must manually check and turn it off if needed.
                val currentlySyncing = syncManager.isSyncing.first()
                if (!currentlySyncing) {
                    isRefreshing = false
                }
            }
        }
    }

    LaunchedEffect(isSyncing) {
        if (isSyncing) {
            isRefreshing = true
        } else {
            // Only hide refresh indicator if the minimum delay has passed
            if (!isMinDelayActive) {
                isRefreshing = false
            }
        }
    }
    
    // Feedback for Booklist Creation
    LaunchedEffect(Unit) {
        BooklistViewModel.BooklistCreationEvent.collect { success ->
            if (success) {
                showCreateBooklistDialog = false
                Toast.makeText(context, "Booklist created successfully", Toast.LENGTH_SHORT).show()
            }
        }
    }
    // La lÃƒÂ³gica de carga diferida (lazy loading) se mantiene.
    LaunchedEffect(Unit) {
        Trace.beginSection("LibraryScreen.InitialTabLoad")
        playerViewModel.onLibraryTabSelected(lastTabIndex)
        Trace.endSection()
    }

    LaunchedEffect(pagerState.currentPage) {
        Trace.beginSection("LibraryScreen.PageChangeTabLoad")
        playerViewModel.onLibraryTabSelected(pagerState.currentPage)
        Trace.endSection()
    }

    val fabState by remember { derivedStateOf { pagerState.currentPage } } // UI sin cambios
    val transition = updateTransition(
        targetState = fabState,
        label = "Action Button Icon Transition"
    ) // UI sin cambios

    val systemNavBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomBarHeightDp = NavBarContentHeight + systemNavBarInset

    val dm = LocalAudioBookPlayerDarkTheme.current

    val iconRotation by transition.animateFloat(
        label = "Action Button Icon Rotation",
        transitionSpec = {
            tween(durationMillis = 300, easing = FastOutSlowInEasing)
        }
    ) { page ->
        when (tabTitles.getOrNull(page)?.toLibraryTabIdOrNull()) {
            LibraryTabId.Booklists -> 0f // Booklist icon (BooklistAdd) usually doesn't rotate
            else -> 360f // Shuffle icon animates
        }
    }

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

    val isCompactNavigation = libraryNavigationMode == LibraryNavigationMode.COMPACT_PILL
    val currentTab = tabTitles.getOrNull(pagerState.currentPage)?.toLibraryTabIdOrNull() ?: currentTabId
    val currentTabTitle = currentTab.displayTitle()

    Scaffold(
        modifier = Modifier.background(brush = gradientBrush),
        topBar = {
            TopAppBar(
                title = {
                    if (isCompactNavigation) {
                        LibraryNavigationPill(
                            title = currentTabTitle,
                            isExpanded = showTabSwitcherSheet,
                            iconRes = currentTab.iconRes(),
                            pageIndex = pagerState.currentPage,
                            onClick = {
                                showTabSwitcherSheet = true
                            },
                            onArrowClick = { showTabSwitcherSheet = true }
                        )
//                        LibraryNavigationPill(
//                            title = currentTabTitle,
//                            animationDirection = pillAnimationDirection,
//                            onClick = { showTabSwitcherSheet = true }
//                        )
                    } else {
                        Text(
                            modifier = Modifier.padding(start = 8.dp),
                            text = "Library",
                            fontFamily = GoogleSansRounded,
                            //style = ExpTitleTypography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 40.sp,
                            letterSpacing = 1.sp
                        )
                    }
                },
                actions = {
                    FilledIconButton(
                        modifier = Modifier.padding(end = 14.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        onClick = {
                            navController.navigate(Screen.Settings.route)
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_settings_24),
                            contentDescription = "Settings"
                        )
                    }
//                    FilledTonalIconButton(
//                        modifier = Modifier.padding(end = 14.dp),
//                        onClick = { /* TODO: User profile action */ },
//                        colors = IconButtonDefaults.filledTonalIconButtonColors(
//                            containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
//                        )
//                    ) {
//                        Icon(
//                            imageVector = Icons.Rounded.Person,
//                            contentDescription = "User Profile",
//                            tint = MaterialTheme.colorScheme.primary
//                        )
//                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = gradientColors[0]
                )
            )
        }
    ) { innerScaffoldPadding ->
        Box( // Box para permitir superposiciÃƒÂ³n del indicador de carga
            modifier = Modifier
                .padding(top = innerScaffoldPadding.calculateTopPadding())
                .fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    // .padding(innerScaffoldPadding) // El padding ya estÃƒÂ¡ en el Box contenedor
                    .background(brush = Brush.verticalGradient(gradientColors))
                    .fillMaxSize()
            ) {
                if (!isCompactNavigation) {
                    ScrollableTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = Color.Transparent,
                        edgePadding = 12.dp,
                        indicator = { tabPositions ->
                            if (pagerState.currentPage < tabPositions.size) {
                                TabRowDefaults.PrimaryIndicator(
                                    modifier = Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                                    height = 3.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        divider = {}
                    ) {
                        tabTitles.forEachIndexed { index, rawId ->
                            val tabId = rawId.toLibraryTabIdOrNull() ?: LibraryTabId.Tracks
                            TabAnimation(
                                index = index,
                                title = tabId.storageKey,
                                selectedIndex = pagerState.currentPage,
                                onClick = { scope.launch { pagerState.animateScrollToPage(index) } }
                            ) {
                                Text(
                                    text = tabId.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                        TabAnimation(
//                        modifier = Modifier.aspectRatio(1f),
                            index = -1, // A non-matching index to keep it unselected
                            title = "Edit",
                            selectedIndex = pagerState.currentPage,
                            onClick = { showReorderTabsSheet = true }
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Reorder tabs",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                            )
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 0.dp), // Added vertical padding
                    color = MaterialTheme.colorScheme.surface,
                    shape = AbsoluteSmoothCornerShape(
                        cornerRadiusTL = 34.dp,
                        smoothnessAsPercentBL = 60,
                        cornerRadiusBL = 0.dp,
                        smoothnessAsPercentBR = 60,
                        cornerRadiusBR = 0.dp,
                        smoothnessAsPercentTR = 60,
                        cornerRadiusTR = 34.dp,
                        smoothnessAsPercentTL = 60
                    )
                    // shape = AbsoluteSmoothCornerShape(cornerRadiusTL = 24.dp, smoothnessAsPercentTR = 60, /*...*/) // Your custom shape
                ) {
                    Column(Modifier.fillMaxSize()) {
                        // OPTIMIZACIÃƒâ€œN: La lÃƒÂ³gica de ordenamiento ahora es mÃƒÂ¡s eficiente.
                        val availableSortOptions by playerViewModel.availableSortOptions.collectAsState()
                        val sanitizedSortOptions = remember(availableSortOptions, currentTabId) {
                            val cleaned = availableSortOptions.filterIsInstance<SortOption>()
                            val ensured = if (cleaned.any { option ->
                                    option.storageKey == currentTabId.defaultSort.storageKey
                                }
                            ) {
                                cleaned
                            } else {
                                buildList {
                                    add(currentTabId.defaultSort)
                                    addAll(cleaned)
                                }
                            }

                            val distinctByKey = ensured.distinctBy { it.storageKey }
                            distinctByKey.ifEmpty { listOf(currentTabId.defaultSort) }
                        }
                        val playerUiState by playerViewModel.playerUiState.collectAsState()
                        val BooklistUiState by BooklistViewModel.uiState.collectAsState()
                        val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()

                        val currentSelectedSortOption: SortOption? = when (currentTabId) {
                            LibraryTabId.Tracks -> playerUiState.currentTracksortOption
                            LibraryTabId.Books -> playerUiState.currentBooksortOption
                            LibraryTabId.Authors -> playerUiState.currentAuthorsortOption
                            LibraryTabId.Booklists -> BooklistUiState.currentBooklistsortOption
                            LibraryTabId.LIKED -> playerUiState.currentFavoriteSortOption
                            LibraryTabId.FOLDERS -> playerUiState.currentFolderSortOption
                        }

                        val onSortOptionChanged: (SortOption) -> Unit = remember(playerViewModel, BooklistViewModel, currentTabId) {
                            { option ->
                                when (currentTabId) {
                                    LibraryTabId.Tracks -> playerViewModel.sortTracks(option)
                                    LibraryTabId.Books -> playerViewModel.sortBooks(option)
                                    LibraryTabId.Authors -> playerViewModel.sortAuthors(option)
                                    LibraryTabId.Booklists -> BooklistViewModel.sortBooklists(option)
                                    LibraryTabId.LIKED -> playerViewModel.sortFavoriteTracks(option)
                                    LibraryTabId.FOLDERS -> playerViewModel.sortFolders(option)
                                }
                            }
                        }

                        //val playerUiState by playerViewModel.playerUiState.collectAsState()
                            //val playerUiState by playerViewModel.playerUiState.collectAsState()
                        LibraryActionRow(
                            modifier = Modifier
                                .padding(
                                    top = 6.dp,
                                    start = 10.dp,
                                    end = 10.dp
                                )
                                .heightIn(min = 56.dp), // Fix for height jump
                            //currentPage = pagerState.currentPage,
                            onMainActionClick = {
                                when (tabTitles.getOrNull(pagerState.currentPage)?.toLibraryTabIdOrNull()) {
                                    LibraryTabId.Booklists -> showCreateBooklistDialog = true
                                    LibraryTabId.LIKED -> playerViewModel.shuffleFavoriteTracks()
                                    LibraryTabId.Books -> playerViewModel.shuffleRandomBook()
                                    LibraryTabId.Authors -> playerViewModel.shuffleRandomAuthor()
                                    else -> playerViewModel.shuffleAllTracks()
                                }
                            },
                            iconRotation = iconRotation,
                            showSortButton = sanitizedSortOptions.isNotEmpty(),
                            onSortClick = { playerViewModel.showSortingSheet() },
                            isBooklistTab = currentTabId == LibraryTabId.Booklists,
                            isFoldersTab = currentTabId == LibraryTabId.FOLDERS && (!playerUiState.isFoldersBooklistView || playerUiState.currentFolder != null),
                            onGenerateWithAiClick = { /* Unused now */ },
                            onImportM3uClick = { m3uImportLauncher.launch("audio/x-mpegurl") },
                            //onFilterClick = { playerViewModel.toggleFolderFilter() },
                            currentFolder = playerUiState.currentFolder,
                            onFolderClick = { playerViewModel.navigateToFolder(it) },
                            onNavigateBack = { playerViewModel.navigateBackFolder() },
                            isShuffleEnabled = stablePlayerState.isShuffleEnabled
                        )

                        if (isSortSheetVisible && sanitizedSortOptions.isNotEmpty()) {
                            val currentSelectionKey = currentSelectedSortOption?.storageKey
                            val selectedOptionForSheet = sanitizedSortOptions.firstOrNull { option ->
                                option.storageKey == currentSelectionKey
                            }
                                ?: sanitizedSortOptions.firstOrNull { option ->
                                    option.storageKey == currentTabId.defaultSort.storageKey
                                }
                                ?: sanitizedSortOptions.first()

                            LibrarySortBottomSheet(
                                title = "Sort by",
                                options = sanitizedSortOptions,
                                selectedOption = selectedOptionForSheet,
                                onDismiss = { playerViewModel.hideSortingSheet() },
                                onOptionSelected = { option ->
                                    onSortOptionChanged(option)
                                    playerViewModel.hideSortingSheet()
                                },
                                showViewToggle = currentTabId == LibraryTabId.FOLDERS,
                                viewToggleChecked = playerUiState.isFoldersBooklistView,
                                onViewToggleChange = { isChecked ->
                                    playerViewModel.setFoldersBooklistView(isChecked)
                                }
                            )
                        }

                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 8.dp),
                            pageSpacing = 0.dp,
                            beyondViewportPageCount = 1, // Pre-load adjacent tabs to reduce lag when switching
                            key = { tabTitles[it] }
                        ) { page ->
                            when (tabTitles.getOrNull(page)?.toLibraryTabIdOrNull()) {
                                LibraryTabId.Tracks -> {
                                    // Use sorted allTracks from LibraryStateHolder
                                    val playerUiState by playerViewModel.playerUiState.collectAsState()
                                    val allTracks = playerUiState.allTracks
                                    val isLoading = playerUiState.isLoadingInitialTracks
                                    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
                                    
                                    LibraryTracksTab(
                                        Tracks = allTracks,
                                        isLoading = isLoading,
                                        stablePlayerState = stablePlayerState,
                                        playerViewModel = playerViewModel,
                                        bottomBarHeight = bottomBarHeightDp,
                                        onMoreOptionsClick = stableOnMoreOptionsClick,
                                        isRefreshing = isRefreshing,
                                        onRefresh = onRefresh
                                    )
                                }
                                LibraryTabId.Books -> {
                                    val Books by remember {
                                        playerViewModel.playerUiState
                                            .map { it.Books }
                                            .distinctUntilChanged()
                                    }.collectAsState(initial = persistentListOf())

                                    val isLoading by remember {
                                        playerViewModel.playerUiState
                                            .map { it.isLoadingLibraryCategories }
                                            .distinctUntilChanged()
                                    }.collectAsState(initial = Books.isEmpty())

                                    val stableOnBookClick: (Long) -> Unit = remember(navController) {
                                        { BookId: Long ->
                                            navController.navigate(Screen.BookDetail.createRoute(BookId))
                                        }
                                    }
                                    LibraryBooksTab(
                                        Books = Books,
                                        isLoading = isLoading,
                                        playerViewModel = playerViewModel,
                                        bottomBarHeight = bottomBarHeightDp,
                                        onBookClick = stableOnBookClick,
                                        isRefreshing = isRefreshing,
                                        onRefresh = onRefresh
                                    )
                                }

                                LibraryTabId.Authors -> {
                                    val Authors by remember {
                                        playerViewModel.playerUiState
                                            .map { it.Authors }
                                            .distinctUntilChanged()
                                    }.collectAsState(initial = persistentListOf())

                                    val isLoading by remember {
                                        playerViewModel.playerUiState
                                            .map { it.isLoadingLibraryCategories }
                                            .distinctUntilChanged()
                                    }.collectAsState(initial = Authors.isEmpty())

                                    LibraryAuthorsTab(
                                        Authors = Authors,
                                        isLoading = isLoading,
                                        playerViewModel = playerViewModel,
                                        bottomBarHeight = bottomBarHeightDp,
                                        onAuthorClick = { AuthorId ->
                                            navController.navigate(
                                                Screen.AuthorDetail.createRoute(
                                                    AuthorId
                                                )
                                            )
                                        },
                                        isRefreshing = isRefreshing,
                                        onRefresh = onRefresh
                                    )
                                }

                                LibraryTabId.Booklists -> {
                                    val currentBooklistUiState by BooklistViewModel.uiState.collectAsState()
                                    LibraryBooklistsTab(
                                        BooklistUiState = currentBooklistUiState,
                                        navController = navController,
                                        playerViewModel = playerViewModel,
                                        bottomBarHeight = bottomBarHeightDp,
                                        onGenerateWithAiClick = { playerViewModel.showAiBooklistsheet() },
                                        isRefreshing = isRefreshing,
                                        onRefresh = onRefresh
                                    )
                                }

                                LibraryTabId.LIKED -> {
                                    val favoriteTracks by playerViewModel.favoriteTracks.collectAsState()
                                    LibraryFavoritesTab(
                                        favoriteTracks = favoriteTracks,
                                        playerViewModel = playerViewModel,
                                        bottomBarHeight = bottomBarHeightDp,
                                        onMoreOptionsClick = stableOnMoreOptionsClick,
                                        isRefreshing = isRefreshing,
                                        onRefresh = onRefresh
                                    )
                                }

                                LibraryTabId.FOLDERS -> {
                                    val context = LocalContext.current
                                    var hasPermission by remember { mutableStateOf(Environment.isExternalStorageManager()) }
                                    val launcher = rememberLauncherForActivityResult(
                                        ActivityResultContracts.StartActivityForResult()
                                    ) {
                                        hasPermission = Environment.isExternalStorageManager()
                                    }

                                    if (hasPermission) {
                                        val playerUiState by playerViewModel.playerUiState.collectAsState()
                                        val folders = playerUiState.AudiobookFolders
                                        val currentFolder = playerUiState.currentFolder
                                        val isLoading = playerUiState.isLoadingLibraryCategories
                                        val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()

                                        LibraryFoldersTab(
                                            folders = folders,
                                            currentFolder = currentFolder,
                                            isLoading = isLoading,
                                            bottomBarHeight = bottomBarHeightDp,
                                            stablePlayerState = stablePlayerState,
                                            onNavigateBack = { playerViewModel.navigateBackFolder() },
                                            onFolderClick = { folderPath -> playerViewModel.navigateToFolder(folderPath) },
                                            onFolderAsBooklistClick = { folder ->
                                                val encodedPath = Uri.encode(folder.path)
                                                navController.navigate(
                                                    Screen.BooklistDetail.createRoute(
                                                        "${BooklistViewModel.FOLDER_Booklist_PREFIX}$encodedPath"
                                                    )
                                                )
                                            },
                                            onPlayTrack = { Track, queue ->
                                                playerViewModel.showAndPlayTrack(Track, queue, currentFolder?.name ?: "Folder")
                                            },
                                            onMoreOptionsClick = stableOnMoreOptionsClick,
                                            isBooklistView = playerUiState.isFoldersBooklistView,
                                            currentSortOption = playerUiState.currentFolderSortOption,
                                            isRefreshing = isRefreshing,
                                            onRefresh = onRefresh
                                        )
                                    } else {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(16.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Text("All files access is required to browse folders.")
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(onClick = {
                                                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                                intent.data = Uri.fromParts("package", context.packageName, null)
                                                launcher.launch(intent)
                                            }) {
                                                Text("Grant Permission")
                                            }
                                        }
                                    }
                                }

                                null -> Unit
                            }
                        }
                    }
                }
                val globalLoadingState by playerViewModel.playerUiState.collectAsState()
                if (globalLoadingState.isGeneratingAiMetadata) {
                    Surface( // Fondo semitransparente para el indicador
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                LoadingIndicator(modifier = Modifier.size(64.dp))
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Generating metadata with AI...",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                } else if (globalLoadingState.isSyncingLibrary || ((globalLoadingState.isLoadingInitialTracks || globalLoadingState.isLoadingLibraryCategories) && (globalLoadingState.TrackCount == 0 && globalLoadingState.Books.isEmpty() && globalLoadingState.Authors.isEmpty()))) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(32.dp)
                            ) {
                                if (syncProgress.hasProgress && syncProgress.isRunning) {
                                    // Show progress bar with file count when we have progress info
                                    SyncProgressBar(
                                        syncProgress = syncProgress,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                } else {
                                    // Show indeterminate loading indicator when scanning starts
                                    LoadingIndicator(modifier = Modifier.size(64.dp))
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = stringResource(R.string.syncing_library),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
            //Grad box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(170.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.2f to Color.Transparent,
                                0.8f to MaterialTheme.colorScheme.surfaceContainerLowest,
                                1.0f to MaterialTheme.colorScheme.surfaceContainerLowest
                            )
                        )
                    )
            ) {

            }
        }
    }

    val allTracks by playerViewModel.allTracksFlow.collectAsState(initial = emptyList())


    CreateBooklistDialog(
        visible = showCreateBooklistDialog,
        allTracks = allTracks,
        onDismiss = { showCreateBooklistDialog = false },
        onCreate = { name, imageUri, color, icon, TrackIds, cropScale, cropPanX, cropPanY, shapeType, d1, d2, d3, d4 ->
            BooklistViewModel.createBooklist(
                name = name,
                coverImageUri = imageUri,
                coverColor = color,
                coverIcon = icon,
                TrackIds = TrackIds,
                cropScale = cropScale,
                cropPanX = cropPanX,
                cropPanY = cropPanY,
                isAiGenerated = false,
                isQueueGenerated = false,
                coverShapeType = shapeType,
                coverShapeDetail1 = d1,
                coverShapeDetail2 = d2,
                coverShapeDetail3 = d3,
                coverShapeDetail4 = d4
            )
        }
    )


    val showAiSheet by playerViewModel.showAiBooklistsheet.collectAsState()
    val isGeneratingAiBooklist by playerViewModel.isGeneratingAiBooklist.collectAsState()
    val aiError by playerViewModel.aiError.collectAsState()

    if (showAiSheet) {
        AiBooklistsheet(
            onDismiss = { playerViewModel.dismissAiBooklistsheet() },
            onGenerateClick = { prompt, minLength, maxLength ->
                playerViewModel.generateAiBooklist(
                    prompt = prompt,
                    minLength = minLength,
                    maxLength = maxLength,
                    saveAsBooklist = true
                )
            },
            isGenerating = isGeneratingAiBooklist,
            error = aiError
        )
    }

    if (showTrackInfoBottomSheet && selectedTrackForInfo != null) {
        val currentTrack = selectedTrackForInfo
        val isFavorite = remember(currentTrack?.id, favoriteIds) { derivedStateOf { currentTrack?.let {
            favoriteIds.contains(
                it.id)
        } } }.value ?: false

        if (currentTrack != null) {
            TrackInfoBottomSheet(
                Track = currentTrack,
                isFavorite = isFavorite,
                onToggleFavorite = {
                    // Directly use PlayerViewModel's method to toggle, which should handle UserPreferencesRepository
                    playerViewModel.toggleFavoriteSpecificTrack(currentTrack) // Assumes such a method exists or will be added to PlayerViewModel
                },
                onDismiss = { showTrackInfoBottomSheet = false },
                onPlayTrack = {
                    playerViewModel.showAndPlayTrack(currentTrack)
                    showTrackInfoBottomSheet = false
                },
                onAddToQueue = {
                    playerViewModel.addTrackToQueue(currentTrack) // Assumes such a method exists or will be added
                    showTrackInfoBottomSheet = false
                    playerViewModel.sendToast("Added to the queue")
                },
                onAddNextToQueue = {
                    playerViewModel.addTrackNextToQueue(currentTrack)
                    showTrackInfoBottomSheet = false
                    playerViewModel.sendToast("Will play next")
                },
                onAddToBooklist = {
                    showBooklistBottomSheet = true
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
                    playerViewModel.editTrackMetadata(currentTrack, newTitle, newAuthor, newBook, newCategory, newTranscript, newTrackNumber, coverArtUpdate)
                },
                generateAiMetadata = { fields ->
                    playerViewModel.generateAiMetadata(currentTrack, fields)
                },
                removeFromListTrigger = {}
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

    if (showTabSwitcherSheet) {
        LibraryTabSwitcherSheet(
            tabs = tabTitles,
            currentIndex = pagerState.currentPage,
            onTabSelected = { index ->
                scope.launch { pagerState.animateScrollToPage(index) }
                showTabSwitcherSheet = false
            },
            onEditClick = {
                showTabSwitcherSheet = false
                showReorderTabsSheet = true
            },
            onDismiss = { showTabSwitcherSheet = false }
        )
    }

    if (showReorderTabsSheet) {
        ReorderTabsSheet(
            tabs = tabTitles,
            onReorder = { newOrder ->
                playerViewModel.saveLibraryTabsOrder(newOrder)
            },
            onReset = {
                playerViewModel.resetLibraryTabsOrder()
            },
            onDismiss = { showReorderTabsSheet = false }
        )
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LibraryNavigationPill(
    title: String,
    isExpanded: Boolean,
    iconRes: Int,
    pageIndex: Int,
    onClick: () -> Unit,
    onArrowClick: () -> Unit
) {
    data class PillState(val pageIndex: Int, val iconRes: Int, val title: String)

    val pillRadius = 26.dp
    val innerRadius = 4.dp
    // Radio para cuando estÃƒÂ¡ expandido/seleccionado (totalmente redondo)
    val expandedRadius = 60.dp

    // AnimaciÃƒÂ³n Esquina Flecha (Interna):
    // Depende de 'isExpanded':
    // - true: Se vuelve redonda (expandedRadius/pillRadius) separÃƒÂ¡ndose visualmente.
    // - false: Se mantiene recta (innerRadius) pareciendo unida al tÃƒÂ­tulo.
    val animatedArrowCorner by animateDpAsState(
        targetValue = if (isExpanded) pillRadius else innerRadius,
        label = "ArrowCornerAnimation"
    )

    val arrowRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "ArrowRotation"
    )

    // IntrinsicSize.Min en el Row + fillMaxHeight en los hijos asegura misma altura
    Row(
        modifier = Modifier
            .padding(start = 4.dp)
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = pillRadius,
                bottomStart = pillRadius,
                topEnd = innerRadius,
                bottomEnd = innerRadius
            ),
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .fillMaxHeight()
                .clip(
                    RoundedCornerShape(
                        topStart = pillRadius,
                        bottomStart = pillRadius,
                        topEnd = innerRadius,
                        bottomEnd = innerRadius
                    )
                )
                .clickable(onClick = onClick)
        ) {
            Box(
                modifier = Modifier.padding(start = 18.dp, end = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                AnimatedContent(
                    targetState = PillState(pageIndex = pageIndex, iconRes = iconRes, title = title),
                    transitionSpec = {
                        val direction = targetState.pageIndex.compareTo(initialState.pageIndex).coerceIn(-1, 1)
                        val slideIn = slideInHorizontally { fullWidth -> if (direction >= 0) fullWidth else -fullWidth } + fadeIn()
                        val slideOut = slideOutHorizontally { fullWidth -> if (direction >= 0) -fullWidth else fullWidth } + fadeOut()
                        slideIn.togetherWith(slideOut)
                    },
                    label = "LibraryPillTitle"
                ) { targetState ->
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = targetState.iconRes),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = targetState.title,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 26.sp),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        // --- PARTE 2: FLECHA (Cambia de forma segÃƒÂºn estado) ---
        Surface(
            shape = RoundedCornerShape(
                topStart = animatedArrowCorner, // Anima entre 4.dp y 26.dp
                bottomStart = animatedArrowCorner, // Anima entre 4.dp y 26.dp
                topEnd = pillRadius,
                bottomEnd = pillRadius
            ),
            tonalElevation = 8.dp,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .fillMaxHeight()
                .clip(
                    RoundedCornerShape(
                        topStart = animatedArrowCorner, // Anima entre 4.dp y 26.dp
                        bottomStart = animatedArrowCorner, // Anima entre 4.dp y 26.dp
                        topEnd = pillRadius,
                        bottomEnd = pillRadius
                    )
                )
                .clickable(
                    indication = ripple(),
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onArrowClick
                )
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .width(36.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    modifier = Modifier.rotate(arrowRotation),
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = "Expandir menÃƒÂº",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryTabSwitcherSheet(
    tabs: List<String>,
    currentIndex: Int,
    onTabSelected: (Int) -> Unit,
    onEditClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Library tabs",
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = GoogleSansRounded
            )
            Text(
                text = "Jump directly to any tab or reorder them.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 150.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp)
            ) {
                itemsIndexed(tabs, key = { index, tab -> "$tab-$index" }) { index, rawId ->
                    val tabId = rawId.toLibraryTabIdOrNull() ?: return@itemsIndexed
                    LibraryTabGridItem(
                        tabId = tabId,
                        isSelected = index == currentIndex,
                        onClick = { onTabSelected(index) }
                    )
                }

                item(
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    Box(
                       modifier = Modifier
                           .fillMaxWidth()
                           .heightIn(min = 46.dp, max = 60.dp)
                    ) {
                        FilledTonalButton(
                            onClick = onEditClick,
                            shape = CircleShape,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            ),
                            modifier = Modifier
                                .fillMaxHeight()
                                .align(Alignment.CenterEnd)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Reorder tabs")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryTabGridItem(
    tabId: LibraryTabId,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val iconContainer = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        color = containerColor,
        tonalElevation = if (isSelected) 6.dp else 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(iconContainer.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = tabId.iconRes()),
                    contentDescription = tabId.title,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Text(
                text = tabId.displayTitle(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = textColor
            )
        }
    }
}

private fun LibraryTabId.iconRes(): Int = when (this) {
    LibraryTabId.Tracks -> R.drawable.rounded_Audiobook_note_24
    LibraryTabId.Books -> R.drawable.rounded_Book_24
    LibraryTabId.Authors -> R.drawable.rounded_Author_24
    LibraryTabId.Booklists -> R.drawable.rounded_Booklist_play_24
    LibraryTabId.FOLDERS -> R.drawable.rounded_folder_24
    LibraryTabId.LIKED -> R.drawable.rounded_favorite_24
}

private fun LibraryTabId.displayTitle(): String =
    title.lowercase().replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString()
    }

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LibraryFoldersTab(
    folders: ImmutableList<AudiobookFolder>,
    currentFolder: AudiobookFolder?,
    isLoading: Boolean,
    onNavigateBack: () -> Unit,
    onFolderClick: (String) -> Unit,
    onFolderAsBooklistClick: (AudiobookFolder) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    stablePlayerState: StablePlayerState,
    bottomBarHeight: Dp,
    onMoreOptionsClick: (Track) -> Unit,
    isBooklistView: Boolean = false,
    currentSortOption: SortOption = SortOption.FolderNameAZ,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    // List state moved inside AnimatedContent to prevent state sharing issues during transitions


    AnimatedContent(
        targetState = Pair(isBooklistView, currentFolder?.path ?: "root"),
        label = "FolderNavigation",
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            (slideInHorizontally { width -> width } + fadeIn())
                .togetherWith(slideOutHorizontally { width -> -width } + fadeOut())
        }
    ) { (BooklistMode, targetPath) ->
        // Each navigation destination gets its own independant ListState
        val listState = rememberLazyListState()
        
        // Scroll to top when sort option changes
        LaunchedEffect(currentSortOption) {
            listState.scrollToItem(0)
        }

        val flattenedFolders = remember(folders, currentSortOption) {
            val flattened = flattenFolders(folders)
            when (currentSortOption) {
                SortOption.FolderNameZA -> flattened.sortedByDescending { it.name.lowercase() }
                else -> flattened.sortedBy { it.name.lowercase() }
            }
        }

        val isRoot = targetPath == "root"
        val activeFolder = if (isRoot) null else currentFolder
        val showBooklistCards = BooklistMode && activeFolder == null
        val itemsToShow = remember(activeFolder, folders, flattenedFolders, currentSortOption) {
            when {
                showBooklistCards -> flattenedFolders
                activeFolder != null -> {
                    when (currentSortOption) {
                        SortOption.FolderNameZA -> activeFolder.subFolders.sortedByDescending { it.name.lowercase() }
                        else -> activeFolder.subFolders.sortedBy { it.name.lowercase() }
                    }
                }
                else -> {
                     when (currentSortOption) {
                        SortOption.FolderNameZA -> folders.sortedByDescending { it.name.lowercase() }
                        else -> folders.sortedBy { it.name.lowercase() }
                    }
                }
            }
        }.toImmutableList()

        val TracksToShow = remember(activeFolder, currentSortOption) {
            val Tracks = activeFolder?.Tracks ?: emptyList()
            when (currentSortOption) {
                SortOption.FolderNameZA -> Tracks.sortedByDescending { it.title.lowercase() }
                else -> Tracks.sortedBy { it.title.lowercase() }
            }
        }.toImmutableList()
        val shouldShowLoading = isLoading && itemsToShow.isEmpty() && TracksToShow.isEmpty() && isRoot

        Column(modifier = Modifier.fillMaxSize()) {
            when {
                shouldShowLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                }

                itemsToShow.isEmpty() && TracksToShow.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_folder),
                                contentDescription = null,
                                Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                            Text(
                                "No folders found.",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                else -> {
                    val foldersPullToRefreshState = rememberPullToRefreshState()
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        state = foldersPullToRefreshState,
                        modifier = Modifier.fillMaxSize(),
                        indicator = {
                            PullToRefreshDefaults.LoadingIndicator(
                                state = foldersPullToRefreshState,
                                isRefreshing = isRefreshing,
                                modifier = Modifier.align(Alignment.TopCenter)
                            )
                        }
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .fillMaxSize()
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
                            contentPadding = PaddingValues(
                                bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap,
                                top = 0.dp                            )
                        ) {
                            if (showBooklistCards) {
                                items(itemsToShow, key = { "folder_${it.path}" }) { folder ->
                                    FolderBooklistItem(
                                        folder = folder,
                                        onClick = { onFolderAsBooklistClick(folder) }
                                    )
                                }
                            } else {
                                items(itemsToShow, key = { "folder_${it.path}" }) { folder ->
                                    FolderListItem(
                                        folder = folder,
                                        onClick = { onFolderClick(folder.path) }
                                    )
                                }
                            }

                            items(TracksToShow, key = { "Track_${it.id}" }) { Track ->
                                EnhancedTrackListItem(
                                    Track = Track,
                                    isPlaying = stablePlayerState.currentTrack?.id == Track.id && stablePlayerState.isPlaying,
                                    isCurrentTrack = stablePlayerState.currentTrack?.id == Track.id,
                                    onMoreOptionsClick = { onMoreOptionsClick(Track) },
                                    onClick = {
                                        val TrackIndex = TracksToShow.indexOf(Track)
                                        if (TrackIndex != -1) {
                                            val TracksToPlay =
                                                TracksToShow.subList(TrackIndex, TracksToShow.size)
                                                    .toList()
                                            onPlayTrack(Track, TracksToPlay)
                                        }
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

@Composable
fun FolderBooklistItem(folder: AudiobookFolder, onClick: () -> Unit) {
    val previewTracks = remember(folder) { folder.collectAllTracks().take(9) }

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BooklistArtCollage(
                Tracks = previewTracks,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    folder.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${folder.totalTrackCount} Tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun FolderListItem(folder: AudiobookFolder, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                painter = painterResource(id = R.drawable.ic_folder),
                contentDescription = "Folder",
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    .padding(8.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(folder.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${folder.totalTrackCount} Tracks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun flattenFolders(folders: List<AudiobookFolder>): List<AudiobookFolder> {
    return folders.flatMap { folder ->
        val current = if (folder.Tracks.isNotEmpty()) listOf(folder) else emptyList()
        current + flattenFolders(folder.subFolders)
    }
}

private fun AudiobookFolder.collectAllTracks(): List<Track> {
    return Tracks + subFolders.flatMap { it.collectAllTracks() }
}

// NUEVA PestaÃƒÂ±a para Favoritos
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LibraryFavoritesTab(
    favoriteTracks: List<Track>, // This is already StateFlow<ImmutableList<Track>> from ViewModel
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    onMoreOptionsClick: (Track) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
    val listState = rememberLazyListState()

    // Scroll to top when the list changes due to sorting
    LaunchedEffect(favoriteTracks) {
        if (favoriteTracks.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    // No need to collect favoriteTracks again if it's passed directly as a list
    // However, if you need to react to its changes, ensure it's collected or passed as StateFlow's value

    if (favoriteTracks.isEmpty()) {
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.FavoriteBorder, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("No liked Tracks yet.", style = MaterialTheme.typography.titleMedium)
                Text("Touch the heart icon in the player to add Tracks.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        }
    } else {
        Box(modifier = Modifier
            .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val TracksPullToRefreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                state = TracksPullToRefreshState,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = TracksPullToRefreshState,
                        isRefreshing = isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            ) {
                LazyColumn(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
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
                    items(favoriteTracks, key = { "fav_${it.id}" }) { Track ->
                        val isPlayingThisTrack =
                            Track.id == stablePlayerState.currentTrack?.id && stablePlayerState.isPlaying
                        EnhancedTrackListItem(
                            Track = Track,
                            isCurrentTrack = stablePlayerState.currentTrack?.id == Track.id,
                            isPlaying = isPlayingThisTrack,
                            onMoreOptionsClick = { onMoreOptionsClick(Track) },
                            onClick = {
                                playerViewModel.showAndPlayTrack(
                                    Track,
                                    favoriteTracks,
                                    "Liked Tracks"
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LibraryTracksTab(
    Tracks: ImmutableList<Track>,
    isLoadingInitial: Boolean,
    // isLoadingMore: Boolean, // Removed
    // canLoadMore: Boolean, // Removed
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    onMoreOptionsClick: (Track) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    val stablePlayerState by playerViewModel.stablePlayerState.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val imageLoader = context.imageLoader

    // Prefetching logic for LibraryTracksTab
    LaunchedEffect(Tracks, listState) {
        snapshotFlow { listState.layoutInfo }
            .distinctUntilChanged()
            .collect { layoutInfo ->
                val visibleItemsInfo = layoutInfo.visibleItemsInfo
                if (visibleItemsInfo.isNotEmpty() && Tracks.isNotEmpty()) {
                    val lastVisibleItemIndex = visibleItemsInfo.last().index
                    val totalItemsCount = Tracks.size
                    val prefetchThreshold = 10 // Start prefetching when 10 items are left
                    val prefetchCount = 20    // Prefetch next 20 items

                    if (totalItemsCount > lastVisibleItemIndex + 1 && lastVisibleItemIndex + prefetchThreshold >= totalItemsCount - prefetchCount ) {
                         val startIndexToPrefetch = lastVisibleItemIndex + 1
                         val endIndexToPrefetch = (startIndexToPrefetch + prefetchCount).coerceAtMost(totalItemsCount)

                        (startIndexToPrefetch until endIndexToPrefetch).forEach { indexToPrefetch ->
                            val Track = Tracks.getOrNull(indexToPrefetch)
                            Track?.BookArtUriString?.let { uri ->
                                val request = ImageRequest.Builder(context)
                                    .data(uri)
                                    .size(Size(168, 168)) // Same size as in EnhancedTrackListItem
                                    .build()
                                imageLoader.enqueue(request)
                            }
                        }
                    }
                }
            }
    }

    if (isLoadingInitial && Tracks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingIndicator() // O Shimmer para la lista completa
        }
    } else {
        // Determine content based on loading state and data availability
        when {
            isLoadingInitial && Tracks.isEmpty() -> { // Este caso ya estÃƒÂ¡ cubierto arriba, pero es bueno para claridad
                val allTracksPullToRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    state = allTracksPullToRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = allTracksPullToRefreshState,
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
                            )
                            .fillMaxSize(),
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap)
                    ) {
                        items(15) {
                            EnhancedTrackListItem(
                                Track = Track.emptyTrack(), isPlaying = false, isLoading = true,
                                isCurrentTrack = Tracks.isNotEmpty() && stablePlayerState.currentTrack == Track.emptyTrack(),
                                onMoreOptionsClick = {}, onClick = {}
                            )
                        }
                    }
                }
            }

            Tracks.isEmpty() && !isLoadingInitial -> { // canLoadMore removed from condition
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
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
                Box(modifier = Modifier.fillMaxSize()) {
                    val AuthorsPullToRefreshState = rememberPullToRefreshState()
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        state = AuthorsPullToRefreshState,
                        modifier = Modifier.fillMaxSize(),
                        indicator = {
                            PullToRefreshDefaults.LoadingIndicator(
                                state = AuthorsPullToRefreshState,
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
                            items(Tracks, key = { "Track_${it.id}" }) { Track ->
                                val isPlayingThisTrack =
                                    Track.id == stablePlayerState.currentTrack?.id && stablePlayerState.isPlaying

                                // Estabilizar lambdas
                                val rememberedOnMoreOptionsClick: (Track) -> Unit =
                                    remember(onMoreOptionsClick) {
                                        // Esta es la lambda que `remember` ejecutarÃƒÂ¡ para producir el valor recordado.
                                        // El valor recordado es la propia funciÃƒÂ³n `onMoreOptionsClick` (o una lambda que la llama).
                                        { TrackFromListItem -> // Esta es la lambda (Track) -> Unit que se recuerda
                                            onMoreOptionsClick(TrackFromListItem)
                                        }
                                    }
                                val rememberedOnClick: () -> Unit = remember(Track) {
                                    { playerViewModel.showAndPlayTrack(Track) }
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
                            // isLoadingMore indicator removed as all Tracks are loaded at once.
                            // if (isLoadingMore) {
                            //     item {
                            //         Box(
                            //             Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            //             contentAlignment = Alignment.Center
                            //         ) { CircularProgressIndicator() }
                            //     }
                            // }
                        }
                    }
                }
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

/**
 * Paginated version of LibraryTracksTab using Paging 3 for efficient memory usage.
 * Displays Tracks in pages, loading only what's needed for the current viewport.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LibraryTracksTabPaginated(
    paginatedTracks: androidx.paging.compose.LazyPagingItems<Track>,
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
        paginatedTracks.loadState.refresh is LoadState.Loading && paginatedTracks.itemCount == 0 -> {
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
                items(12) { // Show 12 skeleton items to fill the screen
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
        paginatedTracks.loadState.refresh is LoadState.Error && paginatedTracks.itemCount == 0 -> {
            // Error state
            val error = (paginatedTracks.loadState.refresh as LoadState.Error).error
            Box(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Error loading Tracks", style = MaterialTheme.typography.titleMedium)
                    Text(
                        error.localizedMessage ?: "Unknown error",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { paginatedTracks.retry() }) {
                        Text("Retry")
                    }
                }
            }
        }
        paginatedTracks.itemCount == 0 && paginatedTracks.loadState.refresh is LoadState.NotLoading -> {
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
            // Tracks loaded - show paginated list
            Box(modifier = Modifier.fillMaxSize()) {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        onRefresh()
                        paginatedTracks.refresh()
                    },
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
                            count = paginatedTracks.itemCount,
                            key = paginatedTracks.itemKey { "Track_${it.id}" },
                            contentType = paginatedTracks.itemContentType { "Track" }
                        ) { index ->
                            val Track = paginatedTracks[index]
                            if (Track != null) {
                                val isPlayingThisTrack = Track.id == stablePlayerState.currentTrack?.id && stablePlayerState.isPlaying
                                
                                val rememberedOnMoreOptionsClick: (Track) -> Unit = remember(onMoreOptionsClick) {
                                    { TrackFromListItem -> onMoreOptionsClick(TrackFromListItem) }
                                }
                                val rememberedOnClick: () -> Unit = remember(Track) {
                                    { playerViewModel.showAndPlayTrack(Track) }
                                }
                                
                                EnhancedTrackListItem(
                                    Track = Track,
                                    isPlaying = isPlayingThisTrack,
                                    isCurrentTrack = stablePlayerState.currentTrack?.id == Track.id,
                                    isLoading = false,
                                    onMoreOptionsClick = rememberedOnMoreOptionsClick,
                                    onClick = rememberedOnClick
                                )
                            } else {
                                // Placeholder while loading
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
                        
                        // Loading indicator for appending more items
                        if (paginatedTracks.loadState.append is LoadState.Loading) {
                            item {
                                Box(
                                    Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    LoadingIndicator(modifier = Modifier.size(32.dp))
                                }
                            }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedTrackListItem(
    modifier: Modifier = Modifier,
    Track: Track,
    isPlaying: Boolean,
    isCurrentTrack: Boolean = false,
    isLoading: Boolean = false,
    onMoreOptionsClick: (Track) -> Unit,
    onClick: () -> Unit
) {
    // Animamos el radio de las esquinas basÃƒÂ¡ndonos en si la canciÃƒÂ³n es la actual.
    val animatedCornerRadius by animateDpAsState(
        targetValue = if (isCurrentTrack && !isLoading) 50.dp else 22.dp,
        animationSpec = tween(durationMillis = 400),
        label = "cornerRadiusAnimation"
    )

    val animatedBookCornerRadius by animateDpAsState(
        targetValue = if (isCurrentTrack && !isLoading) 50.dp else 12.dp,
        animationSpec = tween(durationMillis = 400),
        label = "cornerRadiusAnimation"
    )

    val surfaceShape = remember(animatedCornerRadius) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = animatedCornerRadius,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = animatedCornerRadius,
            smoothnessAsPercentBR = 60,
            cornerRadiusBL = animatedCornerRadius,
            smoothnessAsPercentBL = 60,
            cornerRadiusBR = animatedCornerRadius,
            smoothnessAsPercentTL = 60
        )
    }

    val Bookshape = remember(animatedCornerRadius) {
        AbsoluteSmoothCornerShape(
            cornerRadiusTL = animatedBookCornerRadius,
            smoothnessAsPercentTR = 60,
            cornerRadiusTR = animatedBookCornerRadius,
            smoothnessAsPercentBR = 60,
            cornerRadiusBL = animatedBookCornerRadius,
            smoothnessAsPercentBL = 60,
            cornerRadiusBR = animatedBookCornerRadius,
            smoothnessAsPercentTL = 60
        )
    }

    val colors = MaterialTheme.colorScheme
    val containerColor = if ((isCurrentTrack) && !isLoading) colors.primaryContainer else colors.surfaceContainerLow
    val contentColor = if ((isCurrentTrack) && !isLoading) colors.onPrimaryContainer else colors.onSurface

    val mvContainerColor = if ((isCurrentTrack) && !isLoading) colors.primaryContainer else colors.onSurface
    val mvContentColor = if ((isCurrentTrack) && !isLoading) colors.onPrimaryContainer else colors.surfaceContainerHigh

    if (isLoading) {
        // Shimmer Placeholder Layout
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clip(surfaceShape),
            shape = surfaceShape,
            color = colors.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                ShimmerBox(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                )
            }
        }
    } else {
        // Actual Track Item Layout
        var applyTextMarquee by remember { mutableStateOf(false) }

        Surface(
            modifier = modifier
                .fillMaxWidth()
                .clip(surfaceShape)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { applyTextMarquee = !applyTextMarquee },
                        onPress = {
                            try {
                                awaitRelease()
                            } finally {
                                applyTextMarquee = false
                            }
                        })
                },
            shape = surfaceShape,
            color = containerColor,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 13.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    // Usando tu composable SmartImage
                    SmartImage(
                        model = Track.BookArtUriString,
                        contentDescription = Track.title,
                        shape = Bookshape,
                        targetSize = Size(168, 168), // 56dp * 3 (para densidad xxhdpi)
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp)
                ) {
                    if (applyTextMarquee) {
                        AutoScrollingTextOnDemand(
                            text = Track.title,
                            style = MaterialTheme.typography.bodyLarge,
                            gradientEdgeColor = containerColor,
                            expansionFractionProvider = { 1f },
                        )

                    } else {
                        Text(
                            text = Track.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            color = contentColor,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = Track.displayAuthor,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isCurrentTrack) {
                     PlayingEqIcon(
                         modifier = Modifier
                             .padding(start = 8.dp)
                             .size(width = 18.dp, height = 16.dp),
                         color = contentColor,
                         isPlaying = isPlaying
                     )
                }
                Spacer(modifier = Modifier.width(12.dp))
                FilledIconButton(
                    onClick = { onMoreOptionsClick(Track) },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = mvContentColor,
                        contentColor = mvContainerColor
                    ),
                    modifier = Modifier
                        .size(36.dp)
                        .padding(end = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "More options for ${Track.title}",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LibraryBooksTab(
    Books: ImmutableList<Book>,
    isLoading: Boolean,
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    onBookClick: (Long) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    val gridState = rememberLazyGridState()
    val context = LocalContext.current
    val imageLoader = context.imageLoader

    // Prefetching logic for LibraryBooksTab
    LaunchedEffect(Books, gridState) {
        snapshotFlow { gridState.layoutInfo }
            .distinctUntilChanged()
            .collect { layoutInfo ->
                val visibleItemsInfo = layoutInfo.visibleItemsInfo
                if (visibleItemsInfo.isNotEmpty() && Books.isNotEmpty()) {
                    val lastVisibleItemIndex = visibleItemsInfo.last().index
                    val totalItemsCount = Books.size
                    val prefetchThreshold = 5 // Start prefetching when 5 items are left to be displayed from current visible ones
                    val prefetchCount = 10 // Prefetch next 10 items

                    if (totalItemsCount > lastVisibleItemIndex + 1 && lastVisibleItemIndex + prefetchThreshold >= totalItemsCount - prefetchCount) {
                        val startIndexToPrefetch = lastVisibleItemIndex + 1
                        val endIndexToPrefetch = (startIndexToPrefetch + prefetchCount).coerceAtMost(totalItemsCount)

                        (startIndexToPrefetch until endIndexToPrefetch).forEach { indexToPrefetch ->
                            val Book = Books.getOrNull(indexToPrefetch)
                            Book?.BookArtUriString?.let { uri ->
                                val request = ImageRequest.Builder(context)
                                    .data(uri)
                                    .size(Size(256, 256)) // Same size as in BookGridItemRedesigned
                                    .build()
                                imageLoader.enqueue(request)
                            }
                        }
                    }
                }
            }
    }

    if (isLoading && Books.isEmpty()) {
        // Show skeleton grid during loading
        LazyVerticalGrid(
            modifier = Modifier
                .padding(start = 14.dp, end = 14.dp, bottom = 6.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 26.dp,
                        topEnd = 26.dp,
                        bottomStart = PlayerSheetCollapsedCornerRadius,
                        bottomEnd = PlayerSheetCollapsedCornerRadius
                    )
                )
                .fillMaxSize(),
            state = gridState,
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap + 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item(key = "skeleton_top_spacer", span = { GridItemSpan(maxLineSpan) }) {
                Spacer(Modifier.height(4.dp))
            }
            items(8) { // Show 8 skeleton items (4 rows x 2 columns)
                BookGridItemRedesigned(
                    Book = Book.empty(),
                    BookColorSchemePairFlow = MutableStateFlow(null),
                    onClick = {},
                    isLoading = true
                )
            }
        }
    } else if (Books.isEmpty() && !isLoading) { // canLoadMore removed
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Book, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                Text("No Books found.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            val BooksPullToRefreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                state = BooksPullToRefreshState,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = BooksPullToRefreshState,
                        isRefreshing = isRefreshing,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }
            ) {
                LazyVerticalGrid(
                    modifier = Modifier
                        .padding(start = 14.dp, end = 14.dp, bottom = 6.dp)
                        .clip(
                            RoundedCornerShape(
                                topStart = 26.dp,
                                topEnd = 26.dp,
                                bottomStart = PlayerSheetCollapsedCornerRadius,
                                bottomEnd = PlayerSheetCollapsedCornerRadius
                            )
                        ),
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap + 4.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item(key = "Books_top_spacer", span = { GridItemSpan(maxLineSpan) }) {
                        Spacer(Modifier.height(4.dp))
                    }
                    items(Books, key = { "Book_${it.id}" }) { Book ->
                        val BookspecificColorSchemeFlow =
                            playerViewModel.themeStateHolder.getBookColorSchemeFlow(Book.BookArtUriString ?: "")
                        val rememberedOnClick = remember(Book.id) { { onBookClick(Book.id) } }
                        BookGridItemRedesigned(
                            Book = Book,
                            BookColorSchemePairFlow = BookspecificColorSchemeFlow,
                            onClick = rememberedOnClick,
                            isLoading = isLoading && Books.isEmpty() // Shimmer solo si estÃƒÂ¡ cargando Y la lista estÃƒÂ¡ vacÃƒÂ­a
                        )
                    }
                }
            }
//            Box(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .height(14.dp)
//                    .background(
//                        brush = Brush.verticalGradient(
//                            colors = listOf(MaterialTheme.colorScheme.surface, Color.Transparent)
//                        )
//                    )
//                    .align(Alignment.TopCenter)
//            )
        }
    }
}

@Composable
fun BookGridItemRedesigned(
    Book: Book,
    BookColorSchemePairFlow: StateFlow<ColorSchemePair?>,
    onClick: () -> Unit,
    isLoading: Boolean = false
) {
    val BookColorSchemePair by BookColorSchemePairFlow.collectAsState()
    val systemIsDark = LocalAudioBookPlayerDarkTheme.current

    // 1. ObtÃƒÂ©n el colorScheme del tema actual aquÃƒÂ­, en el scope Composable.
    val currentMaterialColorScheme = MaterialTheme.colorScheme

    val itemDesignColorScheme = remember(BookColorSchemePair, systemIsDark, currentMaterialColorScheme) {
        // 2. Ahora, currentMaterialColorScheme es una variable estable que puedes usar.
        BookColorSchemePair?.let { pair ->
            if (systemIsDark) pair.dark else pair.light
        } ?: currentMaterialColorScheme // Usa la variable capturada
    }

    val gradientBaseColor = itemDesignColorScheme.primaryContainer
    val onGradientColor = itemDesignColorScheme.onPrimaryContainer
    val cardCornerRadius = 20.dp

    if (isLoading) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(cardCornerRadius),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.background(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(cardCornerRadius)
                )
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .aspectRatio(3f / 2f)
                        .fillMaxSize()
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.4f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    } else {
        Card(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(cardCornerRadius),
            //elevation = CardDefaults.cardElevation(defaultElevation = 4.dp, pressedElevation = 8.dp),
            colors = CardDefaults.cardColors(containerColor = itemDesignColorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.background(
                    color = gradientBaseColor,
                    shape = RoundedCornerShape(cardCornerRadius)
                )
            ) {
                Box(contentAlignment = Alignment.BottomStart) {
                    var isLoadingImage by remember { mutableStateOf(true) }
                    SmartImage(
                        model = Book.BookArtUriString,
                        contentDescription = "CarÃƒÂ¡tula de ${Book.title}",
                        contentScale = ContentScale.Crop,
                            // Reducido el tamaÃƒÂ±o para mejorar el rendimiento del scroll, como se sugiere en el informe.
                            // ContentScale.Crop se encargarÃƒÂ¡ de ajustar la imagen al aspect ratio.
                            targetSize = Size(256, 256),
                        modifier = Modifier
                            .aspectRatio(3f / 2f)
                            .fillMaxSize(),
                        onState = { state ->
                            isLoadingImage = state is AsyncImagePainter.State.Loading
                        }
                    )
                    if (isLoadingImage) {
                        ShimmerBox(
                            modifier = Modifier
                                .aspectRatio(3f / 2f)
                                .fillMaxSize()
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .aspectRatio(3f / 2f)
                            .background(
                                remember(gradientBaseColor) { // Recordar el Brush
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent, gradientBaseColor
                                        )
                                    )
                                })
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        Book.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = onGradientColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(Book.Author, style = MaterialTheme.typography.bodySmall, color = onGradientColor.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${Book.TrackCount} Tracks", style = MaterialTheme.typography.bodySmall, color = onGradientColor.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LibraryAuthorsTab(
    Authors: ImmutableList<Author>,
    isLoading: Boolean, // This now represents the loading state for all Authors
    // canLoadMore: Boolean, // Removed
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    onAuthorClick: (Long) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    val listState = rememberLazyListState()
    if (isLoading && Authors.isEmpty()) {
        // Show skeleton list during loading
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
            item(key = "skeleton_top_spacer") { Spacer(Modifier.height(4.dp)) }
            items(10) { // Show 10 skeleton items
                AuthorListItem(
                    Author = Author.empty(),
                    onClick = {},
                    isLoading = true
                )
            }
        }
    }
    else if (Authors.isEmpty() && !isLoading) { /* ... No Authors ... */ } // canLoadMore removed
    else {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            val CategoriesPullToRefreshState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = onRefresh,
                state = CategoriesPullToRefreshState,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    PullToRefreshDefaults.LoadingIndicator(
                        state = CategoriesPullToRefreshState,
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
                    contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + ListExtraBottomGap)
                ) {
                    item(key = "Authors_top_spacer") {
                        Spacer(Modifier.height(4.dp))
                    }
                    items(Authors, key = { "Author_${it.id}" }) { Author ->
                        val rememberedOnClick = remember(Author) { { onAuthorClick(Author.id) } }
                        AuthorListItem(Author = Author, onClick = rememberedOnClick)
                    }
                    // "Load more" indicator removed as all Authors are loaded at once
                    // if (isLoading && Authors.isNotEmpty()) {
                    //     item { Box(Modifier
                    //         .fillMaxWidth()
                    //         .padding(16.dp), Alignment.Center) { CircularProgressIndicator() } }
                    // }
                }
            }
//            Box(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .height(10.dp)
//                    .background(
//                        brush = Brush.verticalGradient(
//                            colors = listOf(MaterialTheme.colorScheme.surface, Color.Transparent)
//                        )
//                    )
//                    .align(Alignment.TopCenter)
//            )
        }
    }
}

@Composable
fun AuthorListItem(Author: Author, onClick: () -> Unit, isLoading: Boolean = false) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (isLoading) {
                // Skeleton loading state
                ShimmerBox(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth(0.3f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (!Author.imageUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(Author.imageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = Author.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.rounded_Author_24),
                            contentDescription = "Authora",
                            modifier = Modifier.padding(8.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(Author.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${Author.TrackCount} Tracks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun LibraryBooklistsTab(
    BooklistUiState: BooklistUiState,
    navController: NavController,
    playerViewModel: PlayerViewModel,
    bottomBarHeight: Dp,
    onGenerateWithAiClick: () -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    BooklistContainer(
        BooklistUiState = BooklistUiState,
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        bottomBarHeight = bottomBarHeight,
        navController = navController,
        playerViewModel = playerViewModel,
    )
}
