package io.droidmcp.nfc

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.tech.Ndef
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class ReadNfcTagTool(private val context: Context) : McpTool {

    override val name = "read_nfc_tag"
    override val description = "Read NDEF data from the last scanned NFC tag. Returns cached tag data from the most recent scan, or indicates no tag has been scanned yet."
    override val parameters = emptyList<ToolParameter>()

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val adapter = NfcAdapter.getDefaultAdapter(context)
            ?: return ToolResult.error("NFC is not available on this device")

        if (!adapter.isEnabled) {
            return ToolResult.error("NFC is disabled")
        }

        val tag = NfcTagCache.lastTag
            ?: return ToolResult.success(mapOf(
                "has_tag" to false,
                "message" to "No NFC tag has been scanned yet. Hold a tag near the device first.",
            ))

        val ndef = Ndef.get(tag)
        if (ndef == null) {
            return ToolResult.success(mapOf(
                "has_tag" to true,
                "is_ndef" to false,
                "tag_id" to tag.id?.joinToString("") { "%02x".format(it) },
                "tech_list" to tag.techList.toList(),
            ))
        }

        return try {
            ndef.connect()
            val ndefMessage = ndef.ndefMessage
            val records = ndefMessage?.records?.map { record ->
                mapOf(
                    "tnf" to record.tnf,
                    "type" to String(record.type),
                    "payload" to String(record.payload).let {
                        if (it.length > 1) it.substring(1) else it
                    },
                )
            } ?: emptyList()

            ToolResult.success(mapOf(
                "has_tag" to true,
                "is_ndef" to true,
                "tag_id" to tag.id?.joinToString("") { "%02x".format(it) },
                "max_size" to ndef.maxSize,
                "is_writable" to ndef.isWritable,
                "records" to records,
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to read NFC tag: ${e.message}")
        } finally {
            try { ndef.close() } catch (_: Exception) {}
        }
    }
}
