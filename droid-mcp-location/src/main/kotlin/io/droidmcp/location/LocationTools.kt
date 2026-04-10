package io.droidmcp.location

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

object LocationTools {

    fun all(context: Context): List<McpTool> = listOf(
        GetCurrentLocationTool(context),
        GetLocationAddressTool(context),
    )

    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
