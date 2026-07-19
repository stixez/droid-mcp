package io.droidmcp.nfc

import android.content.Context
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.tech.Ndef
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes a single NDEF record (text or URI) to the most recently scanned tag.
 *
 * Operates on the tag cached in [NfcTagCache], so a tag must have been scanned
 * first (see [ReadNfcTagTool]). Requires NFC available and enabled, and the tag
 * to support NDEF and be writable with enough capacity. Destructive — overwrites
 * tag contents.
 *
 * Output keys: `success`, `type`, `content`, `bytes_written`.
 */
class WriteNfcTagTool(private val context: Context) : McpTool {

    override val name = "write_nfc_tag"
    override val description = "Write an NDEF record to the currently scanned NFC tag. A tag must have been scanned first (see read_nfc_tag). Supports text and URI record types."
    override val parameters = listOf(
        ToolParameter("type", "Record type: 'text' or 'uri'", ParameterType.STRING, required = true),
        ToolParameter("content", "The text or URI to write", ParameterType.STRING, required = true),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val adapter = NfcAdapter.getDefaultAdapter(context)
            ?: return@withContext ToolResult.error("NFC is not available on this device")

        if (!adapter.isEnabled) {
            return@withContext ToolResult.error("NFC is disabled")
        }

        val type = params["type"]?.toString()
            ?: return@withContext ToolResult.error("type is required")
        val content = params["content"]?.toString()
            ?: return@withContext ToolResult.error("content is required")

        if (type !in listOf("text", "uri")) {
            return@withContext ToolResult.error("type must be 'text' or 'uri'")
        }

        val tag = NfcTagCache.lastTag
            ?: return@withContext ToolResult.error("No NFC tag available. Hold a tag near the device first.")

        val ndef = Ndef.get(tag)
            ?: return@withContext ToolResult.error("Tag does not support NDEF")

        try {
            // Record/message construction moved inside the try: createUri/createTextRecord throw
            // IllegalArgumentException on malformed input, which — before this fix — escaped the
            // handler below and surfaced as a generic registry-level error instead of this tool's own.
            val record = when (type) {
                "uri" -> NdefRecord.createUri(content)
                else -> NdefRecord.createTextRecord("en", content)
            }
            val message = NdefMessage(arrayOf(record))

            ndef.connect()
            if (!ndef.isWritable) {
                return@withContext ToolResult.error("Tag is read-only")
            }
            if (message.toByteArray().size > ndef.maxSize) {
                return@withContext ToolResult.error("Content too large for tag (max ${ndef.maxSize} bytes)")
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
