package io.droidmcp.sms

import android.content.Context
import android.provider.Telephony
import io.droidmcp.core.*
import io.droidmcp.core.support.StrictDates
import java.text.SimpleDateFormat
import java.util.*

/**
 * Reads SMS from the inbox or sent box via `ContentResolver` on `Telephony.Sms`, optionally
 * filtered by `address` and `since` (strict `yyyy-MM-dd`; an unparseable value is an error),
 * newest first. `address` matches a raw case-insensitive substring, a digits-only substring after
 * normalizing formatting (`+1 (555) 010-9999` → `+15550109999`), or `PhoneNumberUtils.compare`
 * (tolerates national vs international prefixes). A SQL `LIKE` pre-filter
 * ([SmsUtils.addressPrefilter]) narrows the rows the provider returns, and
 * [SmsUtils.addressMatches] is the precise check in the cursor loop. Requires
 * `READ_SMS`. Output: `messages` (list of {id, address, body, date formatted
 * `yyyy-MM-dd HH:mm`, read}), `count`, and the resolved `box`. `box` is 'inbox'
 * (default) or 'sent'; `limit` clamps to 1–100 (default 10).
 */
class ReadMessagesTool(private val context: Context) : McpTool {

    override val name = "read_messages"
    override val description = "Read SMS messages. Filter by inbox/sent, contact number, or date range."
    override val parameters = listOf(
        ToolParameter("box", "Message box: 'inbox' or 'sent'. Default 'inbox'.", ParameterType.STRING, enumValues = listOf("inbox", "sent")),
        ToolParameter("address", "Filter by phone number", ParameterType.STRING),
        ToolParameter("since", "Only messages after this date (YYYY-MM-DD)", ParameterType.STRING),
        ToolParameter("limit", "Max results. Default 10.", ParameterType.INTEGER, minimum = 1.0, maximum = 100.0),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val box = params["box"]?.toString()?.trim()?.lowercase() ?: "inbox"
        if (box !in setOf("inbox", "sent")) {
            return ToolResult.error("Invalid box '$box'. Use: inbox, sent")
        }
        val address = params["address"]?.toString()?.takeIf { it.isNotBlank() }
        val since = params["since"]?.toString()
        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 10

        val uri = when (box) {
            "sent" -> Telephony.Sms.Sent.CONTENT_URI
            else -> Telephony.Sms.Inbox.CONTENT_URI
        }

        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        // Stored addresses vary in formatting and country prefix ("+1 555-0109" vs "5550109"),
        // which a plain substring LIKE can't reconcile, so SmsUtils.addressMatches decides in the
        // cursor loop. The LIKE pre-filter only narrows the candidates (always a superset of the
        // matches) so a filtered read doesn't pull the whole box through the provider.
        if (address != null) {
            SmsUtils.addressPrefilter(address)?.let {
                selectionParts.add(it.selection)
                selectionArgs.add(it.arg)
            }
        }
        if (since != null) {
            // Previously a bad `since` was silently swallowed, dropping the filter entirely
            // while the caller believed messages were date-filtered — now it's an error.
            val sinceMillis = StrictDates.parse("yyyy-MM-dd", since)?.time
                ?: return ToolResult.error("Invalid since date '$since'. Use format: YYYY-MM-DD")
            selectionParts.add("${Telephony.Sms.DATE} >= ?")
            selectionArgs.add(sinceMillis.toString())
        }

        val selection = if (selectionParts.isNotEmpty()) selectionParts.joinToString(" AND ") else null
        val args = if (selectionArgs.isNotEmpty()) selectionArgs.toTypedArray() else null
        val sortOrder = "${Telephony.Sms.DATE} DESC"

        val messages = mutableListOf<Map<String, Any?>>()
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ,
        )

        context.contentResolver.query(uri, projection, selection, args, sortOrder)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val readIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
            var count = 0
            while (cursor.moveToNext() && count < limit) {
                val rowAddress = cursor.getString(addressIdx)
                if (address != null && !SmsUtils.addressMatches(rowAddress, address)) continue
                messages.add(mapOf(
                    "id" to cursor.getLong(idIdx),
                    "address" to rowAddress,
                    "body" to cursor.getString(bodyIdx),
                    "date" to timeFormat.format(Date(cursor.getLong(dateIdx))),
                    "read" to (cursor.getInt(readIdx) == 1),
                ))
                count++
            }
        }

        return ToolResult.success(mapOf(
            "messages" to messages,
            "count" to messages.size,
            "box" to box,
        ))
    }
}
