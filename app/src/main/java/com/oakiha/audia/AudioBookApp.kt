package com.oakiha.audia

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.oakiha.audia.data.worker.SyncManager
import com.oakiha.audia.utils.CrashHandler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import android.os.StrictMode
import coil.ImageLoader
import coil.ImageLoaderFactory
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import timber.log.Timber

@HiltAndroidApp
class AudioBookApp : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var imageLoader: dagger.Lazy<ImageLoader>

    companion object {
        const val NOTIFICATION_CHANNEL_ID = ""AudioBookPlayer_music_channel""
    }

    override fun onCreate() {
        super.onCreate()
        
        CrashHandler.install(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                ""AudioBookPlayer Music Playback"",
                NotificationManager.IMPORTANCE_LOW
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun newImageLoader(): ImageLoader {
        return imageLoader.get()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}