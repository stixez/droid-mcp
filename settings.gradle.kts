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
    ":droid-mcp-files",
    ":droid-mcp-notifications",
    ":droid-mcp-calllog",
    ":droid-mcp-media",
    ":droid-mcp-location",
    ":droid-mcp-health",
    ":droid-mcp-clipboard",
    ":droid-mcp-apps",
    ":droid-mcp-alarms",
    ":droid-mcp-settings",
    ":droid-mcp-bluetooth",
    ":droid-mcp-wifi",
    ":droid-mcp-downloads",
    ":droid-mcp-screen",
    ":droid-mcp-tts",
    ":droid-mcp-web",
    ":droid-mcp-all",
    ":sample-app",
)
