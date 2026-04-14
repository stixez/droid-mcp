package io.droidmcp.usb

import android.content.Context
import io.droidmcp.core.McpTool

object UsbTools {

    fun all(context: Context): List<McpTool> = listOf(
        ListUsbDevicesTool(context),
        GetUsbDeviceInfoTool(context),
    )

    fun requiredPermissions(): List<String> = emptyList()

    fun hasPermissions(context: Context): Boolean = true
}
