plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.droidmcp.core"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.core.ktx)

    // HTTP transport (optional at runtime, needed at compile)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty) {
        // Netty 4.2 (via Ktor 3.6) ships desktop native transports and HTTP/3 QUIC binaries
        // (linux/osx .so/.jnilib) that can't load on Android — they only bloat the APK and
        // collide in resource merging. The pure-Java NIO transport is what runs on-device.
        exclude(group = "io.netty", module = "netty-codec-native-quic")
        exclude(group = "io.netty", module = "netty-transport-native-epoll")
        exclude(group = "io.netty", module = "netty-transport-native-kqueue")
    }
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.json)

    // Testing
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.truth)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.server.test.host) {
        // test-host drags in its own JUnit platform/kotlin-test-junit, which clashes with junit5 above.
        exclude(group = "org.junit.jupiter")
        exclude(group = "org.junit.platform")
        exclude(group = "junit")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-test-junit")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-test-junit5")
    }
}
