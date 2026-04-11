package io.droidmcp.qr

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

object QrTools {

    fun all(context: Context): List<McpTool> = listOf(
        ScanQrCodeTool(context),
        ScanBarcodeTool(context),
        GenerateQrCodeTool(),
    )

    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.CAMERA,
    )

    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
