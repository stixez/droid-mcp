package io.droidmcp.wifi

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.Inet4Address
import android.net.wifi.WifiManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Reports the current WiFi connection. Requires `ACCESS_WIFI_STATE`; `ssid`/`bssid` also need
 * `ACCESS_FINE_LOCATION` on API 26+ and are `null` (or filtered placeholder MAC) without it.
 *
 * Output keys: `is_connected`, `ssid`, `bssid`, `ip_address`, `link_speed_mbps`, `rssi`
 * (signal strength in dBm), `frequency_mhz`.
 */
class GetWifiInfoTool(private val context: Context) : McpTool {

    override val name = "get_wifi_info"
    override val description = "Get current WiFi connection information including SSID, signal strength, and IP address. Note: SSID requires location permission on API 26+."
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

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

        // WifiInfo.ipAddress is deprecated (IPv4-only int); read the Wi-Fi network's link
        // addresses instead. Only meaningful while the active network is Wi-Fi.
        val ipAddress = if (isConnected) {
            connectivityManager.getLinkProperties(activeNetwork)?.linkAddresses
                ?.map { it.address }
                ?.firstOrNull { it is Inet4Address }
                ?.hostAddress
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
