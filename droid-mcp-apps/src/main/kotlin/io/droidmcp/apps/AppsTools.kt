package io.droidmcp.apps

import android.content.Context
import io.droidmcp.core.McpTool

object AppsTools {

    fun all(context: Context): List<McpTool> = listOf(
        ListInstalledAppsTool(context),
        GetAppInfoTool(context),
        LaunchAppTool(context),
    )

    fun requiredPermissions(): List<String> = emptyList()

    fun hasPermissions(context: Context): Boolean = true
}
