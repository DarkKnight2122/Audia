package com.oakiha.audia.presentation.viewmodel

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.oakiha.audia.data.model.Booklist
import com.oakiha.audia.data.model.Track
import com.oakiha.audia.data.model.SortOption
import com.oakiha.audia.data.Booklist.M3uManager
import com.oakiha.audia.data.preferences.UserPreferencesRepository
import com.oakiha.audia.data.repository.AudiobookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.io.OutputStreamWriter
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

data class BooklistUiState(
    val Booklists: List<Booklist> = emptyList(),
    val currentBooklistTracks: List<Track> = emptyList(),
    val currentBooklistDetails: Booklist? = null,
    val isLoading: Boolean = false,
    val BooklistNotFound: Boolean = false,

    // Para el diÃƒÂ¡logo/pantalla de selecciÃƒÂ³n de canciones
    val TrackselectionPage: Int = 1, // Nuevo: para rastrear la pÃƒÂ¡gina actual de selecciÃƒÂ³n
    val TrackselectionForBooklist: List<Track> = emptyList(),
    val isLoadingTrackselection: Boolean = false,
    val canLoadMoreTracksForSelection: Boolean = true, // Nuevo: para saber si hay mÃƒÂ¡s canciones para cargar

    //Sort option
    val currentBooklistsortOption: SortOption = SortOption.BooklistNameAZ,
    val currentBooklistTracksSortOption: SortOption = SortOption.TrackTitleAZ,
    val BooklistTracksOrderMode: BooklistTracksOrderMode = BooklistTracksOrderMode.Sorted(SortOption.TrackTitleAZ),
    val BooklistOrderModes: Map<String, BooklistTracksOrderMode> = emptyMap()
)

sealed class BooklistTracksOrderMode {
    object Manual : BooklistTracksOrderMode()
    data class Sorted(val option: SortOption) : BooklistTracksOrderMode()
}

