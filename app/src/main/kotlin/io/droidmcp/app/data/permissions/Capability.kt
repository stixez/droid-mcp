package io.droidmcp.app.data.permissions

import android.content.Context
import android.content.Intent

/** Where a capability is listed on the Setup screen. */
enum class CapabilitySection(val title: String, val subtitle: String) {
    Essentials("Essentials", "Needed for the app to work well"),
    PersonalData("Your data", "Grant only what you want clients to see"),
    PhoneControl("Phone control", "Granted in system Settings"),
    Advanced("Advanced", "For power users"),
}

/** How the user grants a capability. Resolved to a concrete action by the UI. */
sealed interface GrantMethod {
    /** Normal runtime permission dialog. */
    data class Runtime(val permissions: List<String>) : GrantMethod

    /** A system Settings page the user toggles something on. */
    data class SystemSettings(val intent: (Context) -> Intent) : GrantMethod

    /** MediaProjection consent dialog; the token lasts until the app process ends. */
    data object ScreenCapture : GrantMethod

    /** Shizuku permission prompt, or the Shizuku app when it isn't running yet. */
    data object Shizuku : GrantMethod

    /** Superuser prompt from Magisk / KernelSU. */
    data object Root : GrantMethod
}

/**
 * One thing the user can allow, phrased for people rather than for Android: "Calendar",
 * "Screen control", "Notification access". Tool groups point at capabilities by [id].
 */
data class Capability(
    val id: String,
    val title: String,
    val description: String,
    val section: CapabilitySection,
    val grant: GrantMethod,
    val isGranted: (Context) -> Boolean,
    /** Lost when the app restarts (screen capture); the UI says so. */
    val sessionOnly: Boolean = false,
)
