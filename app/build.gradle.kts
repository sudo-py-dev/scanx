plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
}

import java.util.Properties

val secretsFile = rootProject.file("secrets.properties")
val secrets = Properties().apply {
    if (secretsFile.exists()) {
        secretsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.scanx.qrscanner"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.scanx.qrscanner"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }


    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = secrets.getProperty("RELEASE_STORE_PASSWORD") ?: project.findProperty("RELEASE_STORE_PASSWORD")?.toString() ?: ""
            keyAlias = secrets.getProperty("RELEASE_KEY_ALIAS") ?: project.findProperty("RELEASE_KEY_ALIAS")?.toString() ?: "release"
            keyPassword = secrets.getProperty("RELEASE_KEY_PASSWORD") ?: project.findProperty("RELEASE_KEY_PASSWORD")?.toString() ?: ""
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
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
        viewBinding = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // CameraX
    val cameraxVersion = "1.3.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ML Kit Barcode Scanning (Independent/Bundled - works without Play Services)
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // Gson for serialization
    implementation("com.google.code.gson:gson:2.10.1")

    // ZXing for QR Code generation
    implementation("com.google.zxing:core:3.5.3")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
}
