plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// The Play listing's package name and the next version code come from the release workflow
// (or -P on the command line) so the update lands on the existing listing.
val appId = (findProperty("appId") as String?) ?: "io.github.extracteddeath.pursuitiron"
val appVersionCode = ((findProperty("versionCode") as String?) ?: "1").toInt()
val appVersionName = (findProperty("versionName") as String?) ?: "4.0.0-native"
val keystorePath = System.getenv("PI_KEYSTORE_PATH")

android {
    namespace = "com.pursuitiron.app"
    compileSdk = 37

    defaultConfig {
        applicationId = appId
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (keystorePath != null) {
            create("upload") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("PI_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PI_KEY_ALIAS")
                keyPassword = System.getenv("PI_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePath != null) signingConfig = signingConfigs.getByName("upload")
        }
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    androidResources {
        // The engine bundle is read as text; never compress-and-mangle it.
        noCompress += "js"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.javascriptengine:javascriptengine:1.1.1")
    implementation("androidx.concurrent:concurrent-futures-ktx:1.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
