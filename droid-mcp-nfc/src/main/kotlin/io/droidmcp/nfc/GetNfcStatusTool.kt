package io.droidmcp.nfc

import android.content.Context
import android.nfc.NfcAdapter
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Reports whether NFC hardware is present and currently enabled.
 *
 * Reads the default [NfcAdapter]; requires no runtime permission (the manifest
 * declares `android.permission.NFC`, which is install-time). Read-only.
 *
 * Output keys: `available` (adapter present), `enabled` (NFC turned on).
 */
class GetNfcStatusTool(private val context: Context) : McpTool {

    override val name = "get_nfc_status"
    override val description = "Check if NFC is available and enabled on the device"
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val adapter = NfcAdapter.getDefaultAdapter(context)

        return ToolResult.success(mapOf(
            "available" to (adapter != null),
            "enabled" to (adapter?.isEnabled == true),
        ))
    }
}
