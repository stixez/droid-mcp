package io.droidmcp.apps

import android.content.Context
import io.droidmcp.core.McpTool

/**
 * Provider for the app tools ([ListInstalledAppsTool], [GetAppInfoTool], [LaunchAppTool],
 * [GetAppUsageTool]). Results may be subject to API 30+ package-visibility rules.
 * [GetAppUsageTool] needs Usage access, a special access granted in Settings (check it with
 * [io.droidmcp.core.support.UsageAccess.isGranted]); without it that tool returns an error and
 * the others still work, so [requiredPermissions] stays empty.
 */
object AppsTools {

    /** All app tools, bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        ListInstalledAppsTool(context),
        GetAppInfoTool(context),
        LaunchAppTool(context),
        GetAppUsageTool(context),
    )

    /** None — these tools require no runtime permissions (Usage access is a Settings toggle). */
    fun requiredPermissions(): List<String> = emptyList()

    /** Always `true`; no permissions are needed. */
    fun hasPermissions(context: Context): Boolean = true
}
