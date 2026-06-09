package io.droidmcp.location

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

/**
 * Provider for the location tools: [GetCurrentLocationTool] and [GetLocationAddressTool].
 * Note that only the current-location tool gates on these permissions; reverse geocoding
 * needs network access, not a location permission.
 */
object LocationTools {

    /** All location [McpTool]s bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        GetCurrentLocationTool(context),
        GetLocationAddressTool(context),
    )

    /** Location permissions; either fine or coarse is sufficient at runtime. */
    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    /** True when at least the declared location permissions are granted. */
    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
