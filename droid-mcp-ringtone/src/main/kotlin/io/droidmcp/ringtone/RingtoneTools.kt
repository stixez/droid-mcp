package io.droidmcp.ringtone

import android.content.Context
import io.droidmcp.core.McpTool

object RingtoneTools {

    fun all(context: Context): List<McpTool> = listOf(
        ListRingtonesTool(context),
        GetActiveRingtoneTool(context),
        SetRingtoneTool(context),
    )

    // WRITE_SETTINGS is a special permission; read operations work without it
    fun requiredPermissions(): List<String> = emptyList()

    fun hasPermissions(context: Context): Boolean = true
}
