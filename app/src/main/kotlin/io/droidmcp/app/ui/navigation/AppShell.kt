package io.droidmcp.app.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import io.droidmcp.app.AppGraph
import io.droidmcp.app.BuildConfig
import io.droidmcp.app.data.update.UpdateChecker
import io.droidmcp.app.feature.activity.ActivityLog
import io.droidmcp.app.feature.computers.ComputersRoute
import io.droidmcp.app.feature.computers.ComputersViewModel
import io.droidmcp.app.feature.activity.ActivityRoute
import io.droidmcp.app.feature.activity.ActivityViewModel
import io.droidmcp.app.feature.home.HomeRoute
import io.droidmcp.app.feature.home.HomeViewModel
import io.droidmcp.app.feature.settings.SettingsRoute
import io.droidmcp.app.feature.settings.SettingsViewModel
import io.droidmcp.app.feature.setup.EssentialCapabilityIds
import io.droidmcp.app.feature.setup.SetupRoute
import io.droidmcp.app.feature.setup.SetupViewModel
import io.droidmcp.app.feature.tools.ToolsRoute
import io.droidmcp.app.feature.tools.ToolsViewModel
import io.droidmcp.audit.RoomAuditSink
import io.droidmcp.core.ToolCallAudit
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data object HomeKey : NavKey

@Serializable
data object ToolsKey : NavKey

@Serializable
data object ActivityKey : NavKey

@Serializable
data object SettingsKey : NavKey

@Serializable
data class SetupKey(val firstRun: Boolean) : NavKey

@Serializable
data object ComputersKey : NavKey

/** Rows Home reads to count today's calls; the card shows "500+" beyond it. */
private const val RECENT_CALLS = 500

private data class Tab(val key: NavKey, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    Tab(HomeKey, "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Tab(ToolsKey, "Tools", Icons.Outlined.Build, Icons.Filled.Build),
    Tab(ActivityKey, "Activity", Icons.Outlined.History, Icons.Filled.History),
)

/** Bottom navigation + the screen stack. Home is always the root, so Back from a tab goes Home. */
@Composable
fun AppShell(graph: AppGraph, startOnSetup: Boolean) {
    val backStack = rememberNavBackStack(if (startOnSetup) SetupKey(firstRun = true) else HomeKey)
    val top = backStack.lastOrNull()
    val showTabs = tabs.any { it.key == top }
    // Landscape phones and tablets: a side rail keeps the short screen height for content.
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val wide = windowWidth >= WIDE_LAYOUT
    val showBottomBar = showTabs && !wide

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            AnimatedVisibility(showBottomBar, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                NavigationBar {
                    tabs.forEach { tab ->
                        val selected = tab.key == top
                        NavigationBarItem(
                            selected = selected,
                            onClick = { backStack.selectTab(tab.key) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize()) {
            AnimatedVisibility(wide && showTabs, enter = fadeIn() + expandHorizontally(), exit = fadeOut() + shrinkHorizontally()) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Spacer(Modifier.weight(1f))
                    tabs.forEach { tab ->
                        val selected = tab.key == top
                        NavigationRailItem(
                            selected = selected,
                            onClick = { backStack.selectTab(tab.key) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                }
            }
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                // The bottom bar covers the navigation bar inset; without it, the screens need the inset.
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (showBottomBar) Modifier.padding(bottom = padding.calculateBottomPadding())
                        else Modifier.windowInsetsPadding(WindowInsets.navigationBars),
                    ),
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                entryProvider = entryProvider {
                    entry<HomeKey> {
                        HomeRoute(
                            viewModel = viewModel {
                                HomeViewModel(
                                    server = graph.server,
                                    settings = graph.settings,
                                    permissions = graph.permissions,
                                    network = graph.networkUpdates,
                                    recentCalls = graph.audit.observe(limit = RECENT_CALLS),
                                    recentCallsLimit = RECENT_CALLS,
                                    currentVersion = BuildConfig.VERSION_NAME,
                                    releasesUrl = UpdateChecker.RELEASES_URL,
                                    essentials = EssentialCapabilityIds,
                                )
                            },
                            onOpenSettings = { backStack.add(SettingsKey) },
                            onOpenPermissions = { backStack.add(SetupKey(firstRun = false)) },
                            onOpenActivity = { backStack.selectTab(ActivityKey) },
                            onOpenComputers = { backStack.add(ComputersKey) },
                        )
                    }
                    entry<ToolsKey> {
                        ToolsRoute(
                            viewModel {
                                ToolsViewModel(
                                    server = graph.server,
                                    settings = graph.settings,
                                    permissions = graph.permissions,
                                    resolve = graph.catalog::resolve,
                                )
                            },
                        )
                    }
                    entry<ActivityKey> {
                        ActivityRoute(viewModel { ActivityViewModel(AuditLog(graph.audit)) })
                    }
                    entry<SettingsKey> {
                        SettingsRoute(
                            viewModel = viewModel {
                                SettingsViewModel(
                                    settings = graph.settings,
                                    server = graph.server,
                                    versionName = BuildConfig.VERSION_NAME,
                                    approvals = graph.approvals,
                                    network = graph.networkUpdates,
                                    checkForUpdate = graph.updates::checkNow,
                                )
                            },
                            onBack = { backStack.removeLastOrNull() },
                            onOpenPermissions = { backStack.add(SetupKey(firstRun = false)) },
                            onOpenComputers = { backStack.add(ComputersKey) },
                        )
                    }
                    entry<ComputersKey> {
                        ComputersRoute(
                            viewModel = viewModel {
                                ComputersViewModel(
                                    pairing = graph.server,
                                    settings = graph.settings,
                                    network = graph.networkUpdates,
                                    recentCalls = graph.audit.observe(limit = RECENT_CALLS),
                                    status = graph.server.status,
                                )
                            },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                    entry<SetupKey> { key ->
                        SetupRoute(
                            viewModel = viewModel {
                                SetupViewModel(
                                    firstRun = key.firstRun,
                                    permissions = graph.permissions,
                                    settings = graph.settings,
                                    countByCapability = { graph.catalog.toolCountsByCapability(graph.server.currentSettings.value) },
                                    countReady = { granted -> graph.catalog.availableTools(graph.server.currentSettings.value, granted).size },
                                )
                            },
                            onFinished = {
                                if (key.firstRun) backStack.replaceAll(HomeKey) else backStack.removeLastOrNull()
                            },
                            onBack = { backStack.removeLastOrNull() },
                        )
                    }
                },
            )
        }
    }
}

/** Window width from which the tabs move to a side rail. */
private val WIDE_LAYOUT = 600.dp

private fun NavBackStack<NavKey>.selectTab(key: NavKey) {
    if (lastOrNull() == key) return
    if (key == HomeKey) replaceAll(HomeKey) else replaceAll(HomeKey, key)
}

private fun NavBackStack<NavKey>.replaceAll(vararg keys: NavKey) {
    // Add first, then trim, so the stack is never empty mid-change.
    addAll(keys)
    repeat(size - keys.size) { removeAt(0) }
}

/** Adapts the Room audit sink to what the Activity screen needs. */
private class AuditLog(private val sink: RoomAuditSink) : ActivityLog {
    override fun observe(limit: Int): Flow<List<ToolCallAudit>> = sink.observe(limit)
    override suspend fun clear() = sink.clear()
    override suspend fun exportJson(): String = sink.exportJson()
}
