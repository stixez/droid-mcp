package io.droidmcp.apps

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import java.util.Date

class GetAppInfoTool(private val context: Context) : McpTool {

    override val name = "get_app_info"
    override val description = "Get detailed information for a specific installed app"
    override val parameters = listOf(
        ToolParameter("package_name", "Package name of the app (e.g. com.example.app)", ParameterType.STRING, required = true),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val packageName = params["package_name"]?.toString()
            ?: return ToolResult.error("package_name is required")

        val pm = context.packageManager

        val packageInfo = try {
            pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        } catch (e: PackageManager.NameNotFoundException) {
            return ToolResult.error("App not found: $packageName")
        }

        val appInfo = packageInfo.applicationInfo
        val appName = appInfo?.loadLabel(pm)?.toString() ?: packageName

        @Suppress("DEPRECATION")
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }

        val permissions = packageInfo.requestedPermissions?.toList() ?: emptyList<String>()

        return ToolResult.success(mapOf(
            "app_name" to appName,
            "package_name" to packageName,
            "version_name" to (packageInfo.versionName ?: ""),
            "version_code" to versionCode,
            "install_date" to Date(packageInfo.firstInstallTime).toString(),
            "last_update" to Date(packageInfo.lastUpdateTime).toString(),
            "permissions" to permissions,
        ))
    }
}
