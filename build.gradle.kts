plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

subprojects {
    plugins.withId("com.android.library") {
        apply(plugin = "maven-publish")

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
                        version = "0.2.0"
                    }
                }
            }
        }
    }
}
