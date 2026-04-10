package io.droidmcp.bluetooth

import android.bluetooth.BluetoothManager
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class GetBluetoothStatusTool(private val context: Context) : McpTool {

    override val name = "get_bluetooth_status"
    override val description = "Get Bluetooth adapter status including whether it is enabled and adapter details"
    override val parameters = emptyList<ToolParameter>()

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val adapter = bluetoothManager.adapter
            ?: return ToolResult.success(mapOf(
                "is_enabled" to false,
                "adapter_name" to null,
                "adapter_address" to null,
                "bluetooth_supported" to false,
            ))

        val isEnabled = adapter.isEnabled

        val adapterName = try {
            adapter.name
        } catch (e: SecurityException) { null }

        val adapterAddress = try {
            adapter.address
        } catch (e: SecurityException) { null }

        return ToolResult.success(mapOf(
            "is_enabled" to isEnabled,
            "adapter_name" to adapterName,
            "adapter_address" to adapterAddress,
            "bluetooth_supported" to true,
        ))
    }
}
