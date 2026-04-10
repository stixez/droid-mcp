plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.droidmcp.all"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    api(project(":droid-mcp-core"))
    api(project(":droid-mcp-device"))
    api(project(":droid-mcp-calendar"))
    api(project(":droid-mcp-contacts"))
    api(project(":droid-mcp-sms"))
    api(project(":droid-mcp-files"))
    api(project(":droid-mcp-notifications"))
    api(project(":droid-mcp-calllog"))
    api(project(":droid-mcp-media"))
    api(project(":droid-mcp-location"))
    api(project(":droid-mcp-health"))
}
