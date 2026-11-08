package io.droidmcp.app.feature.home

import androidx.lifecycle.viewModelScope
import io.droidmcp.app.core.mvi.MviViewModel
import io.droidmcp.app.core.time.startOfDay
import io.droidmcp.app.data.permissions.Capabilities
import io.droidmcp.app.data.permissions.PermissionSource
import io.droidmcp.app.data.server.ConnectionInfo
import io.droidmcp.app.data.server.LocalNetwork
import io.droidmcp.app.data.server.ServerControl
import io.droidmcp.app.data.server.ServerStatus
import io.droidmcp.app.data.settings.AppSettings
import io.droidmcp.app.data.settings.SettingsStore
import io.droidmcp.app.data.update.UpdateChecker
import io.droidmcp.core.ToolCallAudit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class HomeViewModel(
    private val server: ServerControl,
    private val settings: SettingsStore,
    private val permissions: PermissionSource,
    network: Flow<LocalNetwork>,
    recentCalls: Flow<List<ToolCallAudit>>,
    private val essentials: List<String>,
    /** The most rows [recentCalls] emits; reaching it means "at least this many". */
    private val recentCallsLimit: Int = Int.MAX_VALUE,
    private val now: () -> Long = System::currentTimeMillis,
    /** This build's version, to tell whether GitHub's latest is newer. */
    private val currentVersion: String = "0",
    private val releasesUrl: String = "",
) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {

    init {
        viewModelScope.launch {
            // Make sure a token exists before the first command is shown.
            val token = settings.ensureToken()
            setState { copy(token = token) }
        }
        viewModelScope.launch {
            combine(server.status, server.restartNeeded) { status, restart -> status to restart }
                .collect { (status, restart) -> setState { copy(status = status, restartNeeded = restart) } }
        }
        viewModelScope.launch {
            server.pausedWhileClosed.collect { setState { copy(pausedWhileClosed = it) } }
        }
        viewModelScope.launch {
            settings.settings.collect { s ->
                val update = s.latestVersion?.takeIf {
                    UpdateChecker.isNewer(it, currentVersion) && it != s.dismissedUpdate
                }
                setState { withSettings(s).copy(updateAvailable = update) }
            }
        }
        viewModelScope.launch {
            network.collect { n -> setState { withNetwork(n) } }
        }
        viewModelScope.launch {
            permissions.granted.collect { granted ->
                setState { copy(missingEssentials = essentials.filterNot { it in granted }, examples = examplePrompts(granted)) }
            }
        }
        viewModelScope.launch {
            recentCalls.collect { calls ->
                val startOfDay = startOfDay(now())
                val today = calls.count { it.timestamp >= startOfDay }
                setState {
                    copy(lastCall = calls.firstOrNull(), callsToday = today, callsTodayCapped = today >= recentCallsLimit)
                }
            }
        }
    }

    override fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.ToggleServer -> when (currentState.status) {
                is ServerStatus.Running, is ServerStatus.Paused, ServerStatus.Starting -> server.stop()
                ServerStatus.Stopped, is ServerStatus.Failed -> server.start()
            }
            HomeIntent.Restart -> server.restart()
            is HomeIntent.SelectClient -> setState { copy(client = intent.client) }
            is HomeIntent.SelectRoute -> setState { copy(route = intent.route, autoUsb = false) }
            HomeIntent.ToggleTokenVisible -> setState { copy(tokenVisible = !tokenVisible) }
            HomeIntent.CopyConfig -> currentState.configText?.let {
                val what = if (currentState.client == ClientKind.ClaudeCode) "Command" else "Config"
                emit(HomeEffect.Copy(it, what, sensitive = true))
            }
            HomeIntent.CopyUrl -> currentState.connection?.let { emit(HomeEffect.Copy(it.url, "Address", sensitive = false)) }
            HomeIntent.CopyToken -> currentState.connection?.token?.let { emit(HomeEffect.Copy(it, "Token", sensitive = true)) }
            HomeIntent.CopyAdbForward ->
                emit(HomeEffect.Copy(ConnectionInfo.adbForwardCommand(currentState.activePort), "adb command", sensitive = false))
            HomeIntent.OpenPermissions -> emit(HomeEffect.NavigatePermissions)
            HomeIntent.OpenActivity -> emit(HomeEffect.NavigateActivity)
            HomeIntent.OpenUpdate -> emit(HomeEffect.OpenUrl(releasesUrl))
            HomeIntent.DismissUpdate -> {
                val version = currentState.updateAvailable ?: return
                setState { copy(updateAvailable = null) }
                viewModelScope.launch { settings.update { it.copy(dismissedUpdate = version) } }
            }
            is HomeIntent.CopyExample -> emit(HomeEffect.Copy(intent.prompt, "Prompt", sensitive = false))
            HomeIntent.OpenComputers -> emit(HomeEffect.NavigateComputers)
            HomeIntent.AllowBackgroundLocation -> {
                server.dismissPausedWhileClosed()
                emit(HomeEffect.LaunchGrant(Capabilities.BACKGROUND_LOCATION))
            }
            HomeIntent.DismissPausedWhileClosed -> server.dismissPausedWhileClosed()
            HomeIntent.DismissEssentialsHint -> {
                setState { copy(essentialsHintDismissed = true) }
                viewModelScope.launch { settings.update { it.copy(essentialsHintDismissed = true) } }
            }
        }
    }

}

/** Pure: fold the saved settings into the screen state. */
internal fun HomeState.withSettings(s: AppSettings): HomeState = copy(
    essentialsHintDismissed = s.essentialsHintDismissed,
    token = s.token ?: token,
    port = s.activePort,
    tls = s.tlsEnabled,
)
