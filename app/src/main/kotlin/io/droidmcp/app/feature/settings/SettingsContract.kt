package io.droidmcp.app.feature.settings

import io.droidmcp.app.data.permissions.Capability
import io.droidmcp.app.data.settings.ThemeMode

data class SettingsState(
    val port: Int = 8080,
    val portInput: String = "8080",
    val portError: String? = null,
    val tls: Boolean = false,
    val tlsFingerprint: String? = null,
    val strictShellPolicy: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.System,
    val restartNeeded: Boolean = false,
    val versionName: String = "",
    val confirmRotate: Boolean = false,
    val showLicenses: Boolean = false,
    val startOnBoot: Boolean = false,
    val checkForUpdates: Boolean = true,
    val checkingForUpdates: Boolean = false,
    val trustedWifiOnly: Boolean = false,
    val trustedNetworks: List<String> = emptyList(),
    /** Name of the Wi-Fi the phone is on, or null when unknown (needs Wi-Fi details access and Location on). */
    val currentSsid: String? = null,
    val onWifi: Boolean = false,
    val alwaysAllowed: List<String> = emptyList(),
    /** Tools allowed "for 1 hour" right now. */
    val temporarilyAllowed: Int = 0,
    val computerCount: Int = 0,
) {
    val currentNetworkTrusted: Boolean get() = currentSsid != null && currentSsid in trustedNetworks

    val portChanged: Boolean get() = portInput != port.toString()
}

sealed interface SettingsIntent {
    data class EditPort(val text: String) : SettingsIntent
    data object SavePort : SettingsIntent
    data class SetTls(val value: Boolean) : SettingsIntent
    data class SetStrictShellPolicy(val value: Boolean) : SettingsIntent
    data class SetThemeMode(val mode: ThemeMode) : SettingsIntent
    data object AskRotateToken : SettingsIntent
    data object CancelRotateToken : SettingsIntent
    data object ConfirmRotateToken : SettingsIntent
    data object Restart : SettingsIntent
    data object OpenPermissions : SettingsIntent
    data object AllowWifiDetails : SettingsIntent
    data object OpenSource : SettingsIntent
    data object ShowLicenses : SettingsIntent
    data object HideLicenses : SettingsIntent
    data class SetStartOnBoot(val value: Boolean) : SettingsIntent
    data class SetCheckForUpdates(val value: Boolean) : SettingsIntent
    data object CheckForUpdatesNow : SettingsIntent
    data class SetTrustedWifiOnly(val value: Boolean) : SettingsIntent
    data object TrustCurrentNetwork : SettingsIntent
    data class ForgetNetwork(val ssid: String) : SettingsIntent
    data class ForgetAlwaysAllowed(val tool: String) : SettingsIntent
    data object ForgetTemporaryApprovals : SettingsIntent
    data object OpenComputers : SettingsIntent
}

sealed interface SettingsEffect {
    data object NavigatePermissions : SettingsEffect
    data object NavigateComputers : SettingsEffect
    data class LaunchGrant(val capability: Capability) : SettingsEffect
    data class OpenUrl(val url: String) : SettingsEffect
    data class Message(val text: String) : SettingsEffect
}

/** Pure: null when [text] is a usable port, else what's wrong with it. */
fun validatePort(text: String): String? {
    val port = text.toIntOrNull() ?: return "Enter a number"
    return when {
        port < 1024 -> "Use 1024 or higher; lower ports need root"
        port > 65535 -> "The highest port is 65535"
        port == 8443 -> "8443 is used for HTTPS"
        else -> null
    }
}
