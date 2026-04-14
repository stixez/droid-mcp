package io.droidmcp.dnd

import android.app.NotificationManager
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class GetDndStatusTool(private val context: Context) : McpTool {

    override val name = "get_dnd_status"
    override val description = "Get the current Do Not Disturb status and interruption filter"
    override val parameters = emptyList<ToolParameter>()

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
