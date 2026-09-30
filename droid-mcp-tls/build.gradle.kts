plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.droidmcp.tls"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig { minSdk = libs.versions.minSdk.get().toInt() }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests.all { it.useJUnitPlatform() }
        // SelfSignedCert logs via android.util.Log on the regenerate paths.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":droid-mcp-core"))
    implementation(libs.bouncycastle.bcpkix)
    implementation(libs.bouncycastle.bcprov)

    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.truth)
}
