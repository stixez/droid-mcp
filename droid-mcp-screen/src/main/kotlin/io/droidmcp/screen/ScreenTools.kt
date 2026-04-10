package io.droidmcp.screen

import android.content.Context
import io.droidmcp.core.McpTool

object ScreenTools {

    fun all(context: Context): List<McpTool> = listOf(
        GetScreenStateTool(context),
        GetDisplayInfoTool(context),
    )

    fun requiredPermissions(): List<String> = emptyList()

    fun hasPermissions(context: Context): Boolean = true
}
