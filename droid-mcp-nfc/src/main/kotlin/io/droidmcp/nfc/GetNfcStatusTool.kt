package io.droidmcp.nfc

import android.content.Context
import android.nfc.NfcAdapter
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class GetNfcStatusTool(private val context: Context) : McpTool {

    override val name = "get_nfc_status"
    override val description = "Check if NFC is available and enabled on the device"
    override val parameters = emptyList<ToolParameter>()

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val adapter = NfcAdapter.getDefaultAdapter(context)

        return ToolResult.success(mapOf(
            "available" to (adapter != null),
            "enabled" to (adapter?.isEnabled == true),
        ))
    }
}
