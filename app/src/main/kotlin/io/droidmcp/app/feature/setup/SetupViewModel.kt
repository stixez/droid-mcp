package io.droidmcp.app.feature.setup

import androidx.lifecycle.viewModelScope
import io.droidmcp.app.core.mvi.MviViewModel
import io.droidmcp.app.data.permissions.Capabilities
import io.droidmcp.app.data.permissions.PermissionSource
import io.droidmcp.app.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SetupViewModel(
    firstRun: Boolean,
    private val permissions: PermissionSource,
    private val settings: SettingsStore,
    /** Tools unlocked per capability id. Builds the catalog, so it runs on [background]. */
    private val countByCapability: () -> Map<String, Int> = { emptyMap() },
    /** Tools offered for a granted set, including the ones that need no permission. */
    private val countReady: (Set<String>) -> Int = { 0 },
    private val background: CoroutineDispatcher = Dispatchers.Default,
) : MviViewModel<SetupState, SetupIntent, SetupEffect>(SetupState(firstRun = firstRun)) {

    init {
        viewModelScope.launch {
            permissions.granted.collect { granted ->
                // Recounted on every change: some modules add write tools only once write access is granted.
                val (toolCounts, ready) = withContext(background) { countByCapability() to countReady(granted) }
                setState {
                    copy(sections = buildSections(Capabilities.all, granted, toolCounts), readyTools = ready)
                }
            }
        }
    }

    override fun onIntent(intent: SetupIntent) {
        when (intent) {
            is SetupIntent.Grant -> emit(SetupEffect.LaunchGrant(Capabilities.get(intent.id)))
            SetupIntent.Refresh -> permissions.refresh()
            SetupIntent.OpenAppInfo -> emit(SetupEffect.LaunchAppInfo)
            SetupIntent.Finish -> viewModelScope.launch {
                settings.update { it.copy(setupCompleted = true) }
                emit(SetupEffect.Finished)
            }
        }
    }
}
