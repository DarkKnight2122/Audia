package com.oakiha.audia.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.size.Size
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.ui.theme.GoogleSansRounded
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackPickerBottomSheet(
    allTracks: List<Track>,
    isLoading: Boolean,
    initiallySelectedTrackIds: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedTrackIds = remember {
        mutableStateMapOf<String, Boolean>().apply {
            initiallySelectedTrackIds.forEach { put(it, true) }
        }
    }
    var searchQuery by remember { mutableStateOf("") }
    val filteredTracks = remember(searchQuery, allTracks) {
        if (searchQuery.isBlank()) allTracks
        else allTracks.filter {
            it.title.contains(searchQuery, true) || it.Author.contains(
                searchQuery,
                true
            )
        }
    }

    val animatedBookCornerRadius = 60.dp

    val Bookshape = remember(animatedBookCornerRadius) {
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxSize()
    ) {
        TrackPickerContent(
            filteredTracks = filteredTracks,
            isLoading = isLoading,
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            selectedTrackIds = selectedTrackIds,
            Bookshape = Bookshape,
            onConfirm = onConfirm
        )
    }
}

@Composable
fun TrackPickerContent(
    filteredTracks: List<Track>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedTrackIds: MutableMap<String, Boolean>,
    Bookshape: androidx.compose.ui.graphics.Shape,
    onConfirm: (Set<String>) -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            topBar = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 26.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Add Tracks",
                            style = MaterialTheme.typography.displaySmall,
                            fontFamily = GoogleSansRounded
                        )
                    }
                    OutlinedTextField(
                        value = searchQuery,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            unfocusedTrailingIconColor = Color.Transparent,
                            focusedSupportingTextColor = Color.Transparent,
                        ),
                        onValueChange = onSearchQueryChange,
                        label = { Text("Search for Tracks...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        shape = CircleShape,
                        singleLine = true,
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) IconButton(onClick = {
                                onSearchQueryChange("")
                            }) { Icon(Icons.Filled.Clear, null) }
                        }
                    )
                }
            },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    modifier = Modifier.padding(bottom = 18.dp, end = 8.dp),
                    shape = CircleShape,
                    onClick = { onConfirm(selectedTrackIds.filterValues { it }.keys) },
                    icon = { Icon(Icons.Rounded.Check, "AÃ±adir canciones") },
                    text = { Text("Add") },
                )
            }
        ) { innerPadding ->
            TrackPickerList(
                filteredTracks = filteredTracks,
                isLoading = isLoading,
                selectedTrackIds = selectedTrackIds,
                Bookshape = Bookshape,
                modifier = Modifier.padding(innerPadding)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(30.dp)
                .background(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
        ) {

        }
    }
}

@Composable
fun TrackPickerList(
    filteredTracks: List<Track>,
    isLoading: Boolean,
    selectedTrackIds: MutableMap<String, Boolean>,
    Bookshape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 100.dp, top = 20.dp)
) {
    if (isLoading) {
        Box(
            modifier
                .fillMaxSize(), Alignment.Center
        ) { CircularProgressIndicator() }
    } else {
        LazyColumn(
            modifier = modifier
                .padding(horizontal = 14.dp),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filteredTracks, key = { it.id }) { Track ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(CircleShape)
                        .clickable {
                            val currentSelection = selectedTrackIds[Track.id] ?: false
                            selectedTrackIds[Track.id] = !currentSelection
                        }
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainerLowest,
                            shape = CircleShape
                        )
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = selectedTrackIds[Track.id] ?: false,
                        onCheckedChange = { isChecked ->
                            selectedTrackIds[Track.id] = isChecked
                        }
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                CircleShape
                            )
                    ) {
                        SmartImage(
                            model = Track.BookArtUriString,
                            contentDescription = Track.title,
                            shape = Bookshape,
                            targetSize = Size(
                                168,
                                168
                            ), // 56dp * 3 (para densidad xxhdpi)
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(Track.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            Track.displayAuthor,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
