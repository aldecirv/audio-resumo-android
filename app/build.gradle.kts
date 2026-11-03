plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val auroraKeystorePath: String? = System.getenv("AURORA_KEYSTORE_PATH")

android {
    namespace = "com.audioresumo.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.audioresumo.app"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "2.0.2"
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (auroraKeystorePath != null) {
            create("release") {
                storeFile = file(auroraKeystorePath)
                storePassword = System.getenv("AURORA_KEYSTORE_PASS")
                keyAlias = System.getenv("AURORA_KEY_ALIAS")
                keyPassword = System.getenv("AURORA_KEY_PASS")
            }
        }
    }

    buildTypes {
        debug {
            if (auroraKeystorePath != null) signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = false
            if (auroraKeystorePath != null) signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { viewBinding = true; buildConfig = true }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")

    // OCR offline para PDFs digitalizados.
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
}

android {
    testOptions { unitTests.isIncludeAndroidResources = true }
}
