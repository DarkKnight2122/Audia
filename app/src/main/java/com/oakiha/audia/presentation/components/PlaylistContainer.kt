package com.oakiha.audia.presentation.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.filled.AudiobookNote
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.MicExternalOn
import androidx.compose.material.icons.outlined.AudiobookVideo
import androidx.compose.material.icons.outlined.Piano
import androidx.compose.material.icons.outlined.QueueAudiobook
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material.icons.automirrored.rounded.BooklistPlay
import com.oakiha.audia.utils.getContrastColor
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.oakiha.audia.R
import com.oakiha.audia.data.model.Booklist
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.presentation.components.subcomps.SineWaveLine
import com.oakiha.audia.presentation.navigation.Screen
import com.oakiha.audia.presentation.screens.PlayerSheetCollapsedCornerRadius
import com.oakiha.audia.presentation.viewmodel.PlayerViewModel
import com.oakiha.audia.presentation.viewmodel.BooklistUiState
import com.oakiha.audia.data.model.BooklistshapeType
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import com.oakiha.audia.utils.shapes.RoundedStarShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.MicExternalOn
import androidx.compose.material.icons.rounded.AudiobookNote
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.QueueAudiobook
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import com.oakiha.audia.ui.theme.GoogleSansRounded
import kotlin.collections.set

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BooklistContainer(
    BooklistUiState: BooklistUiState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    bottomBarHeight: Dp,
    currentTrack: Track? = null,
    navController: NavController?,
    playerViewModel: PlayerViewModel,
    isAddingToBooklist: Boolean = false,
    selectedBooklists: SnapshotStateMap<String, Boolean>? = null,
    filteredBooklists: List<Booklist> = BooklistUiState.Booklists
) {

    Column(modifier = Modifier.fillMaxSize()) {
        if (BooklistUiState.isLoading && filteredBooklists.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
        }

        if (filteredBooklists.isEmpty() && !BooklistUiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier.padding(top = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    SineWaveLine(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp)
                            .padding(horizontal = 8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
                        alpha = 0.95f,
                        strokeWidth = 3.dp,
                        amplitude = 4.dp,
                        waves = 7.6f,
                        phase = 0f
                    )
                    Spacer(Modifier.height(16.dp))
                    Icon(
                        Icons.AutoMirrored.Rounded.BooklistPlay,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No Booklist has been created.",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Touch the 'New Booklist' button to start.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            if (isAddingToBooklist) {
                BooklistItems(
                    currentTrack = currentTrack,
                    bottomBarHeight = bottomBarHeight,
                    navController = navController,
                    playerViewModel = playerViewModel,
                    isAddingToBooklist = true,
                    filteredBooklists = filteredBooklists,
                    selectedBooklists = selectedBooklists
                )
            } else {
                val BooklistPullToRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = onRefresh,
                    state = BooklistPullToRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.LoadingIndicator(
                            state = BooklistPullToRefreshState,
                            isRefreshing = isRefreshing,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                ) {
                    BooklistItems(
                        bottomBarHeight = bottomBarHeight,
                        navController = navController,
                        playerViewModel = playerViewModel,
                        filteredBooklists = filteredBooklists
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                Color.Transparent
                            )
                        )
                    )
                //.align(Alignment.TopCenter)
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun BooklistItems(
    bottomBarHeight: Dp,
    navController: NavController?,
    currentTrack: Track? = null,
    playerViewModel: PlayerViewModel,
    isAddingToBooklist: Boolean = false,
    filteredBooklists: List<Booklist>,
    selectedBooklists: SnapshotStateMap<String, Boolean>? = null
) {
    val listState = rememberLazyListState()

    androidx.compose.runtime.LaunchedEffect(filteredBooklists) {
        val firstVisible = listState.layoutInfo.visibleItemsInfo.firstOrNull()
        if (firstVisible != null) {
            val key = firstVisible.key
            val targetIndex = filteredBooklists.indexOfFirst { it.id == key }
            if (targetIndex >= 0) {
                listState.scrollToItem(targetIndex, firstVisible.offset)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .padding(start = 12.dp, end = 12.dp, bottom = 6.dp)
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
        contentPadding = PaddingValues(bottom = bottomBarHeight + MiniPlayerHeight + 30.dp)
    ) {
        items(filteredBooklists, key = { it.id }) { Booklist ->
            val rememberedOnClick = remember(Booklist.id) {
                {
                    if (isAddingToBooklist && currentTrack != null && selectedBooklists != null) {
                        val currentSelection = selectedBooklists[Booklist.id] ?: false
                        selectedBooklists[Booklist.id] = !currentSelection
                    } else
                        navController?.navigate(Screen.BooklistDetail.createRoute(Booklist.id))
                }
            }
            BooklistItem(
                Booklist = Booklist,
                playerViewModel = playerViewModel,
                onClick = { rememberedOnClick() },
                isAddingToBooklist = isAddingToBooklist,
                selectedBooklists = selectedBooklists
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun BooklistItem(
    Booklist: Booklist,
    playerViewModel: PlayerViewModel,
    onClick: () -> Unit,
    isAddingToBooklist: Boolean,
    selectedBooklists: SnapshotStateMap<String, Boolean>? = null
) {
    val allTracks by playerViewModel.allTracksFlow.collectAsState()
    val BooklistTracks = remember(Booklist.TrackIds, allTracks) {
        allTracks.filter { it.id in Booklist.TrackIds }
    }

    // Shape Logic
    val shape = remember(Booklist.coverShapeType, Booklist.coverShapeDetail1, Booklist.coverShapeDetail2, Booklist.coverShapeDetail3) {
        when (Booklist.coverShapeType) {
            BooklistshapeType.Circle.name -> CircleShape
            BooklistshapeType.SmoothRect.name -> {
                 // Scale radius relative to a 200dp reference (used in Creator)
                 // Current box is 48.dp
                 val referenceSize = 200f
                 val currentSize = 48f 
                 val scale = currentSize / referenceSize
                 val r = ((Booklist.coverShapeDetail1 ?: 20f) * scale).dp
                 val s = (Booklist.coverShapeDetail2 ?: 60f).toInt()
                 AbsoluteSmoothCornerShape(r, s, r, s, r, s, r, s)
            }
            BooklistshapeType.RotatedPill.name -> {
                 // Narrow Pill Shape (Capsule)
                 androidx.compose.foundation.shape.GenericShape { size, _ ->
                     val w = size.width
                     val h = size.height
                     val pillW = w * 0.75f // 75% width (Fat Pill)
                     val offset = (w - pillW) / 2
                     addRoundRect(RoundRect(offset, 0f, offset + pillW, h, CornerRadius(pillW/2, pillW/2)))
                 }
            }
            BooklistshapeType.Star.name -> RoundedStarShape(
                 sides = (Booklist.coverShapeDetail4 ?: 5f).toInt(),
                 curve = (Booklist.coverShapeDetail1 ?: 0.15f).toDouble(),
                 rotation = Booklist.coverShapeDetail2 ?: 0f
            )
            else -> RoundedCornerShape(8.dp)
        }
    }
    
    // Mods
    // For RotatedPill: We Rotate the Container (with the Shape).
    // The Icon should be counter-rotated.
    // The Image: If we rotate the container, the image rotates.
    // If the user wants an "upright" image in a "diagonal" pill, we must counter-rotate the image too.
    // In Creator: `iconMod` counter-rotates. Image didn't have counter-rotation. 
    // Usually "Rotated Shape" implies the frame is rotated. Image handles itself. 
    // Let's keep existing rotation logic but apply the Narrow Pill Shape.
    val shapeMod = if(Booklist.coverShapeType == BooklistshapeType.RotatedPill.name) Modifier.graphicsLayer(rotationZ = 45f) else Modifier
    // Counter rotate content?
    // If I rotate the container 45deg, the image is tilted.
    // If I want upright image, I apply counter-rotation to the Image.
    // Let's check Creator behavior: It didn't counter-rotate image.
    // I will stick to Creator behavior for consistency, or fix it if it looks bad.
    // Providing a rotated pill frame usually implies distinct style.
    val iconMod = if(Booklist.coverShapeType == BooklistshapeType.RotatedPill.name) Modifier.graphicsLayer(rotationZ = -45f) else Modifier
    
    val scaleMod = if(Booklist.coverShapeType == BooklistshapeType.Star.name) {
          val s = Booklist.coverShapeDetail3 ?: 1f
          Modifier.graphicsLayer(scaleX = s, scaleY = s) 
    } else Modifier

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isAddingToBooklist) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(48.dp).then(scaleMod).then(shapeMod).clip(shape)) {
               if (Booklist.coverImageUri != null) {
                   AsyncImage(
                       model = Booklist.coverImageUri,
                       contentDescription = null,
                       modifier = Modifier.fillMaxSize(),
                       contentScale = ContentScale.Crop
                   )
               } else if (Booklist.coverColorArgb != null) {
                   Box(
                       modifier = Modifier
                           .fillMaxSize()
                           .background(Color(Booklist.coverColorArgb)),
                       contentAlignment = Alignment.Center
                   ) {
                       Icon(
                            imageVector = getIconByName(Booklist.coverIconName) ?: Icons.Filled.AudiobookNote,
                            contentDescription = null,
                            tint = getContrastColor(Color(Booklist.coverColorArgb)),
                            modifier = Modifier.size(24.dp).then(iconMod)
                       )
                   }
               } else {
                    BooklistArtCollage(
                        Tracks = BooklistTracks,
                        modifier = Modifier.fillMaxSize()
                    )
               }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.padding(end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = Booklist.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = GoogleSansRounded),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (Booklist.isAiGenerated) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            painter = painterResource(R.drawable.gemini_ai),
                            contentDescription = "AI Generated",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Text(
                    text = "${Booklist.TrackIds.size} Tracks",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isAddingToBooklist && selectedBooklists != null) {
                Checkbox(
                    checked = selectedBooklists[Booklist.id] ?: false,
                    onCheckedChange = { isChecked -> selectedBooklists[Booklist.id] = isChecked }
                )
            }
        }
    }
}


private fun getIconByName(name: String?): ImageVector? {
    return when (name) {
        "AudiobookNote" -> Icons.Rounded.AudiobookNote
        "Headphones" -> Icons.Rounded.Headphones
        "Book" -> Icons.Rounded.Book
        "Mic" -> Icons.Rounded.MicExternalOn
        "Speaker" -> Icons.Rounded.Speaker
        "Favorite" -> Icons.Rounded.Favorite
        "Piano" -> Icons.Rounded.Piano
        "Queue" -> Icons.Rounded.QueueAudiobook
        else -> Icons.Rounded.AudiobookNote
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBooklistDialogRedesigned(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    onGenerateClick: () -> Unit
) {
    var BooklistName by remember { mutableStateOf("") }

    BasicAlertDialog(
        onDismissRequest = onDismiss,
    ) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "New Booklist",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = GoogleSansRounded,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = BooklistName,
                    onValueChange = { BooklistName = it },
                    label = { Text("Booklist Name") },
                    placeholder = { Text("My Booklist") },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    singleLine = true,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Generate with AI Button (New Feature Integration)
                    FilledTonalButton(
                        onClick = {
                            onDismiss()
                            onGenerateClick()
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.generate_Booklist_ai),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Generate with AI")
                    }

                    // Standard Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text("Cancel", fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = { onCreate(BooklistName) },
                            enabled = BooklistName.isNotEmpty(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Create", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
