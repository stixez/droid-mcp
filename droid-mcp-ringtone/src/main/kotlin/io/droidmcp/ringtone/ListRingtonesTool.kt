package io.droidmcp.ringtone

import android.content.Context
import android.media.RingtoneManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class ListRingtonesTool(private val context: Context) : McpTool {

    override val name = "list_ringtones"
    override val description = "List available ringtones on the device by type"
    override val parameters = listOf(
        ToolParameter("type", "Ringtone type: 'ringtone', 'notification', or 'alarm' (default: 'ringtone')", ParameterType.STRING),
        ToolParameter("limit", "Maximum number of results (1-100, default: 50)", ParameterType.INTEGER),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val typeStr = params["type"]?.toString() ?: "ringtone"
        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 50

        val type = when (typeStr) {
            "ringtone" -> RingtoneManager.TYPE_RINGTONE
            "notification" -> RingtoneManager.TYPE_NOTIFICATION
            "alarm" -> RingtoneManager.TYPE_ALARM
            else -> return ToolResult.error("type must be 'ringtone', 'notification', or 'alarm'")
        }

        val manager = RingtoneManager(context)
        manager.setType(type)

        val ringtones = mutableListOf<Map<String, Any?>>()
        val cursor = manager.cursor

        try {
            var count = 0
            while (cursor.moveToNext() && count < limit) {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                val uri = manager.getRingtoneUri(cursor.position)
                ringtones.add(mapOf(
                    "title" to title,
                    "uri" to uri.toString(),
                ))
                count++
            }
        } finally {
            cursor.close()
        }

        return ToolResult.success(mapOf(
            "type" to typeStr,
            "count" to ringtones.size,
            "ringtones" to ringtones,
        ))
    }
}
