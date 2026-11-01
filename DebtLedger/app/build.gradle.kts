plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The CI workflow passes -PversionCode=<run number> so every build can update the previous install.
val vc = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.daftar.deyoun"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.daftar.deyoun"
        minSdk = 26
        targetSdk = 34
        versionCode = vc
        versionName = "1.0.$vc"
    }

    // A fixed signing key keeps the app's fingerprint (SHA-1) the same on every build.
    // Google sign-in is tied to that fingerprint, so it must not change between builds.
    signingConfigs {
        create("daftar") {
            storeFile = rootProject.file("daftar.jks")
            storePassword = "daftar-ledger"
            keyAlias = "daftar"
            keyPassword = "daftar-ledger"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("daftar")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("daftar")
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
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.03")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
}
