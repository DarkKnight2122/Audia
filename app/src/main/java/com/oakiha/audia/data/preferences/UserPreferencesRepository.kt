package com.oakiha.audia.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey // Added import
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.Player
import com.oakiha.audia.data.model.Booklist
import com.oakiha.audia.data.model.SortOption // Added import
import com.oakiha.audia.data.model.TranscriptSourcePreference
import com.oakiha.audia.data.model.TransitionSettings
import com.oakiha.audia.data.equalizer.EqualizerPreset // Added import
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.text.get
import kotlin.text.set
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object ThemePreference {
    const val DEFAULT = "default"
    const val DYNAMIC = "dynamic"
    const val Book_ART = "Book_art"
    const val GLOBAL = "global"
}

object AppThemeMode {
    const val FOLLOW_SYSTEM = "follow_system"
    const val LIGHT = "light"
    const val DARK = "dark"
}

/**
 * Book art quality settings for developer options.
 * Controls maximum resolution for Book artwork in player view.
 * Thumbnails in lists always use low resolution for performance.
 * 
 * @property maxSize Maximum size in pixels (0 = original size)
 * @property label Human-readable label for UI
 */
enum class BookArtQuality(val maxSize: Int, val label: String) {
    LOW(256, "Low (256px) - Better performance"),
    MEDIUM(512, "Medium (512px) - Balanced"),
    HIGH(800, "High (800px) - Best quality"),
    ORIGINAL(0, "Original - Maximum quality")
}

