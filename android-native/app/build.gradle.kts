import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * AGP 9 built-in Kotlin uses whole source directories; it does not support
 * include patterns on AndroidSourceSet.kotlin. Stage ONLY the approved beta
 * sources into a generated directory with a proper Gradle task dependency.
 * Legacy preview source remains in the repo for audit, not in the app.
 */
abstract class StageKotlinBetaSources @Inject constructor(
    private val fileOps:FileSystemOperations
):DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val betaFiles:ConfigurableFileCollection

    @get:OutputDirectory
    abstract val outputDirectory:DirectoryProperty

    @TaskAction fun stage() {
        fileOps.sync {
            from(betaFiles)
            into(outputDirectory)
        }
    }
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
    // No original app source root is compiled by default. The generated
    // source directory below is the ONLY Kotlin beta app code. JavaScript
    // preview sources are retained for parity/migration work, not packaged.
    sourceSets {
        getByName("main") {
            kotlin.setSrcDirs(emptyList<String>())
            java.setSrcDirs(emptyList<String>())
        }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
// Explicit app source inventory: any new screen needs an intentional
// allowlist change AND Kotlin/Compose verification before the beta includes it.
val stageKotlinBetaSources=tasks.register<StageKotlinBetaSources>(
    "stageKotlinBetaSources"
) {
    betaFiles.from(fileTree("src/main/java") {
        include(
            "**/MainActivity.kt",
            "**/PursuitIronApplication.kt",
            "**/AppPrivateKotlinLocalAthlete.kt",
            "**/NativeKotlin*.kt",
            "**/NativeComponents.kt",
            "**/NativeRestTimer.kt",
            "**/NativeExerciseFavorites.kt",
            "**/NativeStrengthFavorites.kt",
            "**/PursuitDesignComponents.kt",
            "**/NativeDesignTokens.kt",
            "**/NativeAppearanceSheet.kt"
        )
    })
    outputDirectory.set(layout.buildDirectory.dir("generated/kotlin-beta-src"))
}
androidComponents.onVariants { variant ->
    variant.sources.kotlin?.addGeneratedSourceDirectory(
        stageKotlinBetaSources,StageKotlinBetaSources::outputDirectory)
}

dependencies {
    implementation(project(":domain-contract"))
    // One canonical, independently tested Kotlin coach and native persistence.
    // No JavaScript engine-runtime dependency in any app variant.
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

// Beta release has TWO independent gates:
//   1. No transitive JavaScript engine code in the app's runtime graph.
//   2. Independently checked, on-device complete-workout acceptance evidence.
// Source CI and debug compilation can run without issuing a release APK/AAB.
// The evidence file is intentionally NOT checked in during development.
val verifyEarlyBetaRuntime by tasks.registering {
    group = "verification"
    description = "Require Kotlin-only runtime and actual beta device acceptance"
    doLast {
        val components = configurations.getByName("releaseRuntimeClasspath")
            .incoming.resolutionResult.allComponents
        val forbiddenProjectPaths = setOf(
            ":engine-runtime", ":engine-bridge", ":legacy-engine", ":web-engine"
        )
        val forbiddenExternalModules = setOf(
            "org.mozilla:rhino", "org.graalvm.js:js", "org.graalvm.polyglot:js",
            "com.eclipsesource.j2v8:j2v8", "com.squareup.duktape:duktape-android"
        )
        val forbiddenComponents = components.mapNotNull { result ->
            val project = result.id as? ProjectComponentIdentifier
            val identity = result.id.displayName
            when {
                project?.projectPath in forbiddenProjectPaths -> identity
                forbiddenExternalModules.any { identity.startsWith(it + ":") ||
                    identity == it } -> identity
                else -> null
            }
        }
        check(forbiddenComponents.isEmpty()) {
            "Early beta release blocked: forbidden JavaScript runtime dependencies: " +
                forbiddenComponents.joinToString()
        }
        // Guard the staged Kotlin sources, not every historical source in
        // the repository. Native beta screens must not instantiate a web
        // execution surface or dynamically evaluate JavaScript.
        val stagedSources = fileTree(
            layout.buildDirectory.dir("generated/kotlin-beta-src")
        ).matching { include("**/*.kt") }.files
        val forbiddenSourcePatterns = listOf(
            Regex("""\\b(?:android\\.webkit\\.)?WebView\\s*\\("""),
            Regex("""\\b(?:evaluateJavascript|loadUrl)\\s*\\("""),
            Regex("""\\b(?:ScriptEngineManager|JavaScriptEngine)\\s*\\(""")
        )
        val violations = stagedSources.flatMap { source ->
            val body = source.readText()
            forbiddenSourcePatterns.filter { it.containsMatchIn(body) }
                .map { source.relativeTo(projectDir).path + " : " + it.pattern }
        }
        check(violations.isEmpty()) {
            "Early beta release blocked: web/JavaScript execution in staged native code: " +
                violations.joinToString()
        }
        check(rootProject.file("verification/BETA_DEVICE_ACCEPTANCE.md").isFile) {
            "Early beta release blocked: verified Android-device onboarding, " +
                "workout, cold resume and History acceptance evidence is missing."
        }
    }
}
// Bind verification only to actual packaging; source-only CI must still
// compile to establish the Kotlin app's readiness before device milestones.
tasks.matching {
    it.name in setOf(
        "packageRelease", "assembleRelease",
        "bundleRelease", "packageReleaseBundle"
    )
}.configureEach {
    dependsOn(verifyEarlyBetaRuntime)
}
