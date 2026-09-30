plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.droidmcp.camera"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":droid-mcp-core"))
    implementation(libs.kotlinx.coroutines.android)
}
