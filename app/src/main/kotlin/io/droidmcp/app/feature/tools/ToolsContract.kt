package io.droidmcp.app.feature.tools

import io.droidmcp.app.data.permissions.Capability
import io.droidmcp.app.data.tools.ResolvedGroup
import io.droidmcp.app.data.tools.ToolSection

data class ToolUi(
    val name: String,
    val description: String,
    val readOnly: Boolean,
    val destructive: Boolean,
    /** Reaches the internet, so approvals cover it too. */
    val openWorld: Boolean = false,
    val enabled: Boolean,
)

data class ToolGroupUi(
    val id: String,
    val title: String,
    val section: ToolSection,
    /** False until the user grants [requiredCapability]. */
    val available: Boolean,
    val requiredCapability: String?,
    val tools: List<ToolUi>,
) {
    val enabledCount: Int get() = tools.count { it.enabled }
    val anyEnabled: Boolean get() = enabledCount > 0
}

data class ToolsState(
    val loading: Boolean = true,
    val groups: List<ToolGroupUi> = emptyList(),
    val expanded: Set<String> = emptySet(),
    val query: String = "",
    val readOnly: Boolean = false,
    val confirmRiskyCalls: Boolean = true,
    val allowFileWrites: Boolean = false,
    val restartNeeded: Boolean = false,
) {
    /** Groups matching [query], with tools filtered too; a search expands every hit. */
    val visibleGroups: List<ToolGroupUi>
        get() {
            val q = query.trim()
            if (q.isEmpty()) return groups
            return groups.mapNotNull { group ->
                if (group.title.contains(q, ignoreCase = true)) return@mapNotNull group
                val hits = group.tools.filter { it.name.contains(q, ignoreCase = true) || it.description.contains(q, ignoreCase = true) }
                if (hits.isEmpty()) null else group.copy(tools = hits)
            }
        }

    fun isExpanded(id: String): Boolean = id in expanded || query.isNotBlank()

    val offeredCount: Int get() = groups.filter { it.available }.sumOf { it.enabledCount }
}

sealed interface ToolsIntent {
    data class Search(val query: String) : ToolsIntent
    data class ToggleExpanded(val groupId: String) : ToolsIntent
    data class SetGroupEnabled(val groupId: String, val enabled: Boolean) : ToolsIntent
    data class SetToolEnabled(val name: String, val enabled: Boolean) : ToolsIntent
    data class SetReadOnly(val value: Boolean) : ToolsIntent
    data class SetConfirmRiskyCalls(val value: Boolean) : ToolsIntent
    data class SetAllowFileWrites(val value: Boolean) : ToolsIntent
    data class Grant(val capabilityId: String) : ToolsIntent
    data object RefreshPermissions : ToolsIntent
    data object Restart : ToolsIntent
}

sealed interface ToolsEffect {
    data class LaunchGrant(val capability: Capability) : ToolsEffect
}

/** Pure: the screen model for the resolved catalog and the user's switched-off tools. */
fun buildGroups(resolved: List<ResolvedGroup>, disabled: Set<String>): List<ToolGroupUi> =
    resolved.map { r ->
        ToolGroupUi(
            id = r.group.id,
            title = r.group.title,
            section = r.group.section,
            available = r.available,
            requiredCapability = r.group.requiredCapability,
            tools = r.tools.map { tool ->
                ToolUi(
                    name = tool.name,
                    description = tool.description,
                    readOnly = tool.annotations.readOnlyHint,
                    destructive = tool.annotations.destructiveHint,
                    openWorld = tool.annotations.openWorldHint,
                    enabled = tool.name !in disabled,
                )
            },
        )
    }
