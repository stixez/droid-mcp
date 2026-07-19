package io.droidmcp.screen

import android.content.Context
import io.droidmcp.core.McpTool

/**
 * Provider for read-only screen/display tools: [GetScreenStateTool] and [GetDisplayInfoTool].
 */
object ScreenTools {

    /** All screen tools bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        GetScreenStateTool(context),
        GetDisplayInfoTool(context),
    )

    /** No permissions required. */
    fun requiredPermissions(): List<String> = emptyList()

    /** Always true — these tools need no permissions. */
    fun hasPermissions(context: Context): Boolean = true
}
