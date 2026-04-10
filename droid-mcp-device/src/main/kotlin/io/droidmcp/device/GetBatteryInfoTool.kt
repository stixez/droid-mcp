package io.droidmcp.device

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class GetBatteryInfoTool(private val context: Context) : McpTool {

    override val name = "get_battery_info"
    override val description = "Get battery level, charging status, and health"
    override val parameters = emptyList<ToolParameter>()

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return ToolResult.error("Could not read battery info")

        val level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val percentage = if (level >= 0 && scale > 0) (level * 100) / scale else -1

        val status = batteryIntent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL

        val plugged = batteryIntent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val chargingSource = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            else -> "none"
        }

        return ToolResult.success(mapOf(
            "level_percent" to percentage,
            "is_charging" to isCharging,
            "charging_source" to chargingSource,
        ))
    }
}
