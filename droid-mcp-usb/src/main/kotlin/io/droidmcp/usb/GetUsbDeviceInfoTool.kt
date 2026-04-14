package io.droidmcp.usb

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class GetUsbDeviceInfoTool(private val context: Context) : McpTool {

    override val name = "get_usb_device_info"
    override val description = "Get detailed information about a specific connected USB device"
    override val parameters = listOf(
        ToolParameter("device_name", "Device name from list_usb_devices (e.g. '/dev/bus/usb/001/002')", ParameterType.STRING, required = true),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val deviceName = params["device_name"]?.toString()
            ?: return ToolResult.error("device_name is required")

        val usbManager = context.getSystemService(Context.USB_SERVICE) as? UsbManager
            ?: return ToolResult.error("USB host not supported on this device")

        val device = usbManager.deviceList[deviceName]
            ?: return ToolResult.error("Device not found: $deviceName")

        val interfaces = (0 until device.interfaceCount).map { i ->
            val iface = device.getInterface(i)
            val endpoints = (0 until iface.endpointCount).map { j ->
                val endpoint = iface.getEndpoint(j)
                mapOf(
                    "address" to endpoint.address,
                    "direction" to if (endpoint.direction == UsbConstants.USB_DIR_IN) "IN" else "OUT",
                    "type" to when (endpoint.type) {
                        UsbConstants.USB_ENDPOINT_XFER_CONTROL -> "control"
                        UsbConstants.USB_ENDPOINT_XFER_ISOC -> "isochronous"
                        UsbConstants.USB_ENDPOINT_XFER_BULK -> "bulk"
                        UsbConstants.USB_ENDPOINT_XFER_INT -> "interrupt"
                        else -> "unknown"
                    },
                    "max_packet_size" to endpoint.maxPacketSize,
                )
            }
            mapOf(
                "id" to iface.id,
                "class" to iface.interfaceClass,
                "subclass" to iface.interfaceSubclass,
                "protocol" to iface.interfaceProtocol,
                "endpoint_count" to iface.endpointCount,
                "endpoints" to endpoints,
            )
        }

        return ToolResult.success(mapOf(
            "name" to device.deviceName,
            "vendor_id" to device.vendorId,
            "product_id" to device.productId,
            "device_class" to device.deviceClass,
            "device_subclass" to device.deviceSubclass,
            "device_protocol" to device.deviceProtocol,
            "manufacturer" to device.manufacturerName,
            "product" to device.productName,
            "serial_number" to device.serialNumber,
            "version" to device.version,
            "interface_count" to device.interfaceCount,
            "interfaces" to interfaces,
            "has_permission" to usbManager.hasPermission(device),
        ))
    }
}
