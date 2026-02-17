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
    compileSdk = 35

    defaultConfig {
        applicationId = "com.scanx.qrscanner"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.1"
    }


    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = secrets.getProperty("RELEASE_STORE_PASSWORD") ?: project.findProperty("RELEASE_STORE_PASSWORD")?.toString() ?: System.getenv("RELEASE_STORE_PASSWORD") ?: ""
            keyAlias = secrets.getProperty("RELEASE_KEY_ALIAS") ?: project.findProperty("RELEASE_KEY_ALIAS")?.toString() ?: System.getenv("RELEASE_KEY_ALIAS") ?: "release"
            keyPassword = secrets.getProperty("RELEASE_KEY_PASSWORD") ?: project.findProperty("RELEASE_KEY_PASSWORD")?.toString() ?: System.getenv("RELEASE_KEY_PASSWORD") ?: ""
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("googlePlay") {
            dimension = "distribution"
            // applicationIdSuffix = ".play"
            versionNameSuffix = "-play"
        }
        create("github") {
            dimension = "distribution"
            applicationIdSuffix = ".github"
            versionNameSuffix = "-github"
        }
        create("foss") {
            dimension = "distribution"
            applicationIdSuffix = ".foss"
            versionNameSuffix = "-foss"
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
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    kotlinOptions {
        jvmTarget = "21"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
            keepDebugSymbols.add("**/libbarhopper_v3.so")
            keepDebugSymbols.add("**/libimage_processing_util_jni.so")
        }
    }

}

dependencies {
    // AndroidX Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    // CameraX
    val cameraxVersion = "1.4.0"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")

    // ML Kit Barcode Scanning
    // Play Services version for Google Play & GitHub (smaller)
    "googlePlayImplementation"("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    "githubImplementation"("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
    // Independent/Bundled version for FOSS (works without Play Services)
    "fossImplementation"("com.google.mlkit:barcode-scanning:18.0.0")

    // Gson for serialization
    implementation("com.google.code.gson:gson:2.11.0")

    // ZXing for QR Code generation
    implementation("com.google.zxing:core:3.5.3")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
}
