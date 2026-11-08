// app/build.gradle.kts — the installable droid-mcp host app (MCP server for your phone).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Release signing comes from env vars (CI) or Gradle properties (local). When none are
// set the release build is left unsigned, so assembleRelease still works anywhere.
fun signingValue(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty("droidmcp.$name").orNull?.takeIf { it.isNotBlank() }

val keystorePath = signingValue("KEYSTORE_PATH")

android {
    namespace = "io.droidmcp.app"
    compileSdk = libs.versions.sampleCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.droidmcp.app"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionName = providers.gradleProperty("VERSION_NAME").get()
        // 0.13.0 -> 1300, 1.2.3 -> 10203: monotonic as long as minor/patch stay < 100.
        versionCode = versionName!!.split(".").map { it.takeWhile(Char::isDigit).toInt() }
            .let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = signingValue("KEYSTORE_PASSWORD")
                keyAlias = signingValue("KEY_ALIAS")
                keyPassword = signingValue("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // Release: one APK per CPU type, since ML Kit's native libraries make a universal APK over
    // 100 MB. Debug builds stay a single universal APK for easy installs.
    splits {
        abi {
            isEnable = gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) }
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    packaging {
        // Compress native libraries: a ~30% smaller download, at the cost of extracting them on install.
        jniLibs.useLegacyPackaging = true
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties",
                "META-INF/*.kotlin_module",
                "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE.md",
                "META-INF/license/**",
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":droid-mcp-all"))
    implementation(project(":droid-mcp-shizuku"))
    implementation(project(":droid-mcp-root"))
    // Re-opens the root shell after a process restart (ServerController.createServer).
    implementation(libs.libsu.core)
    implementation(project(":droid-mcp-audit"))
    implementation(project(":droid-mcp-tls"))
    implementation(project(":droid-mcp-server-service"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
}
