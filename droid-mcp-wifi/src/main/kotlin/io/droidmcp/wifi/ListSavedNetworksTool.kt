package io.droidmcp.wifi

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class ListSavedNetworksTool(private val context: Context) : McpTool {

    override val name = "list_saved_networks"
    override val description = "List saved/configured WiFi networks. Note: This API is deprecated on Android 10+ (API 29+) and returns an empty list on those devices. Use system WiFi settings to view saved networks on newer devices."
    override val parameters = emptyList<ToolParameter>()

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ToolResult.success(mapOf(
                "networks" to emptyList<String>(),
                "count" to 0,
                "note" to "WifiManager.getConfiguredNetworks() is deprecated on Android 10+ (API 29+) and returns empty results. Use system WiFi settings to manage saved networks.",
            ))
        }

        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        @Suppress("DEPRECATION")
        val configuredNetworks = try {
            wifiManager.configuredNetworks ?: emptyList()
        } catch (e: SecurityException) {
            return ToolResult.error("Location permission is required to list saved networks")
        }

        val networks = configuredNetworks.map { config ->
            @Suppress("DEPRECATION")
            config.SSID?.removeSurrounding("\"") ?: ""
        }.filter { it.isNotEmpty() }

        return ToolResult.success(mapOf(
            "networks" to networks,
            "count" to networks.size,
        ))
    }
}
