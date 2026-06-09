package io.droidmcp.bluetooth

import android.bluetooth.BluetoothManager
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Reports Bluetooth adapter status. Reading `adapter_name`/`adapter_address` needs the
 * Bluetooth permission (see [BluetoothTools]); they return `null` if it is missing.
 *
 * Output keys: `is_enabled`, `adapter_name`, `adapter_address`, `bluetooth_supported`
 * (`false` when the device has no adapter).
 */
class GetBluetoothStatusTool(private val context: Context) : McpTool {

    override val name = "get_bluetooth_status"
    override val description = "Get Bluetooth adapter status including whether it is enabled and adapter details"
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

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
