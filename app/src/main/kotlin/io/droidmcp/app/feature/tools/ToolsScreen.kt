package io.droidmcp.app.feature.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.droidmcp.app.data.tools.ToolSection
import io.droidmcp.app.ui.AppIcons
import io.droidmcp.app.ui.components.Banner
import io.droidmcp.app.ui.components.BannerTone
import io.droidmcp.app.ui.components.ScreenScaffold
import io.droidmcp.app.ui.components.SectionHeader
import io.droidmcp.app.ui.components.RowDivider
import io.droidmcp.app.ui.components.SettingsGroup
import io.droidmcp.app.ui.components.SwitchRow
import io.droidmcp.app.ui.components.Tag
import io.droidmcp.app.ui.components.TagRow
import io.droidmcp.app.ui.grant.rememberGrantLauncher
import io.droidmcp.app.ui.theme.AppTheme

@Composable
fun ToolsRoute(viewModel: ToolsViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val grant = rememberGrantLauncher(onReturn = { viewModel.onIntent(ToolsIntent.RefreshPermissions) })
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onIntent(ToolsIntent.RefreshPermissions) }
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ToolsEffect.LaunchGrant -> grant(effect.capability)
            }
        }
    }
    ScreenScaffold(title = "Tools") { padding ->
        ToolsContent(state, viewModel::onIntent, padding)
    }
}

@Composable
fun ToolsContent(state: ToolsState, onIntent: (ToolsIntent) -> Unit, contentPadding: PaddingValues) {
    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val visible = state.visibleGroups
    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = contentPadding.calculateTopPadding(),
            bottom = 24.dp,
        ),
    ) {
        item(key = "search") {
            OutlinedTextField(
                value = state.query,
                onValueChange = { onIntent(ToolsIntent.Search(it)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search ${state.groups.sumOf { it.tools.size }} tools") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onIntent(ToolsIntent.Search("")) }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
            )
        }

        item(key = "restart") {
            AnimatedVisibility(state.restartNeeded, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Banner(
                    text = "Restart the server to apply the new behaviour.",
                    icon = Icons.Outlined.Restore,
                    tone = BannerTone.Attention,
                    actionLabel = "Restart",
                    onAction = { onIntent(ToolsIntent.Restart) },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        if (state.query.isBlank()) {
            item(key = "behaviour-header") {
                SectionHeader("Behaviour", subtitle = "${state.offeredCount} tools offered to clients")
            }
            item(key = "behaviour") {
                SettingsGroup {
                    SwitchRow(
                        title = "Ask before risky actions",
                        subtitle = "Sending, deleting, changing things and web requests wait for your OK on the phone.",
                        checked = state.confirmRiskyCalls,
                        onChange = { onIntent(ToolsIntent.SetConfirmRiskyCalls(it)) },
                    )
                    RowDivider()
                    SwitchRow(
                        title = "Read-only mode",
                        subtitle = "Clients can look, but can't send, change or delete anything.",
                        checked = state.readOnly,
                        onChange = { onIntent(ToolsIntent.SetReadOnly(it)) },
                    )
                    RowDivider()
                    SwitchRow(
                        title = "Allow file changes",
                        subtitle = "Adds tools that write, move and delete files in shared storage.",
                        checked = state.allowFileWrites,
                        onChange = { onIntent(ToolsIntent.SetAllowFileWrites(it)) },
                    )
                }
            }
        }

        val bySection = visible.groupBy { it.section }
        ToolSection.entries.forEach { section ->
            val groups = bySection[section] ?: return@forEach
            item(key = "section-${section.name}") { SectionHeader(section.title) }
            items(groups, key = { it.id }) { group ->
                GroupCard(
                    group = group,
                    expanded = state.isExpanded(group.id),
                    asksFirst = state.confirmRiskyCalls && !state.readOnly,
                    // Read-only mode keeps web tools listed, and they still ask.
                    webAsksFirst = state.confirmRiskyCalls,
                    onIntent = onIntent,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }

        if (visible.isEmpty()) {
            item(key = "empty") {
                Text(
                    "No tools match \"${state.query}\".",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 32.dp, start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: ToolGroupUi,
    expanded: Boolean,
    asksFirst: Boolean,
    webAsksFirst: Boolean,
    onIntent: (ToolsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onIntent(ToolsIntent.ToggleExpanded(group.id)) }
                    .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(
                            if (group.available && group.anyEnabled) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.toolGroup(group.id), contentDescription = null, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(group.title, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            !group.available -> "Needs permission · ${group.tools.size} ${if (group.tools.size == 1) "tool" else "tools"}"
                            group.enabledCount == group.tools.size -> "${group.tools.size} ${if (group.tools.size == 1) "tool" else "tools"}"
                            else -> "${group.enabledCount} of ${group.tools.size} on"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (group.available) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.tertiary,
                    )
                }
                if (group.available) {
                    Switch(
                        checked = group.anyEnabled,
                        onCheckedChange = { onIntent(ToolsIntent.SetGroupEnabled(group.id, it)) },
                        modifier = Modifier.semantics { stateDescription = "${group.enabledCount} of ${group.tools.size} on" },
                    )
                } else {
                    val capability = group.requiredCapability
                    if (capability != null) {
                        FilledTonalButton(onClick = { onIntent(ToolsIntent.Grant(capability)) }) { Text("Allow") }
                    }
                }
                Icon(
                    Icons.Outlined.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .rotate(rotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column(Modifier.padding(bottom = 8.dp)) {
                    group.tools.forEach { tool ->
                        RowDivider()
                        ToolRow(
                            tool,
                            enabled = group.available,
                            asksFirst = if (tool.destructive) asksFirst else webAsksFirst,
                            onChange = { onIntent(ToolsIntent.SetToolEnabled(tool.name, it)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolRow(tool: ToolUi, enabled: Boolean, asksFirst: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = tool.enabled, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(start = 72.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(tool.name, style = AppTheme.code.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (tool.readOnly || tool.destructive || tool.openWorld) {
                TagRow(Modifier.padding(top = 6.dp)) {
                    if (tool.readOnly) Tag("Read-only")
                    if (tool.destructive || tool.openWorld) {
                        val label = when {
                            asksFirst -> "Asks you first"
                            tool.destructive -> "Changes things"
                            else -> "Uses the internet"
                        }
                        Tag(label, color = MaterialTheme.colorScheme.tertiaryContainer)
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = tool.enabled, onCheckedChange = null, enabled = enabled)
    }
}


