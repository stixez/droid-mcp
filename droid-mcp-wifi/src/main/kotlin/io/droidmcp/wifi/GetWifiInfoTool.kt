package io.droidmcp.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class GetWifiInfoTool(private val context: Context) : McpTool {

    override val name = "get_wifi_info"
    override val description = "Get current WiFi connection information including SSID, signal strength, and IP address. Note: SSID requires location permission on API 26+."
    override val parameters = emptyList<ToolParameter>()

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isConnected = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

        @Suppress("DEPRECATION")
        val wifiInfo = wifiManager.connectionInfo

        val rawSsid = wifiInfo.ssid
        // On API 26+, SSID returns "<unknown ssid>" without location permission
        val ssid = if (rawSsid == "<unknown ssid>") null else rawSsid?.removeSurrounding("\"")

        val ipInt = wifiInfo.ipAddress
        val ipAddress = if (ipInt != 0) {
            "%d.%d.%d.%d".format(
                ipInt and 0xff,
                (ipInt shr 8) and 0xff,
                (ipInt shr 16) and 0xff,
                (ipInt shr 24) and 0xff,
            )
        } else null

        return ToolResult.success(mapOf(
            "is_connected" to isConnected,
            "ssid" to ssid,
            "bssid" to (wifiInfo.bssid?.takeIf { it != "02:00:00:00:00:00" }),
            "ip_address" to ipAddress,
            "link_speed_mbps" to wifiInfo.linkSpeed,
            "rssi" to wifiInfo.rssi,
            "frequency_mhz" to wifiInfo.frequency,
        ))
    }
}
