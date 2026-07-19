plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.dokka)
}

subprojects {
    plugins.withId("com.android.library") {
        apply(plugin = "maven-publish")
        apply(plugin = "org.jetbrains.dokka")

        // Android documentation plugin enriches Dokka output for Android symbols.
        dependencies {
            add("dokkaPlugin", rootProject.libs.dokka.android.plugin)
        }

        extensions.configure<com.android.build.gradle.LibraryExtension> {
            publishing {
                singleVariant("release") {
                    withSourcesJar()
                }
            }
        }

        // afterEvaluate so the release component is registered by AGP before we reference it
        afterEvaluate {
            extensions.configure<PublishingExtension> {
                publications {
                    create<MavenPublication>("release") {
                        from(components["release"])
                        groupId = "io.droidmcp"
                        artifactId = project.name
                        version = "0.10.1"
                    }
                }
            }
        }
    }
}

// ---- Dokka: aggregate API docs for every published library module ----
// Excludes :sample-app (the demo application) and :droid-mcp-all (a sourceless
// convenience aggregator). Output: build/dokka/html. Generate with:
//   ./gradlew :dokkaGenerate
dokka {
    moduleName.set("droid-mcp")
}

dependencies {
    subprojects
        .filter { it.name != "sample-app" && it.name != "droid-mcp-all" }
        .forEach { dokka(it) }
}
