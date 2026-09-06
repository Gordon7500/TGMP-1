plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// GitHub Actions sets GITHUB_RUN_NUMBER and increments it on every single workflow run,
// so each build you install gets a genuinely new, always-increasing version — no more
// wondering whether the APK on your phone is the latest one.
val ciRunNumber: Int = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1

android {
    namespace = "com.tuned.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tuned.app"
        minSdk = 26
        targetSdk = 34
        versionCode = ciRunNumber
        versionName = "1.0.$ciRunNumber"
    }

    signingConfigs {
        // A fixed debug signing key checked into the repo (as base64 text, decoded by the
        // CI workflow before the build runs). Without this, GitHub generates a brand-new
        // random debug key on every single build, which means Android refuses to install
        // a new APK over the old one (signature mismatch) and forces a full uninstall each
        // time. With a stable key, new builds install as clean updates over the old one.
        create("stableDebug") {
            val keystoreFile = file("tuned-debug.keystore")
            if (keystoreFile.exists()) {
                storeFile = keystoreFile
                storePassword = "tuned123"
                keyAlias = "tuned-debug"
                keyPassword = "tuned123"
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            if (file("tuned-debug.keystore").exists()) {
                signingConfig = signingConfigs.getByName("stableDebug")
            }
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources.excludes.add("/META-INF/{AL2.0,LGPL2.1}")
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.activity:activity-ktx:1.9.1")

    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.media:media:1.7.0")
}
