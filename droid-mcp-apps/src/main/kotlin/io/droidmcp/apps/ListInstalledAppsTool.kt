package io.droidmcp.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class ListInstalledAppsTool(private val context: Context) : McpTool {

    override val name = "list_installed_apps"
    override val description = "List installed apps on the device"
    override val parameters = listOf(
        ToolParameter("include_system", "Include system apps in results (default: false)", ParameterType.BOOLEAN),
        ToolParameter("limit", "Maximum number of apps to return (1-100, default: 50)", ParameterType.INTEGER),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val includeSystem = params["include_system"] as? Boolean ?: false
        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 50

        val pm = context.packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { app ->
                if (!includeSystem) {
                    (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0
                } else {
                    true
                }
            }
            .sortedBy { it.loadLabel(pm).toString().lowercase() }
            .take(limit)
            .map { app ->
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val versionName = try {
                    pm.getPackageInfo(app.packageName, 0).versionName ?: ""
                } catch (e: Exception) {
                    ""
                }
                mapOf(
                    "app_name" to app.loadLabel(pm).toString(),
                    "package_name" to app.packageName,
                    "version" to versionName,
                    "is_system_app" to isSystem,
                )
            }

        return ToolResult.success(mapOf(
            "apps" to apps,
            "count" to apps.size,
            "include_system" to includeSystem,
        ))
    }
}
