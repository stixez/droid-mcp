package io.droidmcp.nfc

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

object NfcTools {

    fun all(context: Context): List<McpTool> = listOf(
        GetNfcStatusTool(context),
        ReadNfcTagTool(context),
        WriteNfcTagTool(context),
    )

    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.NFC,
    )

    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
