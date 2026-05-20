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
    api(project(":droid-mcp-mlkit"))
    api(project(":droid-mcp-notification-listener"))
    api(project(":droid-mcp-notifications-reply"))
    api(project(":droid-mcp-notification-watch"))
    api(project(":droid-mcp-accessibility"))
    api(project(":droid-mcp-ime"))
    api(project(":droid-mcp-overlay"))
    api(project(":droid-mcp-shell-core"))
    // NOTE: :droid-mcp-shizuku and :droid-mcp-root are intentionally NOT
    // included in :droid-mcp-all. They pull in third-party deps with native
    // code (dev.rikka.shizuku, libsu) that consumers who don't want
    // shell-UID / root admin tools shouldn't pay for. Hosts opt in
    // explicitly:
    //   implementation(":droid-mcp-shizuku")  // Tier 4
    //   implementation(":droid-mcp-root")     // Tier 5
}
