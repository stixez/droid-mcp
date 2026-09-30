import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Build-time helper: dumps a library's public JVM API (BCV format) so `apiCheck` can
// catch accidental breaking changes. Not published, not shipped.
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

// Target JVM 17 bytecode on whatever JDK runs Gradle (17+), without requiring a JDK 17 toolchain.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

application { mainClass.set("io.droidmcp.tools.abi.AbiDumpKt") }

dependencies {
    implementation(libs.bcv)
    implementation(libs.asm)
    implementation(libs.asm.tree)
    implementation(libs.kotlin.metadata.jvm)
}
