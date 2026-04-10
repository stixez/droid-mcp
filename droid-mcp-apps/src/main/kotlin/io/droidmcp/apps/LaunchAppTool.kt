package io.droidmcp.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class LaunchAppTool(private val context: Context) : McpTool {

    override val name = "launch_app"
    override val description = "Launch an installed app by package name"
    override val parameters = listOf(
        ToolParameter("package_name", "Package name of the app to launch (e.g. com.example.app)", ParameterType.STRING, required = true),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val packageName = params["package_name"]?.toString()
            ?: return ToolResult.error("package_name is required")

        val pm = context.packageManager

        // Verify the app is installed
        try {
            pm.getPackageInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return ToolResult.error("App not found: $packageName")
        }

        val launchIntent = pm.getLaunchIntentForPackage(packageName)
            ?: return ToolResult.error("No launch intent available for: $packageName")

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(launchIntent)
            ToolResult.success(mapOf(
                "success" to true,
                "package_name" to packageName,
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to launch app: ${e.message}")
        }
    }
}
