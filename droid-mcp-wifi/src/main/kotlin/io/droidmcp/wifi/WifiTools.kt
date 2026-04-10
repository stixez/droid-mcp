package io.droidmcp.wifi

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

object WifiTools {

    fun all(context: Context): List<McpTool> = listOf(
        GetWifiInfoTool(context),
        ListSavedNetworksTool(context),
    )

    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
