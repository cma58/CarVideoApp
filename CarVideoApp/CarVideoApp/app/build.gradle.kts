plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.example.carvideo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.carvideo"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    // NOTE (targetSdk 37 / Android 17):
    // - Certificate Transparency is enabled by default. NewPipe traffic to
    //   YouTube uses standard CT-logged certs, so this is normally fine.
    // - Local network access now requires runtime permission; this app only
    //   talks to the public internet, so no LAN permission is declared.
    // - Native libs loaded via System.load() must be read-only (not relevant here).

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Kotlin + coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-service:2.8.3")

    // AndroidX Media3 (ExoPlayer + MediaSession)
    val media3 = "1.4.0"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    // AndroidX Car App Library (Android Auto)
    val car = "1.4.0"
    implementation("androidx.car.app:app:$car")
    implementation("androidx.car.app:app-projected:$car")

    // NewPipeExtractor (YouTube scraping). Pin a known tag.
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.24.3")

    // NewPipe needs an HTTP client for its Downloader impl
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
