package io.droidmcp.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Lists installed applications, sorted by display label, optionally including system apps
 * (`include_system` param, default false). No permissions; the module's manifest declares a
 * `<queries>` element for `MAIN`/`LAUNCHER` intents so API 30+ package-visibility filtering
 * doesn't reduce this to a handful of force-visible packages — apps with no launcher activity
 * (background-only) are still excluded by design.
 * Output: `apps` (each `{app_name, package_name, version, is_system_app}`) capped at the
 * `limit` param, plus `count` and `include_system`.
 */
class ListInstalledAppsTool(private val context: Context) : McpTool {

    override val name = "list_installed_apps"
    override val description = "List installed apps on the device"
    override val parameters = listOf(
        ToolParameter("include_system", "Include system apps in results (default: false)", ParameterType.BOOLEAN),
        ToolParameter("limit", "Maximum number of apps to return (1-100, default: 50)", ParameterType.INTEGER),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val includeSystem = params["include_system"] as? Boolean ?: false
        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 50

        val pm = context.packageManager
        // Labels are loaded once per app (each loadLabel is a resource lookup) and reused for
        // both the sort key and the output. Flags 0: no metadata is read.
        val apps = pm.getInstalledApplications(0)
            .filter { app -> includeSystem || (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
            .map { app -> app to app.loadLabel(pm).toString() }
            .sortedBy { (_, label) -> label.lowercase() }
            .take(limit)
            .map { (app, label) ->
                val isSystem = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val versionName = try {
                    pm.getPackageInfo(app.packageName, 0).versionName ?: ""
                } catch (e: Exception) {
                    ""
                }
                mapOf(
                    "app_name" to label,
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
