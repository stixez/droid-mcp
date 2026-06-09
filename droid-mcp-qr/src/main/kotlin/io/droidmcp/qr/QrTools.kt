package io.droidmcp.qr

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

/**
 * Provider for the QR/barcode module: [ScanQrCodeTool], [ScanBarcodeTool], and [GenerateQrCodeTool].
 *
 * Note: all three tools operate on image files or text and none actually open the camera, yet
 * [requiredPermissions] reports [Manifest.permission.CAMERA] (matching the module manifest). This is
 * conservative — live camera scanning is not implemented here — so the declared permission is broader
 * than what the current tools exercise.
 */
object QrTools {

    /** All tools in this module: [ScanQrCodeTool], [ScanBarcodeTool], [GenerateQrCodeTool]. */
    fun all(context: Context): List<McpTool> = listOf(
        ScanQrCodeTool(context),
        ScanBarcodeTool(context),
        GenerateQrCodeTool(),
    )

    /** Reports [Manifest.permission.CAMERA]; note none of the current tools actually use the camera. */
    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.CAMERA,
    )

    /** `true` when [Manifest.permission.CAMERA] is granted. */
    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
