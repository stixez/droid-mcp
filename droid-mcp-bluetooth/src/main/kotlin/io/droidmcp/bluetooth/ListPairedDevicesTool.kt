package io.droidmcp.bluetooth

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class ListPairedDevicesTool(private val context: Context) : McpTool {

    override val name = "list_paired_devices"
    override val description = "List all paired/bonded Bluetooth devices"
    override val parameters = emptyList<ToolParameter>()

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
            ?: return ToolResult.error("Bluetooth is not supported on this device")

        if (!adapter.isEnabled) {
            return ToolResult.error("Bluetooth is not enabled")
        }

        val bondedDevices = try {
            adapter.bondedDevices ?: emptySet()
        } catch (e: SecurityException) {
            return ToolResult.error("BLUETOOTH_CONNECT permission is required to list paired devices")
        }

        val devices = bondedDevices.map { device ->
            val deviceName = try { device.name } catch (e: SecurityException) { null }
            val deviceAddress = try { device.address } catch (e: SecurityException) { null }
            val deviceType = when (device.type) {
                BluetoothDevice.DEVICE_TYPE_CLASSIC -> "classic"
                BluetoothDevice.DEVICE_TYPE_LE -> "le"
                BluetoothDevice.DEVICE_TYPE_DUAL -> "dual"
                else -> "unknown"
            }
            val bondState = when (device.bondState) {
                BluetoothDevice.BOND_BONDED -> "bonded"
                BluetoothDevice.BOND_BONDING -> "bonding"
                BluetoothDevice.BOND_NONE -> "none"
                else -> "unknown"
            }
            mapOf(
                "device_name" to deviceName,
                "device_address" to deviceAddress,
                "device_type" to deviceType,
                "bond_state" to bondState,
            )
        }

        return ToolResult.success(mapOf(
            "devices" to devices,
            "count" to devices.size,
        ))
    }
}
