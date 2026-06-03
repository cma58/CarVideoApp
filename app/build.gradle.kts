plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val privateVersionCode = (findProperty("privateVersionCode") as String?)?.toIntOrNull() ?: 3
val privateVersionName = (findProperty("privateVersionName") as String?) ?: "1.2-private"

android {
    namespace = "com.example.carvideo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.example.carvideo"
        minSdk = 29
        targetSdk = 37
        versionCode = privateVersionCode
        versionName = privateVersionName
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("../debug-fixed.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        create("privateRelease") {
            // Private/sideload build: stable signing key so updates install over the previous APK.
            // For a public app, use GitHub Secrets or Play App Signing instead.
            storeFile = file("../debug-fixed.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            signingConfig = signingConfigs.getByName("privateRelease")
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

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.lifecycle:lifecycle-service:2.10.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    // Compose BOM + Material 3
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended")

    // Media3
    val media3 = "1.10.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-exoplayer-dash:$media3")
    implementation("androidx.media3:media3-session:$media3")
    implementation("androidx.media3:media3-ui:$media3")

    // Car App
    val car = "1.7.0"
    implementation("androidx.car.app:app:$car")
    implementation("androidx.car.app:app-projected:$car")

    // Media for MediaStyle notifications
    implementation("androidx.media:media:1.8.0")

    // NewPipe v0.26.1 (werkende versie) + OkHttp
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.1")
    implementation("com.squareup.okhttp3:okhttp:5.3.2")

    // Coil voor Compose (thumbnails)
    implementation("io.coil-kt:coil-compose:2.7.0")
    
    // Palette for dynamic colors from images
    implementation("androidx.palette:palette-ktx:1.0.0")
}
