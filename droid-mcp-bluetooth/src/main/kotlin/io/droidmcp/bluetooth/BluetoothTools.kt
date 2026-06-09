package io.droidmcp.bluetooth

import android.Manifest
import android.content.Context
import android.os.Build
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

/**
 * Provider for Bluetooth read tools: [GetBluetoothStatusTool] and [ListPairedDevicesTool].
 * Requires `BLUETOOTH_CONNECT` on API 31+ (Android S), legacy `BLUETOOTH` below that.
 */
object BluetoothTools {

    /** Both Bluetooth tools. */
    fun all(context: Context): List<McpTool> = listOf(
        GetBluetoothStatusTool(context),
        ListPairedDevicesTool(context),
    )

    /** `BLUETOOTH_CONNECT` on API 31+, otherwise the legacy `BLUETOOTH` permission. */
    fun requiredPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            @Suppress("DEPRECATION")
            add(Manifest.permission.BLUETOOTH)
        }
    }

    /** True when the version-appropriate Bluetooth permission is granted. */
    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
