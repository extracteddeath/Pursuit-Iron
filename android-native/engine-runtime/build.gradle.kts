plugins {
    id("com.android.library")
}
android {
    namespace = "io.github.extracteddeath.pursuitiron.engine"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(project(":domain-contract"))
    implementation("androidx.javascriptengine:javascriptengine:1.1.1")
    testImplementation("junit:junit:4.13.2")
}
