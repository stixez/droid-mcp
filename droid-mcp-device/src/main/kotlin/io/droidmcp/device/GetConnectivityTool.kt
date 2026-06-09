package io.droidmcp.device

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Reports the active network's transports via [ConnectivityManager]: `is_connected`, `has_wifi`,
 * `has_cellular`, `has_bluetooth`. All false when there is no active network.
 *
 * Reading network capabilities requires the normal (install-time) `ACCESS_NETWORK_STATE`
 * permission. The device module does not declare it; a host that uses this tool standalone
 * should add it (the `wifi` / `network` modules already declare it, so a merged APK is covered).
 */
class GetConnectivityTool(private val context: Context) : McpTool {

    override val name = "get_connectivity"
    override val description = "Get network connectivity status: WiFi, cellular, Bluetooth"
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val capabilities = network?.let { cm.getNetworkCapabilities(it) }

        return ToolResult.success(mapOf(
            "is_connected" to (capabilities != null),
            "has_wifi" to (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: false),
            "has_cellular" to (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ?: false),
            "has_bluetooth" to (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) ?: false),
        ))
    }
}