@HiltViewModel
class BooklistViewModel @Inject constructor(
    private val userPreferencesRepository: UserPreferencesRepository,
    private val AudiobookRepository: AudiobookRepository,
    private val m3uManager: M3uManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(BooklistUiState())
    val uiState: StateFlow<BooklistUiState> = _uiState.asStateFlow()

    private val _BooklistCreationEvent = MutableSharedFlow<Boolean>()
    val BooklistCreationEvent: SharedFlow<Boolean> = _BooklistCreationEvent.asSharedFlow()

    companion object {
        private const val Track_SELECTION_PAGE_SIZE =
            100 // Cargar 100 canciones a la vez para el selector
        const val FOLDER_Booklist_PREFIX = "folder_Booklist:"
        private const val MANUAL_ORDER_MODE = "manual"
    }

    // Helper function to resolve stored Booklist sort keys
    private fun resolveBooklistsortOption(optionKey: String?): SortOption {
        return SortOption.fromStorageKey(
            optionKey,
            SortOption.Booklists,
            SortOption.BooklistNameAZ
        )
    }

    init {
        loadBooklistsAndInitialSortOption()
        loadMoreTracksForSelection(isInitialLoad = true)
        observeBooklistOrderModes()
    }

    private fun observeBooklistOrderModes() {
        viewModelScope.launch {
            userPreferencesRepository.BooklistTrackOrderModesFlow.collect { storedModes ->
                val resolvedModes = storedModes.mapValues { (_, value) ->
                    decodeOrderMode(value)
                }
                _uiState.update { it.copy(BooklistOrderModes = resolvedModes) }
            }
        }
    }

    private fun loadBooklistsAndInitialSortOption() {
        viewModelScope.launch {
            // First, get the initial sort option
            val initialSortOptionName = userPreferencesRepository.BooklistsSortOptionFlow.first()
            val initialSortOption = resolveBooklistsortOption(initialSortOptionName)
            _uiState.update { it.copy(currentBooklistsortOption = initialSortOption) }

            // Then, collect Booklists and apply the sort option
            userPreferencesRepository.userBooklistsFlow.collect { Booklists ->
                val currentSortOption =
                    _uiState.value.currentBooklistsortOption // Use the most up-to-date sort option
                val sortedBooklists = when (currentSortOption) {
                    SortOption.BooklistNameAZ -> Booklists.sortedBy { it.name.lowercase() }
                    SortOption.BooklistNameZA -> Booklists.sortedByDescending { it.name.lowercase() }
                    SortOption.BooklistDateCreated -> Booklists.sortedByDescending { it.lastModified }
                    else -> Booklists.sortedBy { it.name.lowercase() } // Default to NameAZ
                }
                _uiState.update { it.copy(Booklists = sortedBooklists) }
            }
        }
        // Collect subsequent changes to sort option from preferences
        viewModelScope.launch {
            userPreferencesRepository.BooklistsSortOptionFlow.collect { optionName ->
                val newSortOption = resolveBooklistsortOption(optionName)
                if (_uiState.value.currentBooklistsortOption != newSortOption) {
                    // If the option from preferences is different, re-sort the current list
                    sortBooklists(newSortOption)
                }
            }
        }
    }

    // Nueva funciÃƒÂ³n para cargar canciones para el selector de forma paginada
    fun loadMoreTracksForSelection(isInitialLoad: Boolean = false) {
        val currentState = _uiState.value
        if (currentState.isLoadingTrackselection && !isInitialLoad) {
            Log.d("BooklistVM", "loadMoreTracksForSelection: Already loading. Skipping.")
            return
        }
        if (!currentState.canLoadMoreTracksForSelection && !isInitialLoad) {
            Log.d("BooklistVM", "loadMoreTracksForSelection: Cannot load more. Skipping.")
            return
        }

        viewModelScope.launch {
            val initialPageForLoad = if (isInitialLoad) 1 else _uiState.value.TrackselectionPage

            _uiState.update {
                it.copy(
                    isLoadingTrackselection = true,
                    TrackselectionPage = initialPageForLoad // Establecer la pÃƒÂ¡gina correcta antes de la llamada
                )
            }

            // Usar el TrackselectionPage del estado que acabamos de actualizar para la llamada al repo
            val pageToLoad = _uiState.value.TrackselectionPage // Esta ahora es la pÃƒÂ¡gina correcta

            Log.d(
                "BooklistVM",
                "Loading Tracks for selection. Page: $pageToLoad, PageSize: $Track_SELECTION_PAGE_SIZE"
            )

            try {
                // Colectar la lista de canciones del Flow en un hilo de IO
                val actualNewTracksList: List<Track> =
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        AudiobookRepository.getAudioFiles().first()
                    }
                Log.d("BooklistVM", "Loaded ${actualNewTracksList.size} Tracks for selection.")

                // La actualizaciÃƒÂ³n del UI se hace en el hilo principal (contexto por defecto de viewModelScope.launch)
                _uiState.update { currentStateAfterLoad ->
                    val updatedTrackselectionList = if (isInitialLoad) {
                        actualNewTracksList
                    } else {
                        // Evitar duplicados si por alguna razÃƒÂ³n se recarga la misma pÃƒÂ¡gina
                        val currentTrackIds =
                            currentStateAfterLoad.TrackselectionForBooklist.map { it.id }.toSet()
                        val uniqueNewTracks =
                            actualNewTracksList.filterNot { currentTrackIds.contains(it.id) }
                        currentStateAfterLoad.TrackselectionForBooklist + uniqueNewTracks
                    }

                    currentStateAfterLoad.copy(
                        TrackselectionForBooklist = updatedTrackselectionList,
                        isLoadingTrackselection = false,
                        canLoadMoreTracksForSelection = actualNewTracksList.size == Track_SELECTION_PAGE_SIZE,
                        // Incrementar la pÃƒÂ¡gina solo si se cargaron canciones y se espera que haya mÃƒÂ¡s
                        TrackselectionPage = if (actualNewTracksList.isNotEmpty() && actualNewTracksList.size == Track_SELECTION_PAGE_SIZE) {
                            currentStateAfterLoad.TrackselectionPage + 1
                        } else {
                            currentStateAfterLoad.TrackselectionPage // No incrementar si no hay mÃƒÂ¡s o si la carga fue parcial
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e("BooklistVM", "Error loading Tracks for selection. Page: $pageToLoad", e)
                _uiState.update {
                    it.copy(
                        isLoadingTrackselection = false
                    )
                }
            }
        }
    }


    fun loadBooklistDetails(BooklistId: String) {
        viewModelScope.launch {
            val shouldKeepExisting = _uiState.value.currentBooklistDetails?.id == BooklistId
            _uiState.update {
                it.copy(
                    isLoading = true,
                    BooklistNotFound = false,
                    currentBooklistDetails = if (shouldKeepExisting) it.currentBooklistDetails else null,
                    currentBooklistTracks = if (shouldKeepExisting) it.currentBooklistTracks else emptyList()
                )
            } // Resetear detalles y canciones
            try {
                if (isFolderBooklistId(BooklistId)) {
                    val folderPath = Uri.decode(BooklistId.removePrefix(FOLDER_Booklist_PREFIX))
                    val folders = AudiobookRepository.getAudiobookFolders().first()
                    val folder = findFolder(folderPath, folders)

                    if (folder != null) {
                        val TracksList = folder.collectAllTracks()
                        val pseudoBooklist = Booklist(
                            id = BooklistId,
                            name = folder.name,
                            TrackIds = TracksList.map { it.id }
                        )

                        _uiState.update {
                            it.copy(
                                currentBooklistDetails = pseudoBooklist,
                                currentBooklistTracks = applySortToTracks(TracksList, it.currentBooklistTracksSortOption),
                                BooklistTracksOrderMode = BooklistTracksOrderMode.Sorted(it.currentBooklistTracksSortOption),
                                isLoading = false,
                                BooklistNotFound = false
                            )
                        }
                    } else {
                        Log.w("BooklistVM", "Folder Booklist with path $folderPath not found.")
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                BooklistNotFound = true,
                                currentBooklistDetails = null,
                                currentBooklistTracks = emptyList()
                            )
                        }
                    }
                } else {
                    // Obtener la Booklist de las preferencias del usuario
                        val Booklist = userPreferencesRepository.userBooklistsFlow.first()
                            .find { it.id == BooklistId }

                    if (Booklist != null) {
                        val orderMode = _uiState.value.BooklistOrderModes[BooklistId]
                            ?: BooklistTracksOrderMode.Manual

                        // Colectar la lista de canciones del Flow devuelto por el repositorio en un hilo de IO
                        val TracksList: List<Track> = withContext(kotlinx.coroutines.Dispatchers.IO) {
                            AudiobookRepository.getTracksByIds(Booklist.TrackIds).first()
                        }

                        val orderedTracks = when (orderMode) {
                            is BooklistTracksOrderMode.Sorted -> applySortToTracks(TracksList, orderMode.option)
                            BooklistTracksOrderMode.Manual -> TracksList
                        }

                        // La actualizaciÃƒÂ³n del UI se hace en el hilo principal
                        _uiState.update {
                            it.copy(
                                currentBooklistDetails = Booklist,
                                currentBooklistTracks = orderedTracks,
                                currentBooklistTracksSortOption = (orderMode as? BooklistTracksOrderMode.Sorted)?.option
                                    ?: it.currentBooklistTracksSortOption,
                                BooklistTracksOrderMode = orderMode,
                                BooklistOrderModes = it.BooklistOrderModes + (BooklistId to orderMode),
                                isLoading = false,
                                BooklistNotFound = false
                            )
                        }
                    } else {
                        Log.w("BooklistVM", "Booklist with id $BooklistId not found.")
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                BooklistNotFound = true,
                                currentBooklistDetails = null,
                                currentBooklistTracks = emptyList()
                            )
                        } // Mantener isLoading en false
                        // Opcional: podrÃƒÂ­as establecer un error o un estado especÃƒÂ­fico de "no encontrado"
                    }
                }
            } catch (e: Exception) {
                Log.e("BooklistVM", "Error loading Booklist details for id $BooklistId", e)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        BooklistNotFound = true,
                        currentBooklistDetails = null,
                        currentBooklistTracks = emptyList()
                    )
                }
            }
        }
    }

    fun createBooklist(
        name: String,
        coverImageUri: String? = null,
        coverColor: Int? = null,
        coverIcon: String? = null,
        TrackIds: List<String> = emptyList(), // Added TrackIds parameter
        cropScale: Float = 1f,
        cropPanX: Float = 0f,
        cropPanY: Float = 0f,
        isAiGenerated: Boolean = false,
        isQueueGenerated: Boolean = false,
        coverShapeType: String? = null,
        coverShapeDetail1: Float? = null,
        coverShapeDetail2: Float? = null,
        coverShapeDetail3: Float? = null,
        coverShapeDetail4: Float? = null,
    ) {
        viewModelScope.launch {
            var savedCoverPath: String? = null
            
            if (coverImageUri != null) {
                // Generate a unique ID for the image file since we don't have the Booklist ID yet
                val imageId = UUID.randomUUID().toString()
                savedCoverPath = saveCoverImageToInternalStorage(
                    Uri.parse(coverImageUri), 
                    imageId,
                    cropScale,
                    cropPanX,
                    cropPanY
                )
            }

            userPreferencesRepository.createBooklist(
                name = name,
                TrackIds = TrackIds, // Use passed TrackIds
                isAiGenerated = isAiGenerated,
                isQueueGenerated = isQueueGenerated,
                coverImageUri = savedCoverPath,
                coverColorArgb = coverColor,
                coverIconName = coverIcon,
                coverShapeType = coverShapeType,
                coverShapeDetail1 = coverShapeDetail1,
                coverShapeDetail2 = coverShapeDetail2,
                coverShapeDetail3 = coverShapeDetail3,
                coverShapeDetail4 = coverShapeDetail4
            )
            _BooklistCreationEvent.emit(true)
        }
    }


    suspend fun saveCoverImageToInternalStorage(
        uri: Uri, 
        uniqueId: String,
        cropScale: Float,
        cropPanX: Float,
        cropPanY: Float
    ): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Load original bitmap
                val originalBitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ ->
                         // Optimization: Mutable to support software rendering if needed
                         decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE 
                         // Use HARWARE if possible but need to copy for Canvas? 
                         // Software is safer for manual Canvas drawing.
                    }
                } else {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                }
                
                // Target dimensions (Square)
                val targetSize = 1024
                
                // create target bitmap
                val targetBitmap = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(targetBitmap)
                
                // Calculate base dimensions (fitting smallest dimension to target)
                // Logic must match ImageCropView
                val bitmapWidth = originalBitmap.width.toFloat()
                val bitmapHeight = originalBitmap.height.toFloat()
                val bitmapRatio = bitmapWidth / bitmapHeight
                
                val (baseWidth, baseHeight) = if (bitmapRatio > 1f) {
                     // Wide: Height matches target
                     targetSize * bitmapRatio to targetSize.toFloat()
                } else {
                     // Tall: Width matches target
                     targetSize.toFloat() to targetSize / bitmapRatio
                }
                
                // Calculate transformations
                // Scaled Dimensions
                val scaledWidth = baseWidth * cropScale
                val scaledHeight = baseHeight * cropScale
                
                // Center + Pan
                // Center of target is targetSize/2
                // We want to center the Scaled Image at (Center + Pan)
                // TopLeft = CenterX - ScaledW/2 + PanX
                
                // Pan is normalized relative to Viewport (TargetSize)
                val panPxX = cropPanX * targetSize
                val panPxY = cropPanY * targetSize
                
                val dx = (targetSize - scaledWidth) / 2f + panPxX
                val dy = (targetSize - scaledHeight) / 2f + panPxY
                
                // Draw
                // We draw the original bitmap scaled to (scaledWidth, scaledHeight) at (dx, dy)
                val matrix = android.graphics.Matrix()
                matrix.postScale(scaledWidth / bitmapWidth, scaledHeight / bitmapHeight)
                matrix.postTranslate(dx, dy)
                
                canvas.drawBitmap(originalBitmap, matrix, null)
                
                // Save
                val fileName = "Booklist_cover_$uniqueId.jpg"
                val file = File(context.filesDir, fileName)
                FileOutputStream(file).use { out ->
                    targetBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                
                // Recycle
                if (originalBitmap != targetBitmap) originalBitmap.recycle()
                // Target bitmap is not recycled here, let GC handle? 
                // Or recycle explicitly if immediate memory pressure concern.
                
                file.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    fun deleteBooklist(BooklistId: String) {
        if (isFolderBooklistId(BooklistId)) return
        viewModelScope.launch {
            userPreferencesRepository.deleteBooklist(BooklistId)
        }
    }

    fun importM3u(uri: Uri) {
        viewModelScope.launch {
            try {
                val (name, TrackIds) = m3uManager.parseM3u(uri)
                if (TrackIds.isNotEmpty()) {
                    userPreferencesRepository.createBooklist(name, TrackIds)
                }
            } catch (e: Exception) {
                Log.e("BooklistViewModel", "Error importing M3U", e)
            }
        }
    }

    fun exportM3u(Booklist: Booklist, uri: Uri, context: android.content.Context) {
        viewModelScope.launch {
            try {
                val Tracks = AudiobookRepository.getTracksByIds(Booklist.TrackIds).first()
                val m3uContent = m3uManager.generateM3u(Booklist, Tracks)
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    OutputStreamWriter(outputStream).use { writer ->
                        writer.write(m3uContent)
                    }
                }
            } catch (e: Exception) {
                Log.e("BooklistViewModel", "Error exporting M3U", e)
            }
        }
    }

    fun renameBooklist(BooklistId: String, newName: String) {
        if (isFolderBooklistId(BooklistId)) return
        viewModelScope.launch {
            userPreferencesRepository.renameBooklist(BooklistId, newName)
            if (_uiState.value.currentBooklistDetails?.id == BooklistId) {
                _uiState.update {
                    it.copy(
                        currentBooklistDetails = it.currentBooklistDetails?.copy(
                            name = newName
                        )
                    )
                }
            }
        }
    }

    fun updateBooklistParameters(
        BooklistId: String,
        name: String,
        coverImageUri: String?,
        coverColor: Int?,
        coverIcon: String?,
        cropScale: Float,
        cropPanX: Float,
        cropPanY: Float,
        coverShapeType: String?,
        coverShapeDetail1: Float?,
        coverShapeDetail2: Float?,
        coverShapeDetail3: Float?,
        coverShapeDetail4: Float?
    ) {
        if (isFolderBooklistId(BooklistId)) return
        val currentBooklist = _uiState.value.currentBooklistDetails ?: return
        if (currentBooklist.id != BooklistId) return

        viewModelScope.launch {
            var savedCoverPath: String? = currentBooklist.coverImageUri

            // If a new URI is provided and it's different from the existing one (and not null)
            // Or if we need to re-save because crop params changed?
            // For simplicity, if coverImageUri is passed and it's a content URI, we save it.
            // If it's the same string as savedCoverPath, we assume it's unchanged unless we want to force re-crop.
            // The UI will pass the Uri string. If it's a local file path, it's likely already saved.
            // But if the user selected a new image, it will be a content content:// uri.

            if (coverImageUri != null && coverImageUri != currentBooklist.coverImageUri) {
                 // Check if it is a content URI or a file path that is NOT the existing saved path
                 if (coverImageUri.startsWith("content://") || (coverImageUri.startsWith("/") && coverImageUri != currentBooklist.coverImageUri)) {
                     val imageId = UUID.randomUUID().toString()
                     val newPath = saveCoverImageToInternalStorage(
                         Uri.parse(coverImageUri),
                         imageId,
                         cropScale,
                         cropPanX,
                         cropPanY
                     )
                     if (newPath != null) {
                         savedCoverPath = newPath
                     }
                 }
            } else if (coverImageUri == null) {
                // If passed null, it might mean remove cover? Or just no change?
                // For this implementation let's assume if the user cleared it, the UI passes null.
                // But we need to distinguish "no change" vs "remove".
                // In CreateBooklist we have "selectedImageUri".
                // Let's assume the UI sends the desired final state.
                // NOTE: If the user didn't change the image, the UI might send the existing coverImageUri (which is a file path).
                // Or if they removed it, they send null.
                
                // However, we also have crop parameters. If image is unchanged but crop changed, we should re-save (re-crop)
                // if we have the original source. But we don't have the original source for the existing cover (we only have the cropped result).
                // So, we can only re-crop if we have a source URI.
                // This limitation implies: We can only update crop if we pick an image.
                // So if coverImageUri is the existing path, we ignore crop params.
                savedCoverPath = null // If explicit null passed, we remove it.
            }
            // Logic correction: 
            // If the UI passes the EXISTING file path, implies NO CHANGE to image.
            // If the UI passes a NEW content URI, implies NEW IMAGE (and we use crop params).
            // If the UI passes NULL, implies REMOVE IMAGE.
            if (coverImageUri == currentBooklist.coverImageUri) {
                savedCoverPath = currentBooklist.coverImageUri
            }


            val updatedBooklist = currentBooklist.copy(
                name = name,
                coverImageUri = savedCoverPath,
                coverColorArgb = coverColor,
                coverIconName = coverIcon,
                coverShapeType = coverShapeType,
                coverShapeDetail1 = coverShapeDetail1,
                coverShapeDetail2 = coverShapeDetail2,
                coverShapeDetail3 = coverShapeDetail3,
                coverShapeDetail4 = coverShapeDetail4
            )

            // Optimistic update
            _uiState.update {
                it.copy(currentBooklistDetails = updatedBooklist)
            }

            userPreferencesRepository.updateBooklist(updatedBooklist)
        }
    }

    fun addTracksToBooklist(BooklistId: String, TrackIdsToAdd: List<String>) {
        if (isFolderBooklistId(BooklistId)) return
        viewModelScope.launch {
            userPreferencesRepository.addTracksToBooklist(BooklistId, TrackIdsToAdd)
            if (_uiState.value.currentBooklistDetails?.id == BooklistId) {
                loadBooklistDetails(BooklistId)
            }
        }
    }

    /**
     * @param BooklistIds Ids of Booklists to add the Track to
     * */
    fun addOrRemoveTrackFromBooklists(
        TrackId: String,
        BooklistIds: List<String>,
        currentBooklistId: String?
    ) {
        viewModelScope.launch {
            val removedFromBooklists =
                userPreferencesRepository.addOrRemoveTrackFromBooklists(TrackId, BooklistIds)
            if (currentBooklistId != null && removedFromBooklists.contains (currentBooklistId)) {
                removeTrackFromBooklist(currentBooklistId, TrackId)
            }
        }
    }

    fun removeTrackFromBooklist(BooklistId: String, TrackIdToRemove: String) {
        if (isFolderBooklistId(BooklistId)) return
        viewModelScope.launch {
            userPreferencesRepository.removeTrackFromBooklist(BooklistId, TrackIdToRemove)
            if (_uiState.value.currentBooklistDetails?.id == BooklistId) {
                _uiState.update {
                    it.copy(currentBooklistTracks = it.currentBooklistTracks.filterNot { s -> s.id == TrackIdToRemove })
                }
            }
        }
    }

    fun reorderTracksInBooklist(BooklistId: String, fromIndex: Int, toIndex: Int) {
        if (isFolderBooklistId(BooklistId)) return
        viewModelScope.launch {
            val currentTracks = _uiState.value.currentBooklistTracks.toMutableList()
            if (fromIndex in currentTracks.indices && toIndex in currentTracks.indices) {
                val item = currentTracks.removeAt(fromIndex)
                currentTracks.add(toIndex, item)
                val newTrackOrderIds = currentTracks.map { it.id }
                userPreferencesRepository.reorderTracksInBooklist(BooklistId, newTrackOrderIds)
                userPreferencesRepository.setBooklistTrackOrderMode(
                    BooklistId,
                    MANUAL_ORDER_MODE
                )
                _uiState.update {
                    val updatedModes = it.BooklistOrderModes + (BooklistId to BooklistTracksOrderMode.Manual)
                    it.copy(
                        currentBooklistTracks = currentTracks,
                        BooklistTracksOrderMode = BooklistTracksOrderMode.Manual,
                        BooklistOrderModes = updatedModes
                    )
                }
            }
        }
    }

    //Sort funs
    fun sortBooklists(sortOption: SortOption) {
        _uiState.update { it.copy(currentBooklistsortOption = sortOption) }

        val currentBooklists = _uiState.value.Booklists
        val sortedBooklists = when (sortOption) {
            SortOption.BooklistNameAZ -> currentBooklists.sortedBy { it.name.lowercase() }
            SortOption.BooklistNameZA -> currentBooklists.sortedByDescending { it.name.lowercase() }
            SortOption.BooklistDateCreated -> currentBooklists.sortedByDescending { it.lastModified }
            else -> currentBooklists
        }.toList()

        _uiState.update { it.copy(Booklists = sortedBooklists) }

        viewModelScope.launch {
            userPreferencesRepository.setBooklistsSortOption(sortOption.storageKey)
        }
    }

    fun sortBooklistTracks(sortOption: SortOption) {
        val BooklistId = _uiState.value.currentBooklistDetails?.id
        
        // If TrackDefaultOrder is selected, reload the Booklist to get original order
        if (sortOption == SortOption.TrackDefaultOrder) {
            if (BooklistId != null) {
                viewModelScope.launch {
                    // Set order mode to Manual (which preserves original order)
                    userPreferencesRepository.setBooklistTrackOrderMode(
                        BooklistId,
                        MANUAL_ORDER_MODE
                    )
                    // Reload the Booklist to get original Track order
                    loadBooklistDetails(BooklistId)
                }
            }
            return
        }

        val currentTracks = _uiState.value.currentBooklistTracks
        val sortedTracks = when (sortOption) {
            SortOption.TrackTitleAZ -> currentTracks.sortedBy { it.title.lowercase() }
            SortOption.TrackTitleZA -> currentTracks.sortedByDescending { it.title.lowercase() }
            SortOption.TrackAuthor -> currentTracks.sortedBy { it.Author.lowercase() }
            SortOption.TrackBook -> currentTracks.sortedBy { it.Book.lowercase() }
            SortOption.TrackDuration -> currentTracks.sortedBy { it.duration }
            SortOption.TrackDateAdded -> currentTracks.sortedByDescending { it.dateAdded }
            else -> currentTracks
        }

        _uiState.update {
            val updatedModes = if (BooklistId != null) {
                it.BooklistOrderModes + (BooklistId to BooklistTracksOrderMode.Sorted(sortOption))
            } else {
                it.BooklistOrderModes
            }
            it.copy(
                currentBooklistTracks = sortedTracks,
                currentBooklistTracksSortOption = sortOption,
                BooklistTracksOrderMode = BooklistTracksOrderMode.Sorted(sortOption),
                BooklistOrderModes = updatedModes
            )
        }

        if (BooklistId != null) {
            viewModelScope.launch {
                userPreferencesRepository.setBooklistTrackOrderMode(
                    BooklistId,
                    sortOption.storageKey
                )
            }
        }

        // Persist local sort preference if needed (optional, not requested but good UX)
        // For now, we keep it in memory as per request focus.
    }

    private fun isFolderBooklistId(BooklistId: String): Boolean =
        BooklistId.startsWith(FOLDER_Booklist_PREFIX)

    private fun findFolder(
        targetPath: String,
        folders: List<com.oakiha.audia.data.model.AudiobookFolder>
    ): com.oakiha.audia.data.model.AudiobookFolder? {
        val queue: ArrayDeque<com.oakiha.audia.data.model.AudiobookFolder> = ArrayDeque(folders)
        while (queue.isNotEmpty()) {
            val folder = queue.removeFirst()
            if (folder.path == targetPath) {
                return folder
            }
            folder.subFolders.forEach { queue.addLast(it) }
        }
        return null
    }

    private fun com.oakiha.audia.data.model.AudiobookFolder.collectAllTracks(): List<Track> {
        return Tracks + subFolders.flatMap { it.collectAllTracks() }
    }

    private fun applySortToTracks(Tracks: List<Track>, sortOption: SortOption): List<Track> {
        return when (sortOption) {
            SortOption.TrackTitleAZ -> Tracks.sortedBy { it.title.lowercase() }
            SortOption.TrackTitleZA -> Tracks.sortedByDescending { it.title.lowercase() }
            SortOption.TrackAuthor -> Tracks.sortedBy { it.Author.lowercase() }
            SortOption.TrackBook -> Tracks.sortedBy { it.Book.lowercase() }
            SortOption.TrackDuration -> Tracks.sortedBy { it.duration }
            SortOption.TrackDateAdded -> Tracks.sortedByDescending { it.dateAdded }
            else -> Tracks
        }
    }

    private fun decodeOrderMode(value: String): BooklistTracksOrderMode {
        return if (value == MANUAL_ORDER_MODE) {
            BooklistTracksOrderMode.Manual
        } else {
            val option = SortOption.fromStorageKey(value, SortOption.Tracks, SortOption.TrackTitleAZ)
            BooklistTracksOrderMode.Sorted(option)
        }
    }
}
