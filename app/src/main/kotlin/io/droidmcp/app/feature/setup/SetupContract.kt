package io.droidmcp.app.feature.setup

import io.droidmcp.app.data.permissions.Capability
import io.droidmcp.app.data.permissions.CapabilitySection

data class CapabilityItem(
    val capability: Capability,
    val granted: Boolean,
    /** How many tools this unlocks, to make the trade-off concrete. */
    val toolCount: Int,
)

data class CapabilitySectionUi(
    val section: CapabilitySection,
    val items: List<CapabilityItem>,
)

data class SetupState(
    /** First launch (Continue goes to Home) or reopened later (Done goes back). */
    val firstRun: Boolean = true,
    val sections: List<CapabilitySectionUi> = emptyList(),
    /** Tools a client would see right now, including the ones that need no permission. */
    val readyTools: Int = 0,
) {
    val grantedCount: Int get() = sections.sumOf { s -> s.items.count { it.granted } }
    val totalCount: Int get() = sections.sumOf { it.items.size }
}

sealed interface SetupIntent {
    data class Grant(val id: String) : SetupIntent

    /** Back from a permission dialog or Settings page. */
    data object Refresh : SetupIntent

    /** Open this app's system page (for "Allow restricted settings"). */
    data object OpenAppInfo : SetupIntent

    data object Finish : SetupIntent
}

sealed interface SetupEffect {
    data class LaunchGrant(val capability: Capability) : SetupEffect
    data object LaunchAppInfo : SetupEffect
    data object Finished : SetupEffect
}

/** Pure: builds the screen sections from the catalog and the granted set. */
fun buildSections(
    capabilities: List<Capability>,
    granted: Set<String>,
    toolCounts: Map<String, Int>,
): List<CapabilitySectionUi> =
    capabilities
        .groupBy { it.section }
        .toSortedMap(compareBy { it.ordinal })
        .map { (section, items) ->
            CapabilitySectionUi(
                section = section,
                items = items.map { CapabilityItem(it, it.id in granted, toolCounts[it.id] ?: 0) },
            )
        }
