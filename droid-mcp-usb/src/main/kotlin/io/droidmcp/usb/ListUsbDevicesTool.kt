package io.droidmcp.usb

import android.content.Context
import android.hardware.usb.UsbManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Lists currently connected USB devices from [UsbManager.getDeviceList].
 *
 * No permissions required. Returns an error if USB host mode is unsupported on the device.
 * Read-only.
 *
 * Output keys: `count`, `devices` (each with `name`, `vendor_id`, `product_id`, `device_class`,
 * `device_subclass`, `manufacturer`, `product`, `interface_count`, `has_permission`).
 */
class ListUsbDevicesTool(private val context: Context) : McpTool {

    override val name = "list_usb_devices"
    override val description = "List all connected USB devices"
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: return ToolResult.error("USB host not supported on this device")

        val devices = usbManager.deviceList.values.map { device ->
            mapOf(
                "name" to device.deviceName,
                "vendor_id" to device.vendorId,
                "product_id" to device.productId,
                "device_class" to device.deviceClass,
                "device_subclass" to device.deviceSubclass,
                "manufacturer" to device.manufacturerName,
                "product" to device.productName,
                "interface_count" to device.interfaceCount,
                "has_permission" to usbManager.hasPermission(device),
            )
        }

        return ToolResult.success(mapOf(
            "count" to devices.size,
            "devices" to devices,
        ))
    }
}
