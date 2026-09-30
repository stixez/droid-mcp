package io.droidmcp.nfc

import android.content.Context
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.TagLostException
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
 * For NDEF tags it first tries a live read; if the tag has left the field
 * (`TagLostException`) it serves the NDEF snapshot [NfcTagCache] took at
 * discovery time and reports `cached = true`.
 *
 * Output keys: `has_tag`; when a tag is cached, `tag_id` (hex), `is_ndef`, and
 * `tech_list` (non-NDEF tags) or `is_ndef`/`max_size`/`is_writable`/`records`
 * (each record: `tnf`, `type`, `payload`) plus `cached` (true when served from
 * the discovery-time snapshot, false for a live read). When no tag is cached:
 * `has_tag` false plus a `message`.
 */
class ReadNfcTagTool(private val context: Context) : McpTool {

    override val name = "read_nfc_tag"
    override val description = "Read NDEF data from the last scanned NFC tag. Reads live if the tag is still in range, otherwise returns the data captured when it was scanned (cached=true); indicates when no tag has been scanned yet."
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
            val maxSize = ndef.maxSize
            val isWritable = ndef.isWritable
            NfcTagCache.refresh(tag, ndefMessage, maxSize, isWritable)
            ToolResult.success(ndefResult(tag, ndefMessage, maxSize, isWritable, cached = false))
        } catch (e: TagLostException) {
            // The tag left the field (the usual case by the time a tool runs):
            // serve the NDEF snapshot taken when it was discovered.
            val snap = NfcTagCache.snapshot
                ?: return@withContext ToolResult.error("Failed to read NFC tag: tag is out of range and no cached NDEF data is available. Tap the tag again.")
            ToolResult.success(ndefResult(tag, snap.message, snap.maxSize, snap.isWritable, cached = true))
        } catch (e: Exception) {
            ToolResult.error("Failed to read NFC tag: ${e.message}")
        } finally {
            try { ndef.close() } catch (_: Exception) {}
        }
    }

    private fun ndefResult(
        tag: Tag,
        message: NdefMessage?,
        maxSize: Int,
        isWritable: Boolean,
        cached: Boolean,
    ): Map<String, Any?> {
        val records = message?.records?.map { record ->
            mapOf(
                "tnf" to record.tnf,
                "type" to String(record.type),
                "payload" to decodePayload(record),
            )
        } ?: emptyList()
        return mapOf(
            "has_tag" to true,
            "is_ndef" to true,
            "tag_id" to tag.id?.joinToString("") { "%02x".format(it) },
            "max_size" to maxSize,
            "is_writable" to isWritable,
            "records" to records,
            "cached" to cached,
        )
    }

    /**
     * Decodes a record's payload per its actual NDEF type — the payload layout differs by
     * record type, so a single fixed byte offset (the previous implementation's bug) corrupts
     * every record: URI records lose their abbreviated prefix, text records keep the IANA
     * language-code prefix stuck to the text, and MIME/other records shouldn't be trimmed at all.
     */
    private fun decodePayload(record: NdefRecord): String = when {
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
