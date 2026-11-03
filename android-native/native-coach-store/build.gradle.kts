plugins { id("com.android.library") }
android {
    namespace = "io.github.extracteddeath.pursuitiron.nativecoachstore"
    compileSdk = 37
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    implementation(project(":kotlin-engine"))
    testImplementation("junit:junit:4.13.2")
}
