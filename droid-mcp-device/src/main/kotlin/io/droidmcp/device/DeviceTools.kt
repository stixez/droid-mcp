package io.droidmcp.device

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

/**
 * Provider for the device module: read-only hardware and system facts (model, battery,
 * connectivity, storage). Only [GetConnectivityTool] needs a permission — the normal
 * (install-time) `ACCESS_NETWORK_STATE` — for `ConnectivityManager.getNetworkCapabilities()`.
 */
object DeviceTools {

    /** Every tool in this module, bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        GetDeviceInfoTool(context),
        GetBatteryInfoTool(context),
        GetConnectivityTool(context),
        GetStorageInfoTool(),
    )

    /** Permissions this module needs: `ACCESS_NETWORK_STATE`, for [GetConnectivityTool]. */
    fun requiredPermissions(): List<String> = listOf(Manifest.permission.ACCESS_NETWORK_STATE)

    /** True if all [requiredPermissions] are currently granted. */
    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
