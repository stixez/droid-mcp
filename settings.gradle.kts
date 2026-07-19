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
        maven { url = uri("https://jitpack.io") } // libsu (com.github.topjohnwu.libsu:core)
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
    ":droid-mcp-telephony",
    ":droid-mcp-vibration",
    ":droid-mcp-flashlight",
    ":droid-mcp-biometric",
    ":droid-mcp-network",
    ":droid-mcp-sensors",
    ":droid-mcp-qr",
    ":droid-mcp-camera",
    ":droid-mcp-audio",
    ":droid-mcp-nfc",
    ":droid-mcp-intent",
    ":droid-mcp-playback",
    ":droid-mcp-screenshot",
    ":droid-mcp-dnd",
    ":droid-mcp-keyguard",
    ":droid-mcp-wallpaper",
    ":droid-mcp-ringtone",
    ":droid-mcp-usb",
    ":droid-mcp-print",
    ":droid-mcp-mlkit",
    ":droid-mcp-notification-listener",
    ":droid-mcp-notifications-reply",
    ":droid-mcp-notification-watch",
    ":droid-mcp-accessibility",
    ":droid-mcp-ime",
    ":droid-mcp-overlay",
    ":droid-mcp-shell-core",
    ":droid-mcp-shizuku",
    ":droid-mcp-root",
    ":droid-mcp-audit",
    ":droid-mcp-tls",
    ":droid-mcp-server-service",
    ":droid-mcp-all",
    ":sample-app",
)
