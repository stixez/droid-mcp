package io.droidmcp.device

import android.content.Context
import io.droidmcp.core.McpTool

/**
 * Provider for the device module: read-only hardware and system facts (model, battery,
 * connectivity, storage). Requires no permissions — all data is public.
 */
object DeviceTools {

    /** Every tool in this module, bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        GetDeviceInfoTool(context),
        GetBatteryInfoTool(context),
        GetConnectivityTool(context),
        GetStorageInfoTool(),
    )

    /** Permissions this module needs — none; device facts are freely readable. */
    fun requiredPermissions(): List<String> = emptyList()

    /** Always true: this module needs no permissions. */
    fun hasPermissions(context: Context): Boolean = true
}
