package io.droidmcp.nfc

import android.Manifest
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.PermissionHelper

/**
 * Provider for the NFC tools: [GetNfcStatusTool], [ReadNfcTagTool], and
 * [WriteNfcTagTool].
 *
 * Read/write tools rely on the host Activity feeding scanned tags into
 * [NfcTagCache] via NFC foreground dispatch.
 */
object NfcTools {

    /** Instantiates all NFC tools bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        GetNfcStatusTool(context),
        ReadNfcTagTool(context),
        WriteNfcTagTool(context),
    )

    /** The install-time `NFC` permission required by all NFC tools. */
    fun requiredPermissions(): List<String> = listOf(
        Manifest.permission.NFC,
    )

    /** Returns whether [context] holds the NFC permission. */
    fun hasPermissions(context: Context): Boolean =
        PermissionHelper.hasPermissions(context, requiredPermissions())
}
