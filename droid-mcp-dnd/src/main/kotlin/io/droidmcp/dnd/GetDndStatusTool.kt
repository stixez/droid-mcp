package io.droidmcp.dnd

import android.app.NotificationManager
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Reports the current Do Not Disturb / interruption-filter state.
 *
 * Reads [android.app.NotificationManager.getCurrentInterruptionFilter]; no
 * special access is needed to read it (reading is unrestricted). Read-only.
 *
 * Output keys: `enabled` (filter is not "all"), `filter` (one of `all`,
 * `priority`, `none`, `alarms`, `unknown`), `has_policy_access` (whether the
 * app holds DND policy access, required to *change* the mode).
 */
class GetDndStatusTool(private val context: Context) : McpTool {

    override val name = "get_dnd_status"
    override val description = "Get the current Do Not Disturb status and interruption filter"
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val filter = nm.currentInterruptionFilter
        val filterName = when (filter) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> "all"
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "priority"
            NotificationManager.INTERRUPTION_FILTER_NONE -> "none"
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "alarms"
            else -> "unknown"
        }

        return ToolResult.success(mapOf(
            "enabled" to (filter != NotificationManager.INTERRUPTION_FILTER_ALL),
            "filter" to filterName,
            "has_policy_access" to nm.isNotificationPolicyAccessGranted,
        ))
    }
}
