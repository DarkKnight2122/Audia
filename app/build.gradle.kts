plugins {
    alias(libs.plugins.android.application)
    id("com.google.devtools.ksp") version "2.1.0-1.0.29"
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.dagger.hilt.android)
    kotlin("plugin.serialization") version "2.1.0"
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.oakiha.audia"
    compileSdk = 36

    androidResources {
        noCompress.add("tflite")
    }

    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "/META-INF/io.netty.versions.properties"
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    defaultConfig {
        applicationId = "com.oakiha.audia"
        minSdk = 29 // Bumped to match PixelPlayer requirements
        targetSdk = 36
        
        // Use tag_name from project property if available (e.g. from GitHub Actions)
        val tagName = project.findProperty("tag_name") as String?
        versionName = tagName ?: "0.1.0-beta"
        
        val appVersionCode = project.findProperty("APP_VERSION_CODE") as String?
        versionCode = appVersionCode?.toInt() ?: 3

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS")
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    applicationVariants.all {
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            val abiRaw = output.filters.find { it.filterType == "ABI" }?.identifier ?: "universal"
            val abi = when(abiRaw) {
                "arm64-v8a" -> "armv8"
                "armeabi-v7a" -> "armv7"
                "x86_64" -> "x64"
                "x86" -> "x86"
                else -> "univ"
            }
            val type = buildType.name
            val version = versionName
            output.outputFileName = "Audia-${version}-${abi}-${type}.apk"
        }
    }
}

dependencies {
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.paging.common)
    // "baselineProfile"(project(":baselineprofile")) // Module not present yet
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.generativeai)
    implementation(libs.androidx.mediarouter)
    implementation(libs.play.services.cast.framework)
    implementation(libs.androidx.navigation.runtime.ktx)
    implementation(libs.androidx.compose.material3)
    
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    
    // Paging 3
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // Glance
    implementation(libs.androidx.glance)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    //Gson
    implementation(libs.gson)

    //Serialization
    implementation(libs.kotlinx.serialization.json)

    //Work
    implementation(libs.androidx.work.runtime.ktx)

    //Duktape
    implementation(libs.duktape.android)

    //Smooth corners shape
    implementation(libs.smooth.corner.rect.android.compose)
    implementation(libs.androidx.graphics.shapes)

    //Navigation
    implementation(libs.androidx.navigation.compose)

    //Animations
    implementation(libs.androidx.animation)

    //Material3
    implementation(libs.material3)
    implementation("androidx.compose.material3:material3-window-size-class:1.3.1")

    //Coil
    implementation(libs.coil.compose)

    //Capturable
    implementation(libs.capturable)

    //Reorderable List/Drag and Drop
    implementation(libs.compose.dnd)
    implementation(libs.reorderables)

    //CodeView
    implementation(libs.codeview)

    //AppCompat
    implementation(libs.androidx.appcompat)

    // Media3 ExoPlayer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media.router)
    implementation(libs.google.play.services.cast.framework)
    implementation(libs.androidx.media3.exoplayer.ffmpeg)

    // Palette API
    implementation(libs.androidx.palette.ktx)

    // ConstraintLayout
    implementation(libs.androidx.constraintlayout.compose)

    // Foundation
    implementation(libs.androidx.foundation)
    
    // Wavy slider
    implementation(libs.wavy.slider)

    // Splash Screen API
    implementation(libs.androidx.core.splashscreen)

    // Icons
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.material.icons.extended)

    // Material library
    implementation(libs.material)

    // Kotlin Collections
    implementation(libs.kotlinx.collections.immutable)

    // Gemini
    implementation(libs.google.genai)

    // Permissions
    implementation(libs.accompanist.permissions)

    // Audio processing
    implementation(libs.amplituda)
    implementation(libs.compose.audiowaveform)
    implementation(libs.androidx.media3.transformer)

    // Checker framework
    implementation(libs.checker.qual)

    // Timber
    implementation(libs.timber)

    // TagLib & Metadata
    implementation(libs.taglib)
    implementation(libs.vorbisjava.core)

    // Retrofit & OkHttp
    implementation(libs.retrofit)
    implementation(libs.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.logging.interceptor)

    // Ktor
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.androidx.ui.text.google.fonts)
    implementation(libs.accompanist.drawablepainter)
    implementation(kotlin("test"))

    // Android Auto
    implementation(libs.androidx.media)
    implementation(libs.androidx.app)
    implementation(libs.androidx.app.projected)
}
