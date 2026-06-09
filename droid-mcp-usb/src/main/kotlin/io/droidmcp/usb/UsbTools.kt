package io.droidmcp.usb

import android.content.Context
import io.droidmcp.core.McpTool

/**
 * Provider for the USB module: [ListUsbDevicesTool] and [GetUsbDeviceInfoTool].
 *
 * Requires no permissions; the manifest declares the `android.hardware.usb.host` feature as
 * optional (`required="false"`). Tools degrade gracefully when host mode is unavailable.
 */
object UsbTools {

    /** All USB tools bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        ListUsbDevicesTool(context),
        GetUsbDeviceInfoTool(context),
    )

    /** Empty — enumerating USB descriptors needs no runtime permissions. */
    fun requiredPermissions(): List<String> = emptyList()

    /** Always true; no permissions are required. */
    fun hasPermissions(context: Context): Boolean = true
}
