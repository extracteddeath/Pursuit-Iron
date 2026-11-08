plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
// The one-shot device workflow provides its monotonically increasing run
// number. Routine source-only checks retain the existing development code.
val kotlinLabBuildNumber = providers.gradleProperty("kotlinLabBuildNumber")
    .orNull?.toIntOrNull()?.takeIf { it in 1..2_000_000_000 }
// Only the separately gated milestone supplies this. No shared debug app ID
// may be updated under different ephemeral GitHub runner certificates.
val kotlinLabDisposableRun = providers.gradleProperty("kotlinLabDisposableRun")
    .orNull?.toIntOrNull()?.takeIf { it in 1..2_000_000_000 }
if(providers.gradleProperty("kotlinLabDisposableRun").isPresent &&
    kotlinLabDisposableRun == null)
    error("Disposable Lab requires a positive per-run identity")

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
    signingConfigs {
        // Only the opt-in Kotlin Lab device pipeline supplies these values.
        // Source CI may compile without them; assembling a distributable
        // Kotlin Lab APK is separately blocked in the workflow until a
        // private, stable signing key is configured in GitHub secrets.
        getByName("debug") {
            val keyFile=System.getenv("KOTLIN_LAB_SIGNING_STORE_FILE")
            if(!keyFile.isNullOrBlank()) {
                storeFile=file(keyFile)
                storePassword=System.getenv("KOTLIN_LAB_SIGNING_STORE_PASSWORD")
                    ?:error("Missing stable Kotlin Lab store password")
                keyAlias=System.getenv("KOTLIN_LAB_SIGNING_KEY_ALIAS")
                    ?:error("Missing stable Kotlin Lab key alias")
                keyPassword=System.getenv("KOTLIN_LAB_SIGNING_KEY_PASSWORD")
                    ?:error("Missing stable Kotlin Lab key password")
            }
        }
    }
    buildTypes {
        getByName("debug") {
            // This isolated Kotlin Lab is NOT the other developer's
            // native preview. Distinct app-private storage and a separate
            // install identity prevent a debug APK from overwriting it.
            applicationIdSuffix = ".kotlinlabstable"
            manifestPlaceholders["kotlinLabDisplayName"] = "Pursuit Iron Lab Stable"
            if(kotlinLabDisposableRun!=null) {
                // Different app per run: ephemeral debug keys cannot erase
                // an older Lab's app-private workout data.
                applicationIdSuffix = ".kotlinlabtrial$kotlinLabDisposableRun"
                manifestPlaceholders["kotlinLabDisplayName"] =
                    "Pursuit Iron Lab Trial $kotlinLabDisposableRun"
            }
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
