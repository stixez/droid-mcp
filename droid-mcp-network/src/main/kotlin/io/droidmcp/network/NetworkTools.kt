package io.droidmcp.network

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper
import io.droidmcp.core.support.UsageAccess

/**
 * Provider for the network tools: data usage, cellular signal, and VPN status. Combines a
 * normal runtime permission (`ACCESS_NETWORK_STATE`) with the `PACKAGE_USAGE_STATS`
 * special-access grant (checked via [UsageAccess], not a runtime dialog).
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
        return UsageAccess.isGranted(context) &&
                PermissionHelper.hasPermissions(context, listOf(Manifest.permission.ACCESS_NETWORK_STATE))
    }
}
