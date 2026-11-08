plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
// The one-shot device workflow provides its monotonically increasing run
// number. Routine source-only checks retain the existing development code.
val kotlinLabBuildNumber = providers.gradleProperty("kotlinLabBuildNumber")
    .orNull?.toIntOrNull()?.takeIf { it in 1..2_000_000_000 }

android {
    namespace = "io.github.extracteddeath.pursuitiron"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.extracteddeath.pursuitiron"
        minSdk = 26
        targetSdk = 36
        versionCode = kotlinLabBuildNumber ?: 1
        versionName = kotlinLabBuildNumber?.let { "0.1.0-kotlin-lab.$it" }
            ?: "0.1.0-native-foundation"
    }
    buildTypes {
        getByName("debug") {
            // This isolated Kotlin Lab is NOT the other developer's
            // native preview. Distinct app-private storage and a separate
            // install identity prevent a debug APK from overwriting it.
            applicationIdSuffix = ".kotlinlab"
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(project(":domain-contract"))
    // Verified pure canonical runtime; NativeRuntimeHost owns session persistence.
    implementation(project(":engine-runtime"))
    // Isolated native Kotlin coach candidate; NOT selected by live screens.
    implementation(project(":kotlin-engine"))
    implementation(project(":native-coach-store"))
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // Preview channel only: beta API provides the published expressive MotionScheme.
    implementation("androidx.compose.material3:material3:1.5.0-beta02")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
