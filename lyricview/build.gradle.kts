plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fangfei.lyricview"  // <- namespace pindah ke sini!
    compileSdk = 34

    defaultConfig {
        minSdk = 21
    }

    // TAMBAHKAN INI:
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17   // Samain dengan Kotlin
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"  // Bisa juga "1.8", yang penting sama semua
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
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
}