import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

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
    // Historical JS adapter is exclusively for development/old source tests.
    // Never link its JavaScript sandbox, assets or metadata worker into beta.
    debugImplementation(project(":engine-runtime"))
    // The beta launcher and workout journey select this ONE Kotlin engine.
    implementation(project(":kotlin-engine"))
    implementation(project(":native-coach-store"))
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    // Material 3 Expressive 1.5 release candidate, with published MotionScheme.
    implementation("androidx.compose.material3:material3:1.5.0-rc01")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")
}


// A deliberately small, FAIL-CLOSED source allowlist for the Android beta.
// Old preview/JS workflow screens stay in the development source tree for
// existing historical regression tests, and compile only for debug.
// The release compiler sees the same signed Kotlin host, Compose screens and
// shared Material primitives; it cannot silently include another old engine
// when a legacy file is added to src/main in a future change.
val earlyBetaKotlinSources=listOf(
    "**/NativeKotlin*.kt",
    "**/AppPrivateKotlinLocalAthlete.kt",
    "**/MainActivity.kt",
    "**/PursuitIronApplication.kt",
    "**/NativeComponents.kt",
    "**/NativeDesignTokens.kt",
    "**/NativeAppearanceSheet.kt",
    "**/NativeExerciseFavorites.kt",
    "**/NativeStrengthFavorites.kt",
    "**/PursuitDesignComponents.kt"
)
tasks.withType<KotlinJvmCompile>().configureEach {
    if(name=="compileReleaseKotlin") {
        includes.addAll(earlyBetaKotlinSources)
    }
}

// Verify both what Gradle compiled and what it plans to package. This can
// run in source-only CI and never assembles a distributable APK or AAB.
val verifyEarlyBetaSourceClasses by tasks.registering {
    group="verification"
    description="Verify release Kotlin classes exclude historic preview/JS screens"
    dependsOn("compileReleaseKotlin")
    doLast {
        val compileTask=tasks.findByName("compileReleaseKotlin") as? KotlinJvmCompile
            ?:error("Kotlin release compile task is unavailable")
        check(earlyBetaKotlinSources.all { it in compileTask.includes }) {
            "Early beta release must enforce the explicit Kotlin-only source allowlist"
        }
        val compiled=compileTask.destinationDirectory.get().asFile
        check(compiled.isDirectory) { "Release Kotlin classes were not compiled" }
        val legacyTypes=listOf("NativeRuntimeHost", "NativePreviewApp",
            "NativeWorkoutScreen", "NativeOnboardingScreen",
            "PackagedReadOnlySchemaVerifier", "NativeProgramDraftEditor",
            "NativeScreens", "SyntheticCanonicalWorkoutPanel")
        val prohibited=compiled.walkTopDown().filter {
            it.isFile && it.extension=="class" &&
                legacyTypes.any { type ->
                    it.nameWithoutExtension==type ||
                        it.nameWithoutExtension.startsWith(type+"$")
                }
        }.map {it.relativeTo(compiled).path}.toList()
        check(prohibited.isEmpty()) {
            "Legacy JS/preview Kotlin classes leaked into beta: $prohibited"
        }
        check(compiled.walkTopDown().any {
            it.isFile && it.name=="MainActivity.class"
        }) { "Kotlin beta launcher was not compiled" }
    }
}

// External beta is NOT approved yet. Keep source/release dependency checks
// usable in CI, but do not produce a release artifact before data export,
// phone accessibility, signed upgrade and end-to-end training acceptance.
// This gate must be deliberately replaced by evidence-backed release policy.
val verifyEarlyBetaAcceptance by tasks.registering {
    group="verification"
    description="Block release artifacts until actual early beta acceptance"
    doLast {
        error("Early beta artifact is NOT authorized: complete on-device " +
            "Kotlin workout journey, source/data recovery and stable-signed " +
            "upgrade acceptance before changing this gate. No APK/AAB yet.")
    }
}

// Early-launch beta must ship ONE Kotlin engine. The existing dev/debug app
// deliberately still hosts the legacy JS runtime during cutover; it is NOT
// releasable. Fail the release variant before artifact generation while that
// parallel engine would be packaged. Tests and historical migration tools may
// remain in the repository; they are not an excuse to ship two runtimes.
val verifyEarlyBetaRuntime by tasks.registering {
    group = "verification"
    description = "Refuse a beta release if legacy JS engine-runtime is packaged"
    doLast {
        val components = configurations.getByName("releaseRuntimeClasspath")
            .incoming.resolutionResult.allComponents
        val containsLegacyRuntime = components.any { result ->
            val component = result.id as? ProjectComponentIdentifier
            component?.projectPath == ":engine-runtime"
        }
        check(!containsLegacyRuntime) {
            "Early beta release blocked: the app still packages :engine-runtime " +
                "(the legacy JS execution path). Cut MainActivity and its " +
                "runtime host over to the ONE Kotlin engine, remove the old " +
                "runtime dependency, then rerun the full native beta gates."
        }
    }
}
// CI must still be able to compile BOTH debug and release source without
// assembling any artifact. Bind this to actual distributable packaging, not
// preReleaseBuild (a dependency of source compilation). The final unsigned
// or signed artifact is refused while the parallel JS runtime is packaged.
tasks.matching {
    it.name in setOf(
        "packageRelease", "assembleRelease",
        "bundleRelease", "packageReleaseBundle"
    )
}.configureEach {
    dependsOn(verifyEarlyBetaRuntime,verifyEarlyBetaSourceClasses,
        verifyEarlyBetaAcceptance)
}
