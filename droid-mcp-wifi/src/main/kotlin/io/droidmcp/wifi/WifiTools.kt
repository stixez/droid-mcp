package io.droidmcp.wifi

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

/**
 * Provider for WiFi read tools: [GetWifiInfoTool] and [ListSavedNetworksTool].
 * Requires `ACCESS_WIFI_STATE` and `ACCESS_FINE_LOCATION` (location gates SSID/BSSID on API 26+).
 */
object WifiTools {

    /** Both WiFi tools. */
    fun all(context: Context): List<McpTool> = listOf(
        GetWifiInfoTool(context),
        ListSavedNetworksTool(context),
    )

    /**
     * `ACCESS_WIFI_STATE`, `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`. COARSE is listed
     * because Android 12+ only grants FINE when both are requested together; a user who picks
     * "approximate" gets COARSE only, which isn't enough to read the SSID.
     */
    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
    )

    /** True when both WiFi-state and fine-location permissions are granted. */
    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
