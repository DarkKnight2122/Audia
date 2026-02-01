package com.oakiha.audia.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.Room
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.oakiha.audia.AudioBookApp
import com.oakiha.audia.data.database.BookArtThemeDao
import com.oakiha.audia.data.database.EngagementDao
import com.oakiha.audia.data.database.FavoritesDao
import com.oakiha.audia.data.database.LyricsDao
import com.oakiha.audia.data.database.AudiobookDao
import com.oakiha.audia.data.database.AudioBookDatabase
import com.oakiha.audia.data.database.SearchHistoryDao
import com.oakiha.audia.data.database.TransitionDao
import com.oakiha.audia.data.preferences.UserPreferencesRepository
import com.oakiha.audia.data.preferences.dataStore
import com.oakiha.audia.data.media.TrackMetadataEditor
import com.oakiha.audia.data.network.deezer.DeezerApiService
import com.oakiha.audia.data.network.lyrics.LrcLibApiService
import com.oakiha.audia.data.repository.AuthorImageRepository
import com.oakiha.audia.data.repository.LyricsRepository
import com.oakiha.audia.data.repository.LyricsRepositoryImpl
import com.oakiha.audia.data.repository.AudiobookRepository
import com.oakiha.audia.data.repository.AudiobookRepositoryImpl
import com.oakiha.audia.data.repository.TransitionRepository
import com.oakiha.audia.data.repository.TransitionRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideApplication(@ApplicationContext app: Context): AudioBookApp {
        return app as AudioBookApp
    }

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = context.dataStore

    @Singleton
    @Provides
    fun provideJson(): Json { // Proveer Json
        return Json {
            isLenient = true
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
    }

    @Singleton
    @Provides
    fun provideAudioBookDatabase(@ApplicationContext context: Context): AudioBookDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AudioBookDatabase::class.java,
            "audiobook_database"
        ).addMigrations(
            AudioBookDatabase.MIGRATION_3_4,
            AudioBookDatabase.MIGRATION_4_5,
            AudioBookDatabase.MIGRATION_6_7,
            AudioBookDatabase.MIGRATION_9_10,
            AudioBookDatabase.MIGRATION_10_11,
            AudioBookDatabase.MIGRATION_11_12,
            AudioBookDatabase.MIGRATION_12_13,
            AudioBookDatabase.MIGRATION_13_14
        ).fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Singleton
    @Provides
    fun provideBookArtThemeDao(database: AudioBookDatabase): BookArtThemeDao {
        return database.bookArtThemeDao()
    }

    @Singleton
    @Provides
    fun provideSearchHistoryDao(database: AudioBookDatabase): SearchHistoryDao { // NUEVO MÃƒâ€°TODO
        return database.searchHistoryDao()
    }

    @Singleton
    @Provides
    fun provideAudiobookDao(database: AudioBookDatabase): AudiobookDao { // Proveer MusicDao
        return database.audiobookDao()
    }

    @Singleton
    @Provides
    fun provideTransitionDao(database: AudioBookDatabase): TransitionDao {
        return database.transitionDao()
    }

    @Singleton
    @Provides
    fun provideEngagementDao(database: AudioBookDatabase): EngagementDao {
        return database.engagementDao()
    }

    @Singleton
    @Provides
    fun provideFavoritesDao(database: AudioBookDatabase): FavoritesDao {
        return database.favoritesDao()
    }

    @Singleton
    @Provides
    fun provideLyricsDao(database: AudioBookDatabase): LyricsDao {
        return database.lyricsDao()
    }

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context
    ): ImageLoader {
        return ImageLoader.Builder(context)
            .dispatcher(Dispatchers.Default) // Use CPU-bound dispatcher for decoding
            .allowHardware(true) // Re-enable hardware bitmaps for better performance
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.20) // Use 20% of app memory for image cache
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100 MB disk cache
                    .build()
            }
            .respectCacheHeaders(false) // Ignore server cache headers, always cache
            .build()
    }

    @Provides
    @Singleton
    fun provideLyricsRepository(
        @ApplicationContext context: Context,
        lrcLibApiService: LrcLibApiService,
        audiobookDao: AudiobookDao,
        lyricsDao: LyricsDao
    ): LyricsRepository {
        return LyricsRepositoryImpl(
            context = context,
            lrcLibApiService = lrcLibApiService,
            //audiobookDao = audiobookDao,
            lyricsDao = lyricsDao
        )
    }

    @Provides
    @Singleton
    fun provideTrackRepository(
        @ApplicationContext context: Context,
        mediaStoreObserver: com.oakiha.audia.data.observer.MediaStoreObserver,
        favoritesDao: FavoritesDao,
        userPreferencesRepository: UserPreferencesRepository
    ): com.oakiha.audia.data.repository.TrackRepository {
        return com.oakiha.audia.data.repository.MediaStoreTrackRepository(
            context = context,
            mediaStoreObserver = mediaStoreObserver,
            favoritesDao = favoritesDao,
            userPreferencesRepository = userPreferencesRepository
        )
    }

    @Provides
    @Singleton
    fun provideAudiobookRepository(
        @ApplicationContext context: Context,
        userPreferencesRepository: UserPreferencesRepository,
        searchHistoryDao: SearchHistoryDao,
        audiobookDao: AudiobookDao,
        lyricsRepository: LyricsRepository,
        trackRepository: com.oakiha.audia.data.repository.TrackRepository,
        favoritesDao: FavoritesDao
    ): AudiobookRepository {
        return AudiobookRepositoryImpl(
            context = context,
            userPreferencesRepository = userPreferencesRepository,
            searchHistoryDao = searchHistoryDao,
            audiobookDao = audiobookDao,
            lyricsRepository = lyricsRepository,
            trackRepository = trackRepository,
            favoritesDao = favoritesDao
        )
    }

    @Provides
    @Singleton
    fun provideTransitionRepository(
        transitionRepositoryImpl: TransitionRepositoryImpl
    ): TransitionRepository {
        return transitionRepositoryImpl
    }

    @Singleton
    @Provides
    fun provideTrackMetadataEditor(@ApplicationContext context: Context, audiobookDao: AudiobookDao): TrackMetadataEditor {
        return TrackMetadataEditor(context, audiobookDao)
    }

    /**
     * Provee una instancia singleton de OkHttpClient con un interceptor de logging.
     * Configured with 10s timeout and retry logic (2 retries).
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)
        
        // Connection pool with optimized connections for better performance
        val connectionPool = okhttp3.ConnectionPool(
            maxIdleConnections = 5,
            keepAliveDuration = 30,
            timeUnit = java.util.concurrent.TimeUnit.SECONDS
        )
        
        return OkHttpClient.Builder()
            .connectionPool(connectionPool)
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            // Add User-Agent header (required by some APIs)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithUserAgent = originalRequest.newBuilder()
                    .header("User-Agent", "AudioBookPlayer/1.0 (Android; Audiobook Player)")
                    .build()
                chain.proceed(requestWithUserAgent)
            }
            // Retry interceptor
            .addInterceptor { chain ->
                var request = chain.request()
                var response: okhttp3.Response? = null
                var lastException: java.io.IOException? = null
                
                // Retry up to 2 times
                repeat(3) { attempt ->
                    try {
                        response?.close()
                        response = chain.proceed(request)
                        if (response!!.isSuccessful || response!!.code == 404) {
                            return@addInterceptor response!!
                        }
                    } catch (e: java.io.IOException) {
                        lastException = e
                        if (attempt < 2) {
                            // Exponential backoff: 500ms, 1000ms
                            Thread.sleep((500L * (attempt + 1)))
                        }
                    }
                }
                
                // If we have a response, return it; otherwise throw the last exception
                response ?: throw (lastException ?: java.io.IOException("Unknown network error"))
            }
            .addInterceptor(loggingInterceptor)
            .build()
    }

    /**
     * Provee una instancia de OkHttpClient con timeouts para bÃƒÂºsquedas de lyrics.
     * Includes DNS resolver, modern TLS, connection pool, and retry logic.
     */
    @Provides
    @Singleton
    @FastOkHttpClient
    fun provideFastOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.HEADERS)
        
        // Connection pool to reuse connections for better performance
        val connectionPool = okhttp3.ConnectionPool(
            maxIdleConnections = 5,
            keepAliveDuration = 30,
            timeUnit = java.util.concurrent.TimeUnit.SECONDS
        )
        
        // Use Cloudflare and Google DNS to avoid potential DNS issues
        val dns = okhttp3.Dns { hostname ->
            try {
                // First try system DNS
                okhttp3.Dns.SYSTEM.lookup(hostname)
            } catch (e: Exception) {
                // Fallback to manual resolution if system DNS fails
                java.net.InetAddress.getAllByName(hostname).toList()
            }
        }

        return OkHttpClient.Builder()
            .dns(dns)
            .connectionPool(connectionPool)
            // Use HTTP/1.1 to avoid HTTP/2 stream issues with some servers
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            // Use modern TLS connection spec
            .connectionSpecs(listOf(
                okhttp3.ConnectionSpec.MODERN_TLS,
                okhttp3.ConnectionSpec.COMPATIBLE_TLS,
                okhttp3.ConnectionSpec.CLEARTEXT
            ))
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            // Enable built-in retry on connection failure
            .retryOnConnectionFailure(true)
            // Add headers
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val requestWithHeaders = originalRequest.newBuilder()
                    .header("User-Agent", "AudioBookPlayer/1.0 (Android; Audiobook Player)")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(requestWithHeaders)
            }
            .addInterceptor(loggingInterceptor)
            .build()
    }

    /**
     * Provee una instancia singleton de Retrofit para la API de LRCLIB.
     */
    @Provides
    @Singleton
    fun provideRetrofit(@FastOkHttpClient okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://lrclib.net/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Provee una instancia singleton del servicio de la API de LRCLIB.
     */
    @Provides
    @Singleton
    fun provideLrcLibApiService(retrofit: Retrofit): LrcLibApiService {
        return retrofit.create(LrcLibApiService::class.java)
    }

    /**
     * Provee una instancia de Retrofit para la API de Deezer.
     */
    @Provides
    @Singleton
    @DeezerRetrofit
    fun provideDeezerRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.deezer.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * Provee el servicio de la API de Deezer.
     */
    @Provides
    @Singleton
    fun provideDeezerApiService(@DeezerRetrofit retrofit: Retrofit): DeezerApiService {
        return retrofit.create(DeezerApiService::class.java)
    }

    /**
     * Provee el repositorio de imÃƒÂ¡genes de artistas.
     */
    @Provides
    @Singleton
    fun provideAuthorImageRepository(
        deezerApiService: DeezerApiService,
        audiobookDao: AudiobookDao
    ): AuthorImageRepository {
        return AuthorImageRepository(deezerApiService, audiobookDao)
    }
}

