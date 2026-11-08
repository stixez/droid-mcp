package io.droidmcp.app.feature.settings

import androidx.lifecycle.viewModelScope
import io.droidmcp.app.core.mvi.MviViewModel
import io.droidmcp.app.confirm.ApprovalMemory
import io.droidmcp.app.data.permissions.Capabilities
import io.droidmcp.app.data.server.LocalNetwork
import io.droidmcp.app.data.server.ServerControl
import io.droidmcp.app.data.settings.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import io.droidmcp.app.data.settings.SettingsStore
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsStore,
    private val server: ServerControl,
    versionName: String,
    private val approvals: ApprovalMemory? = null,
    network: Flow<LocalNetwork> = emptyFlow(),
    /** Asks GitHub; returns the newer version or null. */
    private val checkForUpdate: suspend () -> String? = { null },
) : MviViewModel<SettingsState, SettingsIntent, SettingsEffect>(SettingsState(versionName = versionName)) {

    /** Trusted Wi-Fi was turned on while the Wi-Fi name was hidden; trust it once it shows. */
    private var trustFirstNetwork = false

    init {
        viewModelScope.launch {
            settings.settings.collect { s ->
                setState {
                    copy(
                        // Keep what the user is typing; only follow the saved port when untouched.
                        portInput = if (portChanged) portInput else s.port.toString(),
                        port = s.port,
                        tls = s.tlsEnabled,
                        strictShellPolicy = s.strictShellPolicy,
                        themeMode = s.themeMode,
                        startOnBoot = s.startOnBoot,
                        checkForUpdates = s.checkForUpdates,
                        trustedWifiOnly = s.trustedWifiOnly,
                        trustedNetworks = s.trustedNetworks.sorted(),
                        alwaysAllowed = s.alwaysAllowedTools.sorted(),
                        computerCount = s.pairedComputers.size,
                    )
                }
            }
        }
        viewModelScope.launch { server.restartNeeded.collect { setState { copy(restartNeeded = it) } } }
        viewModelScope.launch { server.tlsFingerprint.collect { setState { copy(tlsFingerprint = it) } } }
        viewModelScope.launch {
            network.collect { n ->
                setState { copy(currentSsid = n.ssid, onWifi = n.onWifi) }
                // Turned on before the Wi-Fi name was readable: once it is, trust this network, the
                // same as turning it on here with the name known.
                val ssid = n.ssid
                if (trustFirstNetwork && ssid != null) {
                    trustFirstNetwork = false
                    update { s -> if (s.trustedWifiOnly && s.trustedNetworks.isEmpty()) s.copy(trustedNetworks = setOf(ssid)) else s }
                }
            }
        }
        approvals?.let { memory ->
            viewModelScope.launch { memory.temporary.collect { setState { copy(temporarilyAllowed = it.size) } } }
        }
    }

    override fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.EditPort -> {
                val digits = intent.text.filter(Char::isDigit).take(5)
                setState { copy(portInput = digits, portError = null) }
            }
            SettingsIntent.SavePort -> {
                val error = validatePort(currentState.portInput)
                if (error != null) {
                    setState { copy(portError = error) }
                    return
                }
                val port = currentState.portInput.toInt()
                viewModelScope.launch {
                    settings.update { it.copy(port = port) }
                    emit(SettingsEffect.Message("Port set to $port"))
                }
            }
            is SettingsIntent.SetTls -> viewModelScope.launch { settings.update { it.copy(tlsEnabled = intent.value) } }
            is SettingsIntent.SetStrictShellPolicy ->
                viewModelScope.launch { settings.update { it.copy(strictShellPolicy = intent.value) } }
            is SettingsIntent.SetThemeMode -> {
                setState { copy(themeMode = intent.mode) }
                viewModelScope.launch { settings.update { it.copy(themeMode = intent.mode) } }
            }
            SettingsIntent.AskRotateToken -> setState { copy(confirmRotate = true) }
            SettingsIntent.CancelRotateToken -> setState { copy(confirmRotate = false) }
            SettingsIntent.ConfirmRotateToken -> {
                setState { copy(confirmRotate = false) }
                viewModelScope.launch {
                    settings.rotateToken()
                    // The old token must stop working now, not at the next manual restart.
                    server.restartIfRunning()
                    emit(SettingsEffect.Message("New token created. Copy the new command to your clients."))
                }
            }
            SettingsIntent.Restart -> server.restart()
            SettingsIntent.OpenPermissions -> emit(SettingsEffect.NavigatePermissions)
            SettingsIntent.AllowWifiDetails -> emit(SettingsEffect.LaunchGrant(Capabilities.get(Capabilities.WIFI)))
            SettingsIntent.OpenSource -> emit(SettingsEffect.OpenUrl(SOURCE_URL))
            SettingsIntent.ShowLicenses -> setState { copy(showLicenses = true) }
            SettingsIntent.HideLicenses -> setState { copy(showLicenses = false) }
            is SettingsIntent.SetStartOnBoot -> update { it.copy(startOnBoot = intent.value) }
            is SettingsIntent.SetCheckForUpdates -> update { it.copy(checkForUpdates = intent.value) }
            SettingsIntent.CheckForUpdatesNow -> {
                if (currentState.checkingForUpdates) return
                setState { copy(checkingForUpdates = true) }
                viewModelScope.launch {
                    val newer = checkForUpdate()
                    setState { copy(checkingForUpdates = false) }
                    emit(SettingsEffect.Message(newer?.let { "Version $it is available. See Home." } ?: "You have the latest version"))
                }
            }
            is SettingsIntent.SetTrustedWifiOnly -> {
                val ssid = currentState.currentSsid
                update { s ->
                    // Turning it on while on a known Wi-Fi trusts that network, so it doesn't pause at once.
                    val networks = if (intent.value && ssid != null && s.trustedNetworks.isEmpty()) setOf(ssid) else s.trustedNetworks
                    s.copy(trustedWifiOnly = intent.value, trustedNetworks = networks)
                }
                // The feature needs the Wi-Fi name, so ask for access right away rather than
                // sending the user off to find it. If it's granted already, Location is off; the
                // hint under the switch says so.
                trustFirstNetwork = intent.value && ssid == null
                if (intent.value && ssid == null) {
                    emit(SettingsEffect.LaunchGrant(Capabilities.get(Capabilities.WIFI)))
                }
            }
            SettingsIntent.TrustCurrentNetwork -> {
                val ssid = currentState.currentSsid ?: return
                update { it.copy(trustedNetworks = it.trustedNetworks + ssid) }
            }
            is SettingsIntent.ForgetNetwork -> update { it.copy(trustedNetworks = it.trustedNetworks - intent.ssid) }
            is SettingsIntent.ForgetAlwaysAllowed -> approvals?.forgetAlways(intent.tool)
            SettingsIntent.ForgetTemporaryApprovals -> approvals?.forgetTemporary()
            SettingsIntent.OpenComputers -> emit(SettingsEffect.NavigateComputers)
        }
    }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settings.update(transform) }
    }

    companion object {
        const val SOURCE_URL = "https://github.com/stixez/droid-mcp"
    }
}
