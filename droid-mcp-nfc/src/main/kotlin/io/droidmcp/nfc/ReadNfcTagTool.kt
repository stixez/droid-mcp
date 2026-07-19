package io.droidmcp.nfc

import android.content.Context
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.tech.Ndef
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads NDEF data from the most recently scanned NFC tag.
 *
 * Does not actively scan: it returns the tag cached in [NfcTagCache], which the
 * host Activity populates from `onNewIntent()` via NFC foreground dispatch.
 * Requires NFC to be available and enabled. Read-only.
 *
 * Output keys: `has_tag`; when a tag is cached, `tag_id` (hex), `is_ndef`, and
 * `tech_list` (non-NDEF tags) or `is_ndef`/`max_size`/`is_writable`/`records`
 * (each record: `tnf`, `type`, `payload`). When no tag is cached: `has_tag`
 * false plus a `message`.
 */
class ReadNfcTagTool(private val context: Context) : McpTool {

    override val name = "read_nfc_tag"
    override val description = "Read NDEF data from the last scanned NFC tag. Returns cached tag data from the most recent scan, or indicates no tag has been scanned yet."
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val adapter = NfcAdapter.getDefaultAdapter(context)
            ?: return@withContext ToolResult.error("NFC is not available on this device")

        if (!adapter.isEnabled) {
            return@withContext ToolResult.error("NFC is disabled")
        }

        val tag = NfcTagCache.lastTag
            ?: return@withContext ToolResult.success(mapOf(
                "has_tag" to false,
                "message" to "No NFC tag has been scanned yet. Hold a tag near the device first.",
            ))

        val ndef = Ndef.get(tag)
        if (ndef == null) {
            return@withContext ToolResult.success(mapOf(
                "has_tag" to true,
                "is_ndef" to false,
                "tag_id" to tag.id?.joinToString("") { "%02x".format(it) },
                "tech_list" to tag.techList.toList(),
            ))
        }

        try {
            ndef.connect()
            val ndefMessage = ndef.ndefMessage
            val records = ndefMessage?.records?.map { record ->
                mapOf(
                    "tnf" to record.tnf,
                    "type" to String(record.type),
                    "payload" to decodePayload(record),
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

    /**
     * Decodes a record's payload per its actual NDEF type — the payload layout differs by
     * record type, so a single fixed byte offset (the previous implementation's bug) corrupts
     * every record: URI records lose their abbreviated prefix, text records keep the IANA
     * language-code prefix stuck to the text, and MIME/other records shouldn't be trimmed at all.
     */
    private fun decodePayload(record: android.nfc.NdefRecord): String = when {
        record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT) ->
            decodeTextPayload(record.payload)
        record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_URI) ->
            record.toUri()?.toString() ?: String(record.payload, Charsets.UTF_8)
        record.tnf == NdefRecord.TNF_ABSOLUTE_URI ->
            record.toUri()?.toString() ?: String(record.payload, Charsets.UTF_8)
        else -> String(record.payload, Charsets.UTF_8)
    }

    /** RTD_TEXT payload: `[status byte][IANA language code][UTF-8 or UTF-16BE text]`. */
    private fun decodeTextPayload(payload: ByteArray): String {
        if (payload.isEmpty()) return ""
        val statusByte = payload[0].toInt()
        val isUtf16 = (statusByte and 0x80) != 0
        val langCodeLength = statusByte and 0x3F
        val textStart = 1 + langCodeLength
        if (textStart > payload.size) return ""
        val charset = if (isUtf16) Charsets.UTF_16BE else Charsets.UTF_8
        return String(payload, textStart, payload.size - textStart, charset)
    }
}
