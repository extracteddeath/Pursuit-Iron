plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "io.github.extracteddeath.pursuitiron"
    compileSdk = 37
    defaultConfig {
        applicationId = "io.github.extracteddeath.pursuitiron"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0-native-foundation"
    }
    buildTypes {
        getByName("debug") {
            // This preview can coexist with a future Play Store package.
            applicationIdSuffix = ".preview"
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
    // Read-only metadata gateway only; training UI still uses UnconnectedEngineGateway.
    implementation(project(":engine-runtime"))
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // Preview channel only: beta API provides the published expressive MotionScheme.
    implementation("androidx.compose.material3:material3:1.5.0-beta02")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
