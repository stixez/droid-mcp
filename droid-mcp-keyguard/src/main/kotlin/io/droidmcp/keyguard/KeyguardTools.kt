package io.droidmcp.keyguard

import android.content.Context
import io.droidmcp.core.McpTool

object KeyguardTools {

    fun all(context: Context): List<McpTool> = listOf(
        GetLockStateTool(context),
        GetKeyguardInfoTool(context),
    )

    fun requiredPermissions(): List<String> = emptyList()

    fun hasPermissions(context: Context): Boolean = true
}
