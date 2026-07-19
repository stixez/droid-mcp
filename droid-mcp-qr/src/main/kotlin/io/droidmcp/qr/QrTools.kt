package io.droidmcp.qr

import android.content.Context
import io.droidmcp.core.McpTool

/**
 * Provider for the QR/barcode module: [ScanQrCodeTool], [ScanBarcodeTool], and [GenerateQrCodeTool].
 *
 * All three tools operate on image files or text; none opens the camera, so no permission is
 * required. (Live camera scanning is not implemented here.)
 */
object QrTools {

    /** All tools in this module: [ScanQrCodeTool], [ScanBarcodeTool], [GenerateQrCodeTool]. */
    fun all(context: Context): List<McpTool> = listOf(
        ScanQrCodeTool(context),
        ScanBarcodeTool(context),
        GenerateQrCodeTool(),
    )

    /** No permissions required — see object KDoc. */
    fun requiredPermissions(): List<String> = emptyList()

    /** Always true; see [requiredPermissions]. */
    fun hasPermissions(context: Context): Boolean = true
}
