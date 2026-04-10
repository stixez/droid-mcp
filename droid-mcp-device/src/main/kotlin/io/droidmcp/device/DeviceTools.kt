package io.droidmcp.device

import android.content.Context
import io.droidmcp.core.McpTool

object DeviceTools {

    fun all(context: Context): List<McpTool> = listOf(
        GetDeviceInfoTool(context),
        GetBatteryInfoTool(context),
        GetConnectivityTool(context),
        GetStorageInfoTool(),
    )

    fun requiredPermissions(): List<String> = emptyList()

    fun hasPermissions(context: Context): Boolean = true
}
