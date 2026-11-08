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
        versionName = providers.gradleProperty("VERSION_NAME").get()
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
                // Netty ships per-jar license/notice copies.
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.md",
                "META-INF/license/**",
            )
        }
    }
}

dependencies {
    // Pick the modules you need; droid-mcp-all has every module except the opt-in ones.
    implementation(project(":droid-mcp-core"))
    implementation(project(":droid-mcp-device"))
    implementation(project(":droid-mcp-calendar"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
