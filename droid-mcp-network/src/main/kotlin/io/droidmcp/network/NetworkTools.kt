package io.droidmcp.network

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

object NetworkTools {
    fun all(context: Context): List<McpTool> = listOf(
        GetDataUsageTool(context),
        GetCellularSignalTool(context),
        IsVpnActiveTool(context),
    )

    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.PACKAGE_USAGE_STATS,
        Manifest.permission.ACCESS_NETWORK_STATE,
    )

    fun hasPermissions(context: Context): Boolean {
        return hasPackageUsageStatsPermission(context) &&
                PermissionHelper.hasPermissions(context, listOf(Manifest.permission.ACCESS_NETWORK_STATE))
    }

    private fun hasPackageUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false

        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
        }

        return mode == AppOpsManager.MODE_ALLOWED
    }
}
