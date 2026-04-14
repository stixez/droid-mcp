package io.droidmcp.ringtone

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class SetRingtoneTool(private val context: Context) : McpTool {

    override val name = "set_ringtone"
    override val description = "Set the default ringtone, notification, or alarm sound. Requires WRITE_SETTINGS permission."
    override val parameters = listOf(
        ToolParameter("uri", "Ringtone URI (from list_ringtones). Pass 'silent' to set to silent.", ParameterType.STRING, required = true),
        ToolParameter("type", "Ringtone type: 'ringtone', 'notification', or 'alarm' (default: 'ringtone')", ParameterType.STRING),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val uriStr = params["uri"]?.toString()
            ?: return ToolResult.error("uri is required")
        val typeStr = params["type"]?.toString() ?: "ringtone"

        val type = when (typeStr) {
            "ringtone" -> RingtoneManager.TYPE_RINGTONE
            "notification" -> RingtoneManager.TYPE_NOTIFICATION
            "alarm" -> RingtoneManager.TYPE_ALARM
            else -> return ToolResult.error("type must be 'ringtone', 'notification', or 'alarm'")
        }

        if (!Settings.System.canWrite(context)) {
            return ToolResult.error("WRITE_SETTINGS permission required. Enable in Settings > Apps > Special access > Modify system settings.")
        }

        val uri = if (uriStr == "silent") {
            null
        } else {
            val parsed = Uri.parse(uriStr)
            if (parsed.scheme != "content") {
                return ToolResult.error("URI must use content:// scheme (use list_ringtones to get valid URIs)")
            }
            parsed
        }

        return try {
            RingtoneManager.setActualDefaultRingtoneUri(context, type, uri)

            ToolResult.success(mapOf(
                "success" to true,
                "type" to typeStr,
                "uri" to uriStr,
                "is_silent" to (uri == null),
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to set ringtone: ${e.message}")
        }
    }
}
