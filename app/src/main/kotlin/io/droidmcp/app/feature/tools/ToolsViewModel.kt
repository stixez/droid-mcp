package io.droidmcp.app.feature.tools

import androidx.lifecycle.viewModelScope
import io.droidmcp.app.core.mvi.MviViewModel
import io.droidmcp.app.data.permissions.Capabilities
import io.droidmcp.app.data.permissions.PermissionSource
import io.droidmcp.app.data.server.ServerControl
import io.droidmcp.app.data.settings.AppSettings
import io.droidmcp.app.data.settings.SettingsStore
import io.droidmcp.app.data.tools.ResolvedGroup
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ToolsViewModel(
    private val server: ServerControl,
    private val settings: SettingsStore,
    private val permissions: PermissionSource,
    private val resolve: (AppSettings, Set<String>) -> List<ResolvedGroup>,
    resolveDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : MviViewModel<ToolsState, ToolsIntent, ToolsEffect>(ToolsState()) {

    init {
        // Building the catalog is the expensive part, and only these inputs change it; theme,
        // port or a single switch flip shouldn't rebuild ~160 tools.
        val resolved = combine(
            settings.settings.map { it.allowFileWrites to it.strictShellPolicy }.distinctUntilChanged(),
            permissions.granted,
        ) { (fileWrites, strictShell), granted ->
            Triple(fileWrites, strictShell, granted)
        }
            .mapLatest { (fileWrites, strictShell, granted) ->
                resolve(AppSettings(allowFileWrites = fileWrites, strictShellPolicy = strictShell), granted)
            }
            .flowOn(resolveDispatcher)
        viewModelScope.launch {
            combine(resolved, settings.settings) { groups, s -> buildGroups(groups, s.disabledTools) to s }
                .collect { (groups, s) ->
                    setState {
                        copy(
                            loading = false,
                            groups = groups,
                            readOnly = s.readOnly,
                            confirmRiskyCalls = s.confirmRiskyCalls,
                            allowFileWrites = s.allowFileWrites,
                        )
                    }
                }
        }
        viewModelScope.launch {
            server.restartNeeded.collect { setState { copy(restartNeeded = it) } }
        }
    }

    override fun onIntent(intent: ToolsIntent) {
        when (intent) {
            is ToolsIntent.Search -> setState { copy(query = intent.query) }
            is ToolsIntent.ToggleExpanded -> setState {
                copy(expanded = if (intent.groupId in expanded) expanded - intent.groupId else expanded + intent.groupId)
            }
            is ToolsIntent.SetGroupEnabled -> {
                val group = currentState.groups.firstOrNull { it.id == intent.groupId } ?: return
                applyEnabled(group.tools.map { it.name }.toSet(), intent.enabled)
            }
            is ToolsIntent.SetToolEnabled -> applyEnabled(setOf(intent.name), intent.enabled)
            is ToolsIntent.SetReadOnly -> update { it.copy(readOnly = intent.value) }
            is ToolsIntent.SetConfirmRiskyCalls -> update { it.copy(confirmRiskyCalls = intent.value) }
            is ToolsIntent.SetAllowFileWrites -> update { it.copy(allowFileWrites = intent.value) }
            is ToolsIntent.Grant -> emit(ToolsEffect.LaunchGrant(Capabilities.get(intent.capabilityId)))
            ToolsIntent.RefreshPermissions -> permissions.refresh()
            ToolsIntent.Restart -> server.restart()
        }
    }

    /** Optimistic: flip the switches now, persist and apply to the live server in the background. */
    private fun applyEnabled(names: Set<String>, enabled: Boolean) {
        setState { copy(groups = groups.withEnabled(names, enabled)) }
        server.setToolsEnabled(names, enabled)
    }

    private fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch { settings.update(transform) }
    }
}

/** Pure: the same groups with [names] switched to [enabled]. */
internal fun List<ToolGroupUi>.withEnabled(names: Set<String>, enabled: Boolean): List<ToolGroupUi> = map { group ->
    if (group.tools.none { it.name in names }) group
    else group.copy(tools = group.tools.map { if (it.name in names) it.copy(enabled = enabled) else it })
}
