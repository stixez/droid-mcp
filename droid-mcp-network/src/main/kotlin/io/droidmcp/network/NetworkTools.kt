package io.droidmcp.network

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

/**
 * Provider for the network tools: data usage, cellular signal, and VPN status. Combines a
 * normal runtime permission (`ACCESS_NETWORK_STATE`) with the `PACKAGE_USAGE_STATS`
 * special-access grant (checked via [android.app.AppOpsManager], not a runtime dialog).
 * [GetDataUsageTool] degrades to TrafficStats without usage access; the signal and VPN tools
 * only need `ACCESS_NETWORK_STATE`.
 */
object NetworkTools {
    /** All network [McpTool]s bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        GetDataUsageTool(context),
        GetCellularSignalTool(context),
        IsVpnActiveTool(context),
    )

    /** The two permissions backing full functionality (usage-stats + network-state). */
    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.PACKAGE_USAGE_STATS,
        Manifest.permission.ACCESS_NETWORK_STATE,
    )

    /** True only when both usage-stats access (AppOps) and `ACCESS_NETWORK_STATE` are granted. */
    fun hasPermissions(context: Context): Boolean {
        return hasPackageUsageStatsPermission(context) &&
                PermissionHelper.hasPermissions(context, listOf(Manifest.permission.ACCESS_NETWORK_STATE))
    }

    /** Checks the `GET_USAGE_STATS` AppOp via [android.app.AppOpsManager] for this app. */
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
