pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "droid-mcp"

include(
    ":droid-mcp-core",
    ":droid-mcp-device",
    ":droid-mcp-calendar",
    ":droid-mcp-contacts",
    ":droid-mcp-sms",
    ":droid-mcp-all",
    ":sample-app",
)
