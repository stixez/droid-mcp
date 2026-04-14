package io.droidmcp.dnd

import android.app.NotificationManager
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class SetDndModeTool(private val context: Context) : McpTool {

    override val name = "set_dnd_mode"
    override val description = "Set Do Not Disturb mode. Requires notification policy access (Settings > Apps > Special access > Do Not Disturb access)."
    override val parameters = listOf(
        ToolParameter("mode", "DND mode: 'off' (all notifications), 'priority' (priority only), 'alarms' (alarms only), 'none' (total silence)", ParameterType.STRING, required = true),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val mode = params["mode"]?.toString()
            ?: return ToolResult.error("mode is required")

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!nm.isNotificationPolicyAccessGranted) {
            return ToolResult.error("Notification policy access not granted. Enable it in Settings > Apps > Special access > Do Not Disturb access.")
        }

        val filter = when (mode) {
            "off" -> NotificationManager.INTERRUPTION_FILTER_ALL
            "priority" -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
            "alarms" -> NotificationManager.INTERRUPTION_FILTER_ALARMS
            "none" -> NotificationManager.INTERRUPTION_FILTER_NONE
            else -> return ToolResult.error("mode must be one of: off, priority, alarms, none")
        }

        nm.setInterruptionFilter(filter)

        return ToolResult.success(mapOf(
            "success" to true,
            "mode" to mode,
        ))
    }
}
