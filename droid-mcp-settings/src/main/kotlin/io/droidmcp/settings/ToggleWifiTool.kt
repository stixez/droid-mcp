package io.droidmcp.settings

import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class ToggleWifiTool(private val context: Context) : McpTool {

    override val name = "toggle_wifi"
    override val description = "Toggle WiFi on or off. On Android 10+ (API 29+), this opens the WiFi settings panel as apps cannot directly toggle WiFi."
    override val parameters = listOf(
        ToolParameter("enabled", "Whether to enable (true) or disable (false) WiFi", ParameterType.BOOLEAN, required = true),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val enabled = params["enabled"] as? Boolean
            ?: return ToolResult.error("enabled is required")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29+: Cannot directly toggle WiFi, open settings panel
            val intent = Intent(Settings.Panel.ACTION_WIFI).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                context.startActivity(intent)
                ToolResult.success(mapOf(
                    "success" to true,
                    "message" to "On Android 10+, apps cannot directly toggle WiFi. Opened WiFi settings panel.",
                    "opened_settings" to true,
                ))
            } catch (e: Exception) {
                ToolResult.error("Failed to open WiFi settings: ${e.message}")
            }
        } else {
            @Suppress("DEPRECATION")
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            return try {
                wifiManager.isWifiEnabled = enabled
                ToolResult.success(mapOf(
                    "success" to true,
                    "enabled" to enabled,
                    "opened_settings" to false,
                ))
            } catch (e: Exception) {
                ToolResult.error("Failed to toggle WiFi: ${e.message}")
            }
        }
    }
}
