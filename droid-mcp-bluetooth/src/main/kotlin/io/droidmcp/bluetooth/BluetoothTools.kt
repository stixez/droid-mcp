package io.droidmcp.bluetooth

import android.Manifest
import android.content.Context
import android.os.Build
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

object BluetoothTools {

    fun all(context: Context): List<McpTool> = listOf(
        GetBluetoothStatusTool(context),
        ListPairedDevicesTool(context),
    )

    fun requiredPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            @Suppress("DEPRECATION")
            add(Manifest.permission.BLUETOOTH)
        }
    }

    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
