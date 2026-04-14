plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "io.droidmcp.all"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    api(project(":droid-mcp-core"))
    api(project(":droid-mcp-device"))
    api(project(":droid-mcp-calendar"))
    api(project(":droid-mcp-contacts"))
    api(project(":droid-mcp-sms"))
    api(project(":droid-mcp-files"))
    api(project(":droid-mcp-notifications"))
    api(project(":droid-mcp-calllog"))
    api(project(":droid-mcp-media"))
    api(project(":droid-mcp-location"))
    api(project(":droid-mcp-health"))
    api(project(":droid-mcp-clipboard"))
    api(project(":droid-mcp-apps"))
    api(project(":droid-mcp-alarms"))
    api(project(":droid-mcp-settings"))
    api(project(":droid-mcp-bluetooth"))
    api(project(":droid-mcp-wifi"))
    api(project(":droid-mcp-downloads"))
    api(project(":droid-mcp-screen"))
    api(project(":droid-mcp-tts"))
    api(project(":droid-mcp-web"))
    api(project(":droid-mcp-flashlight"))
    api(project(":droid-mcp-biometric"))
    api(project(":droid-mcp-network"))
    api(project(":droid-mcp-sensors"))
    api(project(":droid-mcp-qr"))
    api(project(":droid-mcp-camera"))
    api(project(":droid-mcp-audio"))
    api(project(":droid-mcp-telephony"))
    api(project(":droid-mcp-vibration"))
    api(project(":droid-mcp-nfc"))
    api(project(":droid-mcp-intent"))
    api(project(":droid-mcp-playback"))
    api(project(":droid-mcp-screenshot"))
    api(project(":droid-mcp-dnd"))
    api(project(":droid-mcp-keyguard"))
    api(project(":droid-mcp-wallpaper"))
    api(project(":droid-mcp-ringtone"))
    api(project(":droid-mcp-usb"))
    api(project(":droid-mcp-print"))
}
