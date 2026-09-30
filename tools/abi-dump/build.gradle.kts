// Build-time helper: dumps a library's public JVM API (BCV format) so `apiCheck` can
// catch accidental breaking changes. Not published, not shipped.
plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin { jvmToolchain(17) }

application { mainClass.set("io.droidmcp.tools.abi.AbiDumpKt") }

dependencies {
    implementation(libs.bcv)
    implementation(libs.asm)
    implementation(libs.asm.tree)
    implementation(libs.kotlin.metadata.jvm)
}