@Singleton
class UserPreferencesRepository
@Inject
constructor(
        private val dataStore: DataStore<Preferences>,
        private val json: Json // Inyectar Json para serializaciÃƒÂ³n
) {

    private object PreferencesKeys {
        val APP_REBRAND_DIALOG_SHOWN = booleanPreferencesKey("app_rebrand_dialog_shown")
        val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
        val GEMINI_MODEL = stringPreferencesKey("gemini_model")
        val GEMINI_SYSTEM_PROMPT = stringPreferencesKey("gemini_system_prompt")
        val ALLOWED_DIRECTORIES = stringSetPreferencesKey("allowed_directories")
        val BLOCKED_DIRECTORIES = stringSetPreferencesKey("blocked_directories")
        val INITIAL_SETUP_DONE = booleanPreferencesKey("initial_setup_done")
        // val GLOBAL_THEME_PREFERENCE = stringPreferencesKey("global_theme_preference_v2") //
        // Removed
        val PLAYER_THEME_PREFERENCE = stringPreferencesKey("player_theme_preference_v2")
        val APP_THEME_MODE = stringPreferencesKey("app_theme_mode")
        val FAVORITE_Track_IDS = stringSetPreferencesKey("favorite_Track_ids")
        val USER_Booklists = stringPreferencesKey("user_Booklists_json_v1")
        val Booklist_Track_ORDER_MODES = stringPreferencesKey("Booklist_Track_order_modes")

        // Sort Option Keys
        val Tracks_SORT_OPTION = stringPreferencesKey("Tracks_sort_option")
        val Tracks_SORT_OPTION_MIGRATED = booleanPreferencesKey("Tracks_sort_option_migrated_v2")
        val Books_SORT_OPTION = stringPreferencesKey("Books_sort_option")
        val Authors_SORT_OPTION = stringPreferencesKey("Authors_sort_option")
        val Booklists_SORT_OPTION = stringPreferencesKey("Booklists_sort_option")
        val LIKED_Tracks_SORT_OPTION = stringPreferencesKey("liked_Tracks_sort_option")

        // UI State Keys
        val LAST_LIBRARY_TAB_INDEX =
                intPreferencesKey("last_library_tab_index") // Corrected: Add intPreferencesKey here
        val MOCK_Categories_ENABLED = booleanPreferencesKey("mock_Categories_enabled")
        val LAST_DAILY_MIX_UPDATE = longPreferencesKey("last_daily_mix_update")
        val DAILY_MIX_Track_IDS = stringPreferencesKey("daily_mix_Track_ids")
        val YOUR_MIX_Track_IDS = stringPreferencesKey("your_mix_Track_ids")
        val NAV_BAR_CORNER_RADIUS = intPreferencesKey("nav_bar_corner_radius")
        val NAV_BAR_STYLE = stringPreferencesKey("nav_bar_style")
        val CAROUSEL_STYLE = stringPreferencesKey("carousel_style")
        val LIBRARY_NAVIGATION_MODE = stringPreferencesKey("library_navigation_mode")
        val LAUNCH_TAB = stringPreferencesKey("launch_tab")

        // Transition Settings
        val GLOBAL_TRANSITION_SETTINGS = stringPreferencesKey("global_transition_settings_json")
        val LIBRARY_TABS_ORDER = stringPreferencesKey("library_tabs_order")
        val IS_FOLDER_FILTER_ACTIVE = booleanPreferencesKey("is_folder_filter_active")
        val IS_FOLDERS_Booklist_VIEW = booleanPreferencesKey("is_folders_Booklist_view")
        val USE_SMOOTH_CORNERS = booleanPreferencesKey("use_smooth_corners")
        val KEEP_PLAYING_IN_BACKGROUND = booleanPreferencesKey("keep_playing_in_background")
        val IS_CROSSFADE_ENABLED = booleanPreferencesKey("is_crossfade_enabled")
        val CROSSFADE_DURATION = intPreferencesKey("crossfade_duration")
        val REPEAT_MODE = intPreferencesKey("repeat_mode")
        val IS_SHUFFLE_ON = booleanPreferencesKey("is_shuffle_on")
        val PERSISTENT_SHUFFLE_ENABLED = booleanPreferencesKey("persistent_shuffle_enabled")
        val DISABLE_CAST_AUTOPLAY = booleanPreferencesKey("disable_cast_autoplay")
        val SHOW_QUEUE_HISTORY = booleanPreferencesKey("show_queue_history")
        val FULL_PLAYER_DELAY_ALL = booleanPreferencesKey("full_player_delay_all")
        val FULL_PLAYER_DELAY_Book = booleanPreferencesKey("full_player_delay_Book")
        val FULL_PLAYER_DELAY_METADATA = booleanPreferencesKey("full_player_delay_metadata")
        val FULL_PLAYER_DELAY_PROGRESS = booleanPreferencesKey("full_player_delay_progress")
        val FULL_PLAYER_DELAY_CONTROLS = booleanPreferencesKey("full_player_delay_controls")
        val FULL_PLAYER_PLACEHOLDERS = booleanPreferencesKey("full_player_placeholders")
        val FULL_PLAYER_PLACEHOLDER_TRANSPARENT = booleanPreferencesKey("full_player_placeholder_transparent")
        val FULL_PLAYER_DELAY_THRESHOLD = intPreferencesKey("full_player_delay_threshold_percent")

        // Multi-Author Settings
        val Author_DELIMITERS = stringPreferencesKey("Author_delimiters")
        val GROUP_BY_Book_Author = booleanPreferencesKey("group_by_Book_Author")
        val Author_SETTINGS_RESCAN_REQUIRED =
                booleanPreferencesKey("Author_settings_rescan_required")

        // Equalizer Settings
        val EQUALIZER_ENABLED = booleanPreferencesKey("equalizer_enabled")
        val EQUALIZER_PRESET = stringPreferencesKey("equalizer_preset")
        val EQUALIZER_CUSTOM_BANDS = stringPreferencesKey("equalizer_custom_bands")
        val BASS_BOOST_STRENGTH = intPreferencesKey("bass_boost_strength")
        val VIRTUALIZER_STRENGTH = intPreferencesKey("virtualizer_strength")
        val BASS_BOOST_ENABLED = booleanPreferencesKey("bass_boost_enabled")
        val VIRTUALIZER_ENABLED = booleanPreferencesKey("virtualizer_enabled")
        val LOUDNESS_ENHANCER_ENABLED = booleanPreferencesKey("loudness_enhancer_enabled")
        val LOUDNESS_ENHANCER_STRENGTH = intPreferencesKey("loudness_enhancer_strength")
        
        // Dismissed Warning States
        val BASS_BOOST_DISMISSED = booleanPreferencesKey("bass_boost_dismissed")
        val VIRTUALIZER_DISMISSED = booleanPreferencesKey("virtualizer_dismissed")
        val LOUDNESS_DISMISSED = booleanPreferencesKey("loudness_dismissed")
        
        // View Mode
        // val IS_GRAPH_VIEW = booleanPreferencesKey("is_graph_view") // Deprecated
        val VIEW_MODE = stringPreferencesKey("equalizer_view_mode")

        // Custom Presets
        val CUSTOM_PRESETS = stringPreferencesKey("custom_presets_json") // List<EqualizerPreset>
        val PINNED_PRESETS = stringPreferencesKey("pinned_presets_json") // List<String> (names)
        
        // Library Sync
        val LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")
        
        // Transcript Sync Offset per Track (Map<TrackId, offsetMs> as JSON)
        val Transcript_SYNC_OFFSETS = stringPreferencesKey("Transcript_sync_offsets_json")
        
        // Transcript Source Preference
        val Transcript_SOURCE_PREFERENCE = stringPreferencesKey("Transcript_source_preference")
        val AUTO_SCAN_LRC_FILES = booleanPreferencesKey("auto_scan_lrc_files")
        
        // Developer Options
        val Book_ART_QUALITY = stringPreferencesKey("Book_art_quality")
        val TAP_BACKGROUND_CLOSES_PLAYER = booleanPreferencesKey("tap_background_closes_player")
        val IMMERSIVE_Transcript_ENABLED = booleanPreferencesKey("immersive_Transcript_enabled")
        val IMMERSIVE_Transcript_TIMEOUT = longPreferencesKey("immersive_Transcript_timeout")
    }

    val appRebrandDialogShownFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.APP_REBRAND_DIALOG_SHOWN] ?: false
            }

    suspend fun setAppRebrandDialogShown(wasShown: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.APP_REBRAND_DIALOG_SHOWN] = wasShown
        }
    }

    val isCrossfadeEnabledFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.IS_CROSSFADE_ENABLED] ?: true
            }

    suspend fun setCrossfadeEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_CROSSFADE_ENABLED] = enabled
        }
    }

    // Effects Settings
    val bassBoostEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.BASS_BOOST_ENABLED] ?: false
        }

    suspend fun setBassBoostEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.BASS_BOOST_ENABLED] = enabled }
    }

    val virtualizerEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.VIRTUALIZER_ENABLED] ?: false
        }

    suspend fun setVirtualizerEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.VIRTUALIZER_ENABLED] = enabled }
    }

    val loudnessEnhancerEnabledFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.LOUDNESS_ENHANCER_ENABLED] ?: false
        }

    val loudnessEnhancerStrengthFlow: Flow<Int> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.LOUDNESS_ENHANCER_STRENGTH] ?: 0
        }

    suspend fun setLoudnessEnhancerEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.LOUDNESS_ENHANCER_ENABLED] = enabled }
    }

    suspend fun setLoudnessEnhancerStrength(strength: Int) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.LOUDNESS_ENHANCER_STRENGTH] = strength }
    }

    // Dismissed Warning Flows & Setters
    val bassBoostDismissedFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.BASS_BOOST_DISMISSED] ?: false
    }

    suspend fun setBassBoostDismissed(dismissed: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.BASS_BOOST_DISMISSED] = dismissed }
    }

    val virtualizerDismissedFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.VIRTUALIZER_DISMISSED] ?: false
    }

    suspend fun setVirtualizerDismissed(dismissed: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.VIRTUALIZER_DISMISSED] = dismissed }
    }

    val loudnessDismissedFlow: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[PreferencesKeys.LOUDNESS_DISMISSED] ?: false
    }

    suspend fun setLoudnessDismissed(dismissed: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.LOUDNESS_DISMISSED] = dismissed }
    }

    enum class EqualizerViewMode {
        SLIDERS, GRAPH, HYBRID
    }

    val equalizerViewModeFlow: Flow<EqualizerViewMode> = dataStore.data.map { preferences ->
        val modeString = preferences[PreferencesKeys.VIEW_MODE]
        if (modeString != null) {
            try {
                EqualizerViewMode.valueOf(modeString)
            } catch (e: Exception) {
                EqualizerViewMode.SLIDERS
            }
        } else {
            // Migration: Check legacy boolean
            val isGraph = preferences[booleanPreferencesKey("is_graph_view")] ?: false
            if (isGraph) EqualizerViewMode.GRAPH else EqualizerViewMode.SLIDERS
        }
    }

    suspend fun setEqualizerViewMode(mode: EqualizerViewMode) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.VIEW_MODE] = mode.name
        }
    }

    val crossfadeDurationFlow: Flow<Int> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.CROSSFADE_DURATION] ?: 6000
            }

    suspend fun setCrossfadeDuration(duration: Int) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.CROSSFADE_DURATION] = duration }
    }

    val repeatModeFlow: Flow<Int> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.REPEAT_MODE] ?: Player.REPEAT_MODE_OFF
            }

    suspend fun setRepeatMode(@Player.RepeatMode mode: Int) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.REPEAT_MODE] = mode }
    }

    val isShuffleOnFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.IS_SHUFFLE_ON] ?: false
            }

    suspend fun setShuffleOn(on: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.IS_SHUFFLE_ON] = on }
    }

    val persistentShuffleEnabledFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.PERSISTENT_SHUFFLE_ENABLED] ?: false
            }

    suspend fun setPersistentShuffleEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.PERSISTENT_SHUFFLE_ENABLED] = enabled }
    }

    // ===== Multi-Author Settings =====

    val AuthorDelimitersFlow: Flow<List<String>> =
            dataStore.data.map { preferences ->
                val stored = preferences[PreferencesKeys.Author_DELIMITERS]
                if (stored != null) {
                    try {
                        json.decodeFromString<List<String>>(stored)
                    } catch (e: Exception) {
                        DEFAULT_Author_DELIMITERS
                    }
                } else {
                    DEFAULT_Author_DELIMITERS
                }
            }

    suspend fun setAuthorDelimiters(delimiters: List<String>) {
        // Ensure at least one delimiter is always maintained
        if (delimiters.isEmpty()) {
            return
        }

        dataStore.edit { preferences ->
            preferences[PreferencesKeys.Author_DELIMITERS] = json.encodeToString(delimiters)
            // Mark rescan as required when delimiters change
            preferences[PreferencesKeys.Author_SETTINGS_RESCAN_REQUIRED] = true
        }
    }

    suspend fun resetAuthorDelimitersToDefault() {
        setAuthorDelimiters(DEFAULT_Author_DELIMITERS)
    }

    val groupByBookAuthorFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.GROUP_BY_Book_Author] ?: false
            }

    suspend fun setGroupByBookAuthor(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.GROUP_BY_Book_Author] = enabled
            // Mark rescan as required when this setting changes
            preferences[PreferencesKeys.Author_SETTINGS_RESCAN_REQUIRED] = true
        }
    }

    val AuthorsettingsRescanRequiredFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.Author_SETTINGS_RESCAN_REQUIRED] ?: false
            }

    suspend fun clearAuthorsettingsRescanRequired() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.Author_SETTINGS_RESCAN_REQUIRED] = false
        }
    }

    // ===== Library Sync Settings =====
    
    val lastSyncTimestampFlow: Flow<Long> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP] ?: 0L
            }

    suspend fun getLastSyncTimestamp(): Long {
        return lastSyncTimestampFlow.first()
    }

    suspend fun setLastSyncTimestamp(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_SYNC_TIMESTAMP] = timestamp
        }
    }

    // ===== End Library Sync Settings =====

    // ===== Transcript Sync Offset Settings =====
    
    /**
     * Transcript sync offset per Track in milliseconds.
     * Stored as a JSON map: { "TrackId": offsetMs, ... }
     * Positive values = Transcript appear later (use when Transcript are ahead of audio)
     * Negative values = Transcript appear earlier (use when Transcript are behind audio)
     */
    private val TranscriptSyncOffsetsFlow: Flow<Map<String, Int>> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.Transcript_SYNC_OFFSETS]?.let { jsonString ->
                    try {
                        json.decodeFromString<Map<String, Int>>(jsonString)
                    } catch (e: Exception) {
                        emptyMap()
                    }
                } ?: emptyMap()
            }

    fun getTranscriptSyncOffsetFlow(TrackId: String): Flow<Int> {
        return TranscriptSyncOffsetsFlow.map { offsets -> offsets[TrackId] ?: 0 }
    }

    suspend fun getTranscriptSyncOffset(TrackId: String): Int {
        return getTranscriptSyncOffsetFlow(TrackId).first()
    }

    suspend fun setTranscriptSyncOffset(TrackId: String, offsetMs: Int) {
        dataStore.edit { preferences ->
            val currentOffsets = preferences[PreferencesKeys.Transcript_SYNC_OFFSETS]?.let { jsonString ->
                try {
                    json.decodeFromString<Map<String, Int>>(jsonString).toMutableMap()
                } catch (e: Exception) {
                    mutableMapOf()
                }
            } ?: mutableMapOf()
            
            if (offsetMs == 0) {
                currentOffsets.remove(TrackId) // Don't store default value
            } else {
                currentOffsets[TrackId] = offsetMs
            }
            
            preferences[PreferencesKeys.Transcript_SYNC_OFFSETS] = json.encodeToString(currentOffsets)
        }
    }

    // ===== End Transcript Sync Offset Settings =====

    // ===== Transcript Source Preference Settings =====
    
    val TranscriptSourcePreferenceFlow: Flow<TranscriptSourcePreference> =
            dataStore.data.map { preferences ->
                TranscriptSourcePreference.fromName(preferences[PreferencesKeys.Transcript_SOURCE_PREFERENCE])
            }

    suspend fun setTranscriptSourcePreference(preference: TranscriptSourcePreference) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.Transcript_SOURCE_PREFERENCE] = preference.name
        }
    }

    val autoScanLrcFilesFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.AUTO_SCAN_LRC_FILES] ?: false
            }

    suspend fun setAutoScanLrcFiles(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.AUTO_SCAN_LRC_FILES] = enabled
        }
    }

    val immersiveTranscriptEnabledFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.IMMERSIVE_Transcript_ENABLED] ?: false
            }

    val immersiveTranscriptTimeoutFlow: Flow<Long> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.IMMERSIVE_Transcript_TIMEOUT] ?: 4000L
            }

    suspend fun setImmersiveTranscriptEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IMMERSIVE_Transcript_ENABLED] = enabled
        }
    }

    suspend fun setImmersiveTranscriptTimeout(timeout: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IMMERSIVE_Transcript_TIMEOUT] = timeout
        }
    }

    // ===== End Transcript Source Preference Settings =====

    // ===== End Multi-Author Settings =====

    val globalTransitionSettingsFlow: Flow<TransitionSettings> =
            dataStore.data.map { preferences ->
                val duration = preferences[PreferencesKeys.CROSSFADE_DURATION] ?: 6000
                val settings =
                        preferences[PreferencesKeys.GLOBAL_TRANSITION_SETTINGS]?.let { jsonString ->
                            try {
                                json.decodeFromString<TransitionSettings>(jsonString)
                            } catch (e: Exception) {
                                TransitionSettings() // Return default on error
                            }
                        }
                                ?: TransitionSettings() // Return default if not set

                settings.copy(durationMs = duration)
            }

    suspend fun saveGlobalTransitionSettings(settings: TransitionSettings) {
        dataStore.edit { preferences ->
            val jsonString = json.encodeToString(settings)
            preferences[PreferencesKeys.GLOBAL_TRANSITION_SETTINGS] = jsonString
        }
    }

    val dailyMixTrackIdsFlow: Flow<List<String>> =
            dataStore.data.map { preferences ->
                val jsonString = preferences[PreferencesKeys.DAILY_MIX_Track_IDS]
                if (jsonString != null) {
                    try {
                        json.decodeFromString<List<String>>(jsonString)
                    } catch (e: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            }

    suspend fun saveDailyMixTrackIds(TrackIds: List<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DAILY_MIX_Track_IDS] = json.encodeToString(TrackIds)
        }
    }

    val yourMixTrackIdsFlow: Flow<List<String>> =
            dataStore.data.map { preferences ->
                val jsonString = preferences[PreferencesKeys.YOUR_MIX_Track_IDS]
                if (jsonString != null) {
                    try {
                        json.decodeFromString<List<String>>(jsonString)
                    } catch (e: Exception) {
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            }

    suspend fun saveYourMixTrackIds(TrackIds: List<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.YOUR_MIX_Track_IDS] = json.encodeToString(TrackIds)
        }
    }

    val lastDailyMixUpdateFlow: Flow<Long> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.LAST_DAILY_MIX_UPDATE] ?: 0L
            }

    suspend fun saveLastDailyMixUpdateTimestamp(timestamp: Long) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_DAILY_MIX_UPDATE] = timestamp
        }
    }

    val allowedDirectoriesFlow: Flow<Set<String>> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.ALLOWED_DIRECTORIES] ?: emptySet()
            }

    val blockedDirectoriesFlow: Flow<Set<String>> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.BLOCKED_DIRECTORIES] ?: emptySet()
            }

    val initialSetupDoneFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.INITIAL_SETUP_DONE] ?: false
            }

    val playerThemePreferenceFlow: Flow<String> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.PLAYER_THEME_PREFERENCE]
                        ?: ThemePreference.Book_ART // Default to Book Art
            }

    val appThemeModeFlow: Flow<String> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.APP_THEME_MODE] ?: AppThemeMode.FOLLOW_SYSTEM
            }

    val keepPlayingInBackgroundFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.KEEP_PLAYING_IN_BACKGROUND] ?: true
            }

    val disableCastAutoplayFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.DISABLE_CAST_AUTOPLAY] ?: false
            }

    val showQueueHistoryFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.SHOW_QUEUE_HISTORY] ?: false  // Default to false for performance
            }

    suspend fun setShowQueueHistory(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.SHOW_QUEUE_HISTORY] = show
        }
    }

    val fullPlayerLoadingTweaksFlow: Flow<FullPlayerLoadingTweaks> = dataStore.data
        .map { preferences ->
            val delayBook = preferences[PreferencesKeys.FULL_PLAYER_DELAY_Book] ?: true
            val delayMetadata = preferences[PreferencesKeys.FULL_PLAYER_DELAY_METADATA] ?: true
            val delayProgress = preferences[PreferencesKeys.FULL_PLAYER_DELAY_PROGRESS] ?: true
            val delayControls = preferences[PreferencesKeys.FULL_PLAYER_DELAY_CONTROLS] ?: true
            
            val delayAll = delayBook && delayMetadata && delayProgress && delayControls

            FullPlayerLoadingTweaks(
                delayAll = delayAll,
                delayBookCarousel = delayBook,
                delayTrackMetadata = delayMetadata,
                delayProgressBar = delayProgress,
                delayControls = delayControls,
                showPlaceholders = preferences[PreferencesKeys.FULL_PLAYER_PLACEHOLDERS] ?: false,
                transparentPlaceholders = preferences[PreferencesKeys.FULL_PLAYER_PLACEHOLDER_TRANSPARENT] ?: false,
                contentAppearThresholdPercent = preferences[PreferencesKeys.FULL_PLAYER_DELAY_THRESHOLD] ?: 100
            )
        }

    val favoriteTrackIdsFlow: Flow<Set<String>> =
            dataStore.data // Nuevo flujo para favoritos
                    .map { preferences ->
                preferences[PreferencesKeys.FAVORITE_Track_IDS] ?: emptySet()
            }

    val BooklistTrackOrderModesFlow: Flow<Map<String, String>> =
            dataStore.data.map { preferences ->
                val serializedModes = preferences[PreferencesKeys.Booklist_Track_ORDER_MODES]
                if (serializedModes.isNullOrBlank()) {
                    emptyMap()
                } else {
                    runCatching { json.decodeFromString<Map<String, String>>(serializedModes) }
                            .getOrDefault(emptyMap())
                }
            }

    val userBooklistsFlow: Flow<List<Booklist>> =
            dataStore.data.map { preferences ->
                val jsonString = preferences[PreferencesKeys.USER_Booklists]
                if (jsonString != null) {
                    try {
                        json.decodeFromString<List<Booklist>>(jsonString)
                    } catch (e: Exception) {
                        // Error al deserializar, devolver lista vacÃƒÂ­a o manejar error
                        emptyList()
                    }
                } else {
                    emptyList()
                }
            }

    private suspend fun saveBooklists(Booklists: List<Booklist>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USER_Booklists] = json.encodeToString(Booklists)
        }
    }

    suspend fun createBooklist(
            name: String,
            TrackIds: List<String> = emptyList(),
            isAiGenerated: Boolean = false,
            isQueueGenerated: Boolean = false,
            coverImageUri: String? = null,
            coverColorArgb: Int? = null,
            coverIconName: String? = null,
            coverShapeType: String? = null,
            coverShapeDetail1: Float? = null,
            coverShapeDetail2: Float? = null,
            coverShapeDetail3: Float? = null,
            coverShapeDetail4: Float? = null
    ): Booklist {
        val currentBooklists = userBooklistsFlow.first().toMutableList()
        val newBooklist =
                Booklist(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        TrackIds = TrackIds,
                        isAiGenerated = isAiGenerated,
                        isQueueGenerated = isQueueGenerated,
                        coverImageUri = coverImageUri,
                        coverColorArgb = coverColorArgb,
                        coverIconName = coverIconName,
                        coverShapeType = coverShapeType,
                        coverShapeDetail1 = coverShapeDetail1,
                        coverShapeDetail2 = coverShapeDetail2,
                        coverShapeDetail3 = coverShapeDetail3,
                        coverShapeDetail4 = coverShapeDetail4
                )
        currentBooklists.add(newBooklist)
        saveBooklists(currentBooklists)
        return newBooklist
    }

    suspend fun deleteBooklist(BooklistId: String) {
        val currentBooklists = userBooklistsFlow.first().toMutableList()
        currentBooklists.removeAll { it.id == BooklistId }
        saveBooklists(currentBooklists)
        clearBooklistTrackOrderMode(BooklistId)
    }

    suspend fun renameBooklist(BooklistId: String, newName: String) {
        val currentBooklists = userBooklistsFlow.first().toMutableList()
        val index = currentBooklists.indexOfFirst { it.id == BooklistId }
        if (index != -1) {
            currentBooklists[index] =
                    currentBooklists[index].copy(
                            name = newName,
                            lastModified = System.currentTimeMillis()
                    )
            saveBooklists(currentBooklists)
        }
    }

    suspend fun updateBooklist(Booklist: Booklist) {
        val currentBooklists = userBooklistsFlow.first().toMutableList()
        val index = currentBooklists.indexOfFirst { it.id == Booklist.id }
        if (index != -1) {
            currentBooklists[index] = Booklist.copy(lastModified = System.currentTimeMillis())
            saveBooklists(currentBooklists)
        }
    }

    suspend fun addTracksToBooklist(BooklistId: String, TrackIdsToAdd: List<String>) {
        val currentBooklists = userBooklistsFlow.first().toMutableList()
        val index = currentBooklists.indexOfFirst { it.id == BooklistId }
        if (index != -1) {
            val Booklist = currentBooklists[index]
            // Evitar duplicados, aÃƒÂ±adir solo los nuevos
            val newTrackIds = (Booklist.TrackIds + TrackIdsToAdd).distinct()
            currentBooklists[index] =
                    Booklist.copy(TrackIds = newTrackIds, lastModified = System.currentTimeMillis())
            saveBooklists(currentBooklists)
        }
    }

    /*
     * @param BooklistIds BooklistIds Ids of Booklists to add the Track to
     * will remove Track from the Booklists which are not in BooklistIds
     * */
    suspend fun addOrRemoveTrackFromBooklists(
            TrackId: String,
            BooklistIds: List<String>
    ): MutableList<String> {
        val currentBooklists = userBooklistsFlow.first().toMutableList()
        val removedBooklistIds = mutableListOf<String>()

        // adding to Booklist if not already in
        BooklistIds.forEach { BooklistId ->
            val index = currentBooklists.indexOfFirst { it.id == BooklistId }
            if (index != -1) {
                val Booklist = currentBooklists[index]
                if (Booklist.TrackIds.contains(TrackId)) return@forEach
                else {
                    val newTrackIds = (Booklist.TrackIds + TrackId).distinct()
                    currentBooklists[index] =
                            Booklist.copy(
                                    TrackIds = newTrackIds,
                                    lastModified = System.currentTimeMillis()
                            )
                    saveBooklists(currentBooklists)
                }
            }
        }

        // removing from Booklist if not in BooklistIds
        currentBooklists.forEach { Booklist ->
            if (Booklist.TrackIds.contains(TrackId) && !BooklistIds.contains(Booklist.id)) {
                removeTrackFromBooklist(Booklist.id, TrackId)
                removedBooklistIds.add(Booklist.id)
            }
        }
        return removedBooklistIds
    }

    suspend fun removeTrackFromBooklist(BooklistId: String, TrackIdToRemove: String) {
        val currentBooklists = userBooklistsFlow.first().toMutableList()
        val index = currentBooklists.indexOfFirst { it.id == BooklistId }
        if (index != -1) {
            val Booklist = currentBooklists[index]
            currentBooklists[index] =
                    Booklist.copy(
                            TrackIds = Booklist.TrackIds.filterNot { it == TrackIdToRemove },
                            lastModified = System.currentTimeMillis()
                    )
            saveBooklists(currentBooklists)
        }
    }

    suspend fun removeTrackFromAllBooklists(TrackId: String) {
        val currentBooklists = userBooklistsFlow.first().toMutableList()
        var updated = false

        // Iterate through all Booklists and remove the Track
        currentBooklists.forEachIndexed { index, Booklist ->
            if (Booklist.TrackIds.contains(TrackId)) {
                currentBooklists[index] =
                        Booklist.copy(
                                TrackIds = Booklist.TrackIds.filterNot { it == TrackId },
                                lastModified = System.currentTimeMillis()
                        )
                updated = true
            }
        }

        if (updated) {
            saveBooklists(currentBooklists)
        }
    }

    suspend fun reorderTracksInBooklist(BooklistId: String, newTrackOrderIds: List<String>) {
        val currentBooklists = userBooklistsFlow.first().toMutableList()
        val index = currentBooklists.indexOfFirst { it.id == BooklistId }
        if (index != -1) {
            currentBooklists[index] =
                    currentBooklists[index].copy(
                            TrackIds = newTrackOrderIds,
                            lastModified = System.currentTimeMillis()
                    )
            saveBooklists(currentBooklists)
        }
    }

    suspend fun setBooklistTrackOrderMode(BooklistId: String, modeValue: String) {
        dataStore.edit { preferences ->
            val existingModes =
                    preferences[PreferencesKeys.Booklist_Track_ORDER_MODES]?.let { raw ->
                        runCatching { json.decodeFromString<Map<String, String>>(raw) }
                                .getOrDefault(emptyMap())
                    }
                            ?: emptyMap()

            val updated = existingModes.toMutableMap()
            updated[BooklistId] = modeValue

            preferences[PreferencesKeys.Booklist_Track_ORDER_MODES] = json.encodeToString(updated)
        }
    }

    suspend fun clearBooklistTrackOrderMode(BooklistId: String) {
        dataStore.edit { preferences ->
            val existingModes =
                    preferences[PreferencesKeys.Booklist_Track_ORDER_MODES]?.let { raw ->
                        runCatching { json.decodeFromString<Map<String, String>>(raw) }
                                .getOrDefault(emptyMap())
                    }
                            ?: emptyMap()

            if (!existingModes.containsKey(BooklistId)) return@edit

            val updated = existingModes.toMutableMap()
            updated.remove(BooklistId)

            preferences[PreferencesKeys.Booklist_Track_ORDER_MODES] = json.encodeToString(updated)
        }
    }

    suspend fun updateAllowedDirectories(allowedPaths: Set<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ALLOWED_DIRECTORIES] = allowedPaths
        }
    }

    suspend fun updateDirectorySelections(allowedPaths: Set<String>, blockedPaths: Set<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.ALLOWED_DIRECTORIES] = allowedPaths
            preferences[PreferencesKeys.BLOCKED_DIRECTORIES] = blockedPaths
        }
    }

    suspend fun setPlayerThemePreference(themeMode: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PLAYER_THEME_PREFERENCE] = themeMode
        }
    }

    suspend fun setAppThemeMode(themeMode: String) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.APP_THEME_MODE] = themeMode }
    }

    suspend fun toggleFavoriteTrack(
            TrackId: String,
            removing: Boolean = false
    ) { // Nueva funciÃƒÂ³n para favoritos
        dataStore.edit { preferences ->
            val currentFavorites = preferences[PreferencesKeys.FAVORITE_Track_IDS] ?: emptySet()
            val contains = currentFavorites.contains(TrackId)

            if (contains) preferences[PreferencesKeys.FAVORITE_Track_IDS] = currentFavorites - TrackId
            else {
                if (removing)
                        preferences[PreferencesKeys.FAVORITE_Track_IDS] = currentFavorites - TrackId
                else preferences[PreferencesKeys.FAVORITE_Track_IDS] = currentFavorites + TrackId
            }
        }
    }

    suspend fun setInitialSetupDone(isDone: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.INITIAL_SETUP_DONE] = isDone }
    }

    // Flows for Sort Options
    val TracksSortOptionFlow: Flow<String> =
            dataStore.data.map { preferences ->
                SortOption.fromStorageKey(
                                preferences[PreferencesKeys.Tracks_SORT_OPTION],
                                SortOption.Tracks,
                                SortOption.TrackTitleAZ
                        )
                        .storageKey
            }

    val BooksSortOptionFlow: Flow<String> =
            dataStore.data.map { preferences ->
                SortOption.fromStorageKey(
                                preferences[PreferencesKeys.Books_SORT_OPTION],
                                SortOption.Books,
                                SortOption.BookTitleAZ
                        )
                        .storageKey
            }

    val AuthorsSortOptionFlow: Flow<String> =
            dataStore.data.map { preferences ->
                SortOption.fromStorageKey(
                                preferences[PreferencesKeys.Authors_SORT_OPTION],
                                SortOption.Authors,
                                SortOption.AuthorNameAZ
                        )
                        .storageKey
            }

    val BooklistsSortOptionFlow: Flow<String> =
            dataStore.data.map { preferences ->
                SortOption.fromStorageKey(
                                preferences[PreferencesKeys.Booklists_SORT_OPTION],
                                SortOption.Booklists,
                                SortOption.BooklistNameAZ
                        )
                        .storageKey
            }

    val likedTracksSortOptionFlow: Flow<String> =
            dataStore.data.map { preferences ->
                SortOption.fromStorageKey(
                                preferences[PreferencesKeys.LIKED_Tracks_SORT_OPTION],
                                SortOption.LIKED,
                                SortOption.LikedTrackDateLiked
                        )
                        .storageKey
            }

    // Functions to update Sort Options
    suspend fun setTracksSortOption(optionKey: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.Tracks_SORT_OPTION] = optionKey
            preferences[PreferencesKeys.Tracks_SORT_OPTION_MIGRATED] = true
        }
    }

    suspend fun setBooksSortOption(optionKey: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.Books_SORT_OPTION] = optionKey
        }
    }

    suspend fun setAuthorsSortOption(optionKey: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.Authors_SORT_OPTION] = optionKey
        }
    }

    suspend fun setBooklistsSortOption(optionKey: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.Booklists_SORT_OPTION] = optionKey
        }
    }

    suspend fun setLikedTracksSortOption(optionKey: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LIKED_Tracks_SORT_OPTION] = optionKey
        }
    }

    suspend fun ensureLibrarySortDefaults() {
        dataStore.edit { preferences ->
            val TracksMigrated = preferences[PreferencesKeys.Tracks_SORT_OPTION_MIGRATED] ?: false
            val rawTracksort = preferences[PreferencesKeys.Tracks_SORT_OPTION]
            val resolvedTracksort =
                    SortOption.fromStorageKey(rawTracksort, SortOption.Tracks, SortOption.TrackTitleAZ)
            val shouldForceTrackDefault =
                    !TracksMigrated &&
                            (rawTracksort.isNullOrBlank() ||
                                    rawTracksort == SortOption.TrackTitleZA.storageKey ||
                                    rawTracksort == SortOption.TrackTitleZA.displayName)

            preferences[PreferencesKeys.Tracks_SORT_OPTION] =
                    if (shouldForceTrackDefault) {
                        SortOption.TrackTitleAZ.storageKey
                    } else {
                        resolvedTracksort.storageKey
                    }
            if (!TracksMigrated) {
                preferences[PreferencesKeys.Tracks_SORT_OPTION_MIGRATED] = true
            }

            migrateSortPreference(
                    preferences,
                    PreferencesKeys.Tracks_SORT_OPTION,
                    SortOption.Tracks,
                    SortOption.TrackTitleAZ
            )
            migrateSortPreference(
                    preferences,
                    PreferencesKeys.Books_SORT_OPTION,
                    SortOption.Books,
                    SortOption.BookTitleAZ
            )
            migrateSortPreference(
                    preferences,
                    PreferencesKeys.Authors_SORT_OPTION,
                    SortOption.Authors,
                    SortOption.AuthorNameAZ
            )
            migrateSortPreference(
                    preferences,
                    PreferencesKeys.Booklists_SORT_OPTION,
                    SortOption.Booklists,
                    SortOption.BooklistNameAZ
            )
            migrateSortPreference(
                    preferences,
                    PreferencesKeys.LIKED_Tracks_SORT_OPTION,
                    SortOption.LIKED,
                    SortOption.LikedTrackDateLiked
            )
        }
    }

    private fun migrateSortPreference(
            preferences: MutablePreferences,
            key: Preferences.Key<String>,
            allowed: Collection<SortOption>,
            fallback: SortOption
    ) {
        val resolved = SortOption.fromStorageKey(preferences[key], allowed, fallback)
        if (preferences[key] != resolved.storageKey) {
            preferences[key] = resolved.storageKey
        }
    }

    // --- Library UI State ---
    val lastLibraryTabIndexFlow: Flow<Int> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.LAST_LIBRARY_TAB_INDEX] ?: 0 // Default to 0 (Tracks tab)
            }

    suspend fun saveLastLibraryTabIndex(tabIndex: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LAST_LIBRARY_TAB_INDEX] = tabIndex
        }
    }

    val mockCategoriesEnabledFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.MOCK_Categories_ENABLED] ?: false // Default to false
            }

    suspend fun setMockCategoriesEnabled(enabled: Boolean) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.MOCK_Categories_ENABLED] = enabled }
    }

    val geminiApiKey: Flow<String> =
            dataStore.data.map { preferences -> preferences[PreferencesKeys.GEMINI_API_KEY] ?: "" }

    suspend fun setGeminiApiKey(apiKey: String) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.GEMINI_API_KEY] = apiKey }
    }

    val geminiModel: Flow<String> =
            dataStore.data.map { preferences -> preferences[PreferencesKeys.GEMINI_MODEL] ?: "" }

    suspend fun setGeminiModel(model: String) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.GEMINI_MODEL] = model }
    }

    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
                "You are a helpful AI assistant integrated into a Audiobook player app. You help users create perfect Booklists based on their request."

        /** Default delimiters for splitting multi-Author tags */
        val DEFAULT_Author_DELIMITERS = listOf("/", ";", ",", "+", "&")
    }

    val geminiSystemPrompt: Flow<String> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.GEMINI_SYSTEM_PROMPT] ?: DEFAULT_SYSTEM_PROMPT
            }

    suspend fun setGeminiSystemPrompt(prompt: String) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.GEMINI_SYSTEM_PROMPT] = prompt }
    }

    suspend fun resetGeminiSystemPrompt() {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.GEMINI_SYSTEM_PROMPT] = DEFAULT_SYSTEM_PROMPT
        }
    }

    val navBarCornerRadiusFlow: Flow<Int> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.NAV_BAR_CORNER_RADIUS] ?: 32
            }

    suspend fun setNavBarCornerRadius(radius: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.NAV_BAR_CORNER_RADIUS] = radius
        }
    }

    val navBarStyleFlow: Flow<String> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.NAV_BAR_STYLE] ?: NavBarStyle.DEFAULT
            }

    suspend fun setNavBarStyle(style: String) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.NAV_BAR_STYLE] = style }
    }

    val libraryNavigationModeFlow: Flow<String> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.LIBRARY_NAVIGATION_MODE]
                        ?: LibraryNavigationMode.TAB_ROW
            }

    suspend fun setLibraryNavigationMode(mode: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.LIBRARY_NAVIGATION_MODE] = mode
        }
    }

    val carouselStyleFlow: Flow<String> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.CAROUSEL_STYLE] ?: CarouselStyle.NO_PEEK
            }

    suspend fun setCarouselStyle(style: String) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.CAROUSEL_STYLE] = style }
    }

    val launchTabFlow: Flow<String> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.LAUNCH_TAB] ?: LaunchTab.HOME
            }

    suspend fun setLaunchTab(tab: String) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.LAUNCH_TAB] = tab }
    }

    suspend fun setKeepPlayingInBackground(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEEP_PLAYING_IN_BACKGROUND] = enabled
        }
    }

    suspend fun setDisableCastAutoplay(disabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.DISABLE_CAST_AUTOPLAY] = disabled
        }
    }

    suspend fun setDelayAllFullPlayerContent(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FULL_PLAYER_DELAY_ALL] = enabled
            
            preferences[PreferencesKeys.FULL_PLAYER_DELAY_Book] = enabled
            preferences[PreferencesKeys.FULL_PLAYER_DELAY_METADATA] = enabled
            preferences[PreferencesKeys.FULL_PLAYER_DELAY_PROGRESS] = enabled
            preferences[PreferencesKeys.FULL_PLAYER_DELAY_CONTROLS] = enabled
        }
    }

    suspend fun setDelayBookCarousel(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FULL_PLAYER_DELAY_Book] = enabled
        }
    }

    suspend fun setDelayTrackMetadata(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FULL_PLAYER_DELAY_METADATA] = enabled
        }
    }

    suspend fun setDelayProgressBar(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FULL_PLAYER_DELAY_PROGRESS] = enabled
        }
    }

    suspend fun setDelayControls(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FULL_PLAYER_DELAY_CONTROLS] = enabled
        }
    }

    suspend fun setFullPlayerPlaceholders(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FULL_PLAYER_PLACEHOLDERS] = enabled
            if (!enabled) {
                preferences[PreferencesKeys.FULL_PLAYER_PLACEHOLDER_TRANSPARENT] = false
            }
        }
    }

    suspend fun setTransparentPlaceholders(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FULL_PLAYER_PLACEHOLDER_TRANSPARENT] = enabled
        }
    }

    suspend fun setFullPlayerAppearThreshold(thresholdPercent: Int) {
        val coercedValue = thresholdPercent.coerceIn(50, 100)
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.FULL_PLAYER_DELAY_THRESHOLD] = coercedValue
        }
    }

    val libraryTabsOrderFlow: Flow<String?> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.LIBRARY_TABS_ORDER]
        }

    suspend fun saveLibraryTabsOrder(order: String) {
        dataStore.edit { preferences -> preferences[PreferencesKeys.LIBRARY_TABS_ORDER] = order }
    }

    suspend fun resetLibraryTabsOrder() {
        dataStore.edit { preferences -> preferences.remove(PreferencesKeys.LIBRARY_TABS_ORDER) }
    }

    suspend fun migrateTabOrder() {
        dataStore.edit { preferences ->
            val orderJson = preferences[PreferencesKeys.LIBRARY_TABS_ORDER]
            if (orderJson != null) {
                try {
                    val order = json.decodeFromString<MutableList<String>>(orderJson)
                    if (!order.contains("FOLDERS")) {
                        val likedIndex = order.indexOf("LIKED")
                        if (likedIndex != -1) {
                            order.add(likedIndex + 1, "FOLDERS")
                        } else {
                            order.add("FOLDERS") // Fallback
                        }
                        preferences[PreferencesKeys.LIBRARY_TABS_ORDER] = json.encodeToString(order)
                    }
                } catch (e: Exception) {
                    // Si la deserializaciÃƒÂ³n falla, no hacemos nada para evitar sobrescribir los
                    // datos del usuario.
                }
            }
            // Si orderJson es nulo, significa que el usuario nunca ha reordenado,
            // por lo que se utilizarÃƒÂ¡ el orden predeterminado que ya incluye FOLDERS.
        }
    }

    val isFolderFilterActiveFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.IS_FOLDER_FILTER_ACTIVE] ?: false
            }

    suspend fun setFolderFilterActive(isActive: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_FOLDER_FILTER_ACTIVE] = isActive
        }
    }

    val isFoldersBooklistViewFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.IS_FOLDERS_Booklist_VIEW] ?: false
        }

    val useSmoothCornersFlow: Flow<Boolean> = dataStore.data
        .map { preferences ->
            preferences[PreferencesKeys.USE_SMOOTH_CORNERS] ?: true
        }

    suspend fun setUseSmoothCorners(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.USE_SMOOTH_CORNERS] = enabled
        }
    }

    suspend fun setFoldersBooklistView(isBooklistView: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.IS_FOLDERS_Booklist_VIEW] = isBooklistView
        }
    }

    // ===== Equalizer Settings =====

    val equalizerEnabledFlow: Flow<Boolean> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.EQUALIZER_ENABLED] ?: false
            }

    suspend fun setEqualizerEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.EQUALIZER_ENABLED] = enabled
        }
    }

    val equalizerPresetFlow: Flow<String> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.EQUALIZER_PRESET] ?: "flat"
            }

    suspend fun setEqualizerPreset(preset: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.EQUALIZER_PRESET] = preset
        }
    }

    val equalizerCustomBandsFlow: Flow<List<Int>> =
            dataStore.data.map { preferences ->
                val stored = preferences[PreferencesKeys.EQUALIZER_CUSTOM_BANDS]
                if (stored != null) {
                    try {
                        json.decodeFromString<List<Int>>(stored)
                    } catch (e: Exception) {
                        listOf(0, 0, 0, 0, 0)
                    }
                } else {
                    listOf(0, 0, 0, 0, 0)
                }
            }

    suspend fun setEqualizerCustomBands(bands: List<Int>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.EQUALIZER_CUSTOM_BANDS] = json.encodeToString(bands)
        }
    }

    val bassBoostStrengthFlow: Flow<Int> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.BASS_BOOST_STRENGTH] ?: 0
            }

    suspend fun setBassBoostStrength(strength: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.BASS_BOOST_STRENGTH] = strength.coerceIn(0, 1000)
        }
    }

    val virtualizerStrengthFlow: Flow<Int> =
            dataStore.data.map { preferences ->
                preferences[PreferencesKeys.VIRTUALIZER_STRENGTH] ?: 0
            }

    suspend fun setVirtualizerStrength(strength: Int) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.VIRTUALIZER_STRENGTH] = strength.coerceIn(0, 1000)
        }
    }

    // ===== End Equalizer Settings =====
    // ===== Custom Presets Persistence =====

    val customPresetsFlow: Flow<List<EqualizerPreset>> =
        dataStore.data.map { preferences ->
            val jsonString = preferences[PreferencesKeys.CUSTOM_PRESETS]
            if (jsonString != null) {
                try {
                    json.decodeFromString<List<EqualizerPreset>>(jsonString)
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
        }
        
    suspend fun saveCustomPreset(preset: EqualizerPreset) {
        val current = customPresetsFlow.first().toMutableList()
        // Remove existing if overwriting (by name)
        current.removeAll { it.name == preset.name }
        current.add(preset)
        
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CUSTOM_PRESETS] = json.encodeToString(current)
        }
    }
    
    suspend fun deleteCustomPreset(presetName: String) {
        val current = customPresetsFlow.first().toMutableList()
        current.removeAll { it.name == presetName }
        
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.CUSTOM_PRESETS] = json.encodeToString(current)
        }
        
        // Also remove from pinned if present
        val pinned = pinnedPresetsFlow.first().toMutableList()
        if (pinned.remove(presetName)) {
            setPinnedPresets(pinned)
        }
    }
    
    // ===== Pinned Presets Persistence =====
    
    val pinnedPresetsFlow: Flow<List<String>> =
        dataStore.data.map { preferences ->
            val jsonString = preferences[PreferencesKeys.PINNED_PRESETS]
            if (jsonString != null) {
                try {
                    json.decodeFromString<List<String>>(jsonString)
                } catch (e: Exception) {
                    // Default pinned: All standard presets
                    EqualizerPreset.ALL_PRESETS.map { it.name }
                }
            } else {
                 // Default pinned: All standard presets
                 EqualizerPreset.ALL_PRESETS.map { it.name }
            }
        }
        
    suspend fun setPinnedPresets(presetNames: List<String>) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.PINNED_PRESETS] = json.encodeToString(presetNames)
        }
    }

    // ===== Developer Options =====
    
    /**
     * Book art quality for player view.
     * Controls the maximum resolution for Book artwork displayed in the full player.
     * Thumbnails in lists always use low resolution (256px) for optimal performance.
     */
    val BookArtQualityFlow: Flow<BookArtQuality> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.Book_ART_QUALITY]
                ?.let { 
                    try { BookArtQuality.valueOf(it) } 
                    catch (e: Exception) { BookArtQuality.ORIGINAL }
                }
                ?: BookArtQuality.ORIGINAL
        }

    suspend fun setBookArtQuality(quality: BookArtQuality) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.Book_ART_QUALITY] = quality.name
        }
    }

    /**
     * Whether tapping the background area of the player sheet closes it.
     * Default is true for intuitive dismissal, but power users may prefer to disable this.
     */
    val tapBackgroundClosesPlayerFlow: Flow<Boolean> =
        dataStore.data.map { preferences ->
            preferences[PreferencesKeys.TAP_BACKGROUND_CLOSES_PLAYER] ?: true
        }

    suspend fun setTapBackgroundClosesPlayer(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.TAP_BACKGROUND_CLOSES_PLAYER] = enabled
        }
    }
}
