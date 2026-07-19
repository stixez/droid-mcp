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
 * `adapter_address` is also null on API 26+ when the platform's anonymized dummy value
 * (`02:00:00:00:00:00`) is all a non-privileged caller ever gets back.
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
            // Since API 26, getAddress() returns the fixed dummy value below for any
            // non-privileged caller, regardless of permission — filter it out rather than
            // present it as if it were the real MAC (same treatment as wifi's BSSID).
            adapter.address?.takeIf { it != "02:00:00:00:00:00" }
        } catch (e: SecurityException) { null }

        return ToolResult.success(mapOf(
            "is_enabled" to isEnabled,
            "adapter_name" to adapterName,
            "adapter_address" to adapterAddress,
            "bluetooth_supported" to true,
        ))
    }
}
