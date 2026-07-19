package io.droidmcp.apps

import android.content.Context
import io.droidmcp.core.McpTool

/**
 * Provider for the app-inventory tools ([ListInstalledAppsTool], [GetAppInfoTool],
 * [LaunchAppTool]). Declares no manifest permissions; results may be subject to API 30+
 * package-visibility rules.
 */
object AppsTools {

    /** All app tools, bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        ListInstalledAppsTool(context),
        GetAppInfoTool(context),
        LaunchAppTool(context),
    )

    /** None — these tools require no runtime permissions. */
    fun requiredPermissions(): List<String> = emptyList()

    /** Always `true`; no permissions are needed. */
    fun hasPermissions(context: Context): Boolean = true
}
