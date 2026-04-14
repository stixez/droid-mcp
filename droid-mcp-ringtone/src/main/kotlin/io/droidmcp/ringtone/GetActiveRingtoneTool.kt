package io.droidmcp.ringtone

import android.content.Context
import android.media.RingtoneManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class GetActiveRingtoneTool(private val context: Context) : McpTool {

    override val name = "get_active_ringtone"
    override val description = "Get the currently active ringtone, notification, or alarm sound"
    override val parameters = listOf(
        ToolParameter("type", "Ringtone type: 'ringtone', 'notification', or 'alarm' (default: 'ringtone')", ParameterType.STRING),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val typeStr = params["type"]?.toString() ?: "ringtone"

        val type = when (typeStr) {
            "ringtone" -> RingtoneManager.TYPE_RINGTONE
            "notification" -> RingtoneManager.TYPE_NOTIFICATION
            "alarm" -> RingtoneManager.TYPE_ALARM
            else -> return ToolResult.error("type must be 'ringtone', 'notification', or 'alarm'")
        }

        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, type)
        val title = uri?.let {
            RingtoneManager.getRingtone(context, it)?.getTitle(context)
        }

        return ToolResult.success(mapOf(
            "type" to typeStr,
            "uri" to uri?.toString(),
            "title" to title,
            "is_silent" to (uri == null),
        ))
    }
}
