// sample-app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.droidmcp.sample"
    compileSdk = libs.versions.sampleCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.droidmcp.sample"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.10.1"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/*.kotlin_module",
                // BouncyCastle (droid-mcp-tls) ships this OSGi manifest in all
                // three of its jars — it's unused on Android.
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                // BouncyCastle 1.8x and Netty 4.2 ship per-jar license/notice copies.
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.md",
                "META-INF/license/**",
            )
        }
    }
}

dependencies {
    implementation(project(":droid-mcp-all"))
    // Tier 4 — opt-in (pulls dev.rikka.shizuku). The sample app exercises Shizuku tools, so we add it here.
    implementation(project(":droid-mcp-shizuku"))
    // Tier 5 — opt-in (pulls libsu). Same shell tool surface as Shizuku, just routed via su.
    implementation(project(":droid-mcp-root"))
    // 0.10.0 hardening — opt-in modules (Room, BouncyCastle, foreground service).
    implementation(project(":droid-mcp-audit"))
    implementation(project(":droid-mcp-tls"))
    implementation(project(":droid-mcp-server-service"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)
}
