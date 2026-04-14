package io.droidmcp.nfc

import android.content.Context
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.tech.Ndef
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class WriteNfcTagTool(private val context: Context) : McpTool {

    override val name = "write_nfc_tag"
    override val description = "Write an NDEF record to the currently scanned NFC tag. A tag must have been scanned first (see read_nfc_tag). Supports text and URI record types."
    override val parameters = listOf(
        ToolParameter("type", "Record type: 'text' or 'uri'", ParameterType.STRING, required = true),
        ToolParameter("content", "The text or URI to write", ParameterType.STRING, required = true),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val adapter = NfcAdapter.getDefaultAdapter(context)
            ?: return ToolResult.error("NFC is not available on this device")

        if (!adapter.isEnabled) {
            return ToolResult.error("NFC is disabled")
        }

        val type = params["type"]?.toString()
            ?: return ToolResult.error("type is required")
        val content = params["content"]?.toString()
            ?: return ToolResult.error("content is required")

        if (type !in listOf("text", "uri")) {
            return ToolResult.error("type must be 'text' or 'uri'")
        }

        val tag = NfcTagCache.lastTag
            ?: return ToolResult.error("No NFC tag available. Hold a tag near the device first.")

        val ndef = Ndef.get(tag)
            ?: return ToolResult.error("Tag does not support NDEF")

        val record = when (type) {
            "uri" -> NdefRecord.createUri(content)
            else -> NdefRecord.createTextRecord("en", content)
        }
        val message = NdefMessage(arrayOf(record))

        return try {
            ndef.connect()
            if (!ndef.isWritable) {
                return ToolResult.error("Tag is read-only")
            }
            if (message.toByteArray().size > ndef.maxSize) {
                return ToolResult.error("Content too large for tag (max ${ndef.maxSize} bytes)")
            }
            ndef.writeNdefMessage(message)

            ToolResult.success(mapOf(
                "success" to true,
                "type" to type,
                "content" to content,
                "bytes_written" to message.toByteArray().size,
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to write NFC tag: ${e.message}")
        } finally {
            try { ndef.close() } catch (_: Exception) {}
        }
    }
}
