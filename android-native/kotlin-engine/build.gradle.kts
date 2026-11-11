plugins { id("com.android.library") }
android {
    namespace = "io.github.extracteddeath.pursuitiron.rebuild"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
}

// Executable measurement, separate from unit tests and from Android issuance.
tasks.register<JavaExec>("runPairedProgressionProbe") {
    val tests = tasks.named<Test>("testDebugUnitTest")
    dependsOn("compileDebugUnitTestKotlin", "processDebugUnitTestJavaRes")
    classpath = files(tests.map { it.classpath })
    mainClass.set("io.github.extracteddeath.pursuitiron.rebuild.PairedProgressionProbe")
    args(providers.gradleProperty("comparisonInput").get(),
        providers.gradleProperty("comparisonOutput").get())
}

tasks.register<JavaExec>("runPairedFunctionalProbe") {
    val tests = tasks.named<Test>("testDebugUnitTest")
    dependsOn("compileDebugUnitTestKotlin", "processDebugUnitTestJavaRes")
    classpath = files(tests.map { it.classpath })
    mainClass.set("io.github.extracteddeath.pursuitiron.rebuild.PairedFunctionalProbe")
    args(providers.gradleProperty("comparisonInput").get(),
        providers.gradleProperty("comparisonOutput").get())
}

tasks.register<JavaExec>("runStarterProgramReleaseProbe") {
    val tests=tasks.named<Test>("testDebugUnitTest")
    dependsOn("compileDebugUnitTestKotlin", "processDebugUnitTestJavaRes")
    classpath=files(tests.map{it.classpath})
    mainClass.set("io.github.extracteddeath.pursuitiron.rebuild.StarterProgramReleaseProbe")
    args(layout.buildDirectory.file("reports/starter-program-release-audit.json").get().asFile.absolutePath)
}


/** Keep failed coaching/program assertions actionable in headless CI logs. */
tasks.withType<Test>().configureEach {
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}
