plugins {
    id("com.android.library")
}
android {
    namespace = "io.github.extracteddeath.pursuitiron.engine"
    compileSdk = 37
    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    sourceSets.getByName("test").resources.srcDir("../domain-contract/src/test/resources")
}
dependencies {
    implementation(project(":domain-contract"))
    implementation("androidx.javascriptengine:javascriptengine:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
