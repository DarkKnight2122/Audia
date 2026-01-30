# Audia Release ProGuard Configuration

# Disable obfuscation - keep class/method names readable
-dontobfuscate

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Netty / Ktor / Logging / OkHttp - Ignore all missing optional classes
-dontwarn io.netty.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.apache.log4j.**
-dontwarn org.eclipse.jetty.**
-dontwarn reactor.blockhound.**
-dontwarn javax.lang.model.**
-dontwarn java.lang.management.**
-dontwarn okhttp3.internal.publicsuffix.PublicSuffixDatabase
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Media3 / ExoPlayer - Keep everything
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Hilt / Dagger - Keep everything
-keep class dagger.hilt.** { *; }
-keep class com.oakiha.audia.di.** { *; }

# Room - Keep everything
-keep class androidx.room.** { *; }

# Coil
-dontwarn coil.util.CoilUtils

# [NUEVO] Kotlin Metadata
-keep class kotlin.Metadata { *; }

# Keep models and viewmodels to prevent R8 from stripping them
-keep class com.oakiha.audia.data.model.** { *; }
-keep class com.oakiha.audia.presentation.viewmodel.** { *; }

# TagLib
-keep class com.kyant.taglib.** { *; }