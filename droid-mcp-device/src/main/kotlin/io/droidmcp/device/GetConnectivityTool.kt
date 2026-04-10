package io.droidmcp.device

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class GetConnectivityTool(private val context: Context) : McpTool {

    override val name = "get_connectivity"
    override val description = "Get network connectivity status: WiFi, cellular, Bluetooth"
    override val parameters = emptyList<ToolParameter>()

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
