plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fangfei.lyricview.sample"  // <- namespace di sini
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fangfei.lyricview.sample"
        minSdk = 21
        targetSdk = 34
    }

    // TAMBAHKAN INI JUGA:
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(project(":lyricview"))  // <-- PENTING: pake library kita
    
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}