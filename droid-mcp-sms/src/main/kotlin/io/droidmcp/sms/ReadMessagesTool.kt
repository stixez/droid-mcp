package io.droidmcp.sms

import android.content.Context
import android.provider.Telephony
import io.droidmcp.core.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Reads SMS from the inbox or sent box via `ContentResolver` on `Telephony.Sms`, optionally
 * filtered by `address` (LIKE substring) and `since` (`yyyy-MM-dd`, kept only if it parses),
 * newest first. Requires `READ_SMS`. Output: `messages` (list of {id, address, body, date
 * formatted `yyyy-MM-dd HH:mm`, read}), `count`, and the resolved `box`. `box` is 'inbox'
 * (default) or 'sent'; `limit` clamps to 1–100 (default 10).
 */
class ReadMessagesTool(private val context: Context) : McpTool {

    override val name = "read_messages"
    override val description = "Read SMS messages. Filter by inbox/sent, contact number, or date range."
    override val parameters = listOf(
        ToolParameter("box", "Message box: 'inbox' or 'sent'. Default 'inbox'.", ParameterType.STRING),
        ToolParameter("address", "Filter by phone number", ParameterType.STRING),
        ToolParameter("since", "Only messages after this date (YYYY-MM-DD)", ParameterType.STRING),
        ToolParameter("limit", "Max results. Default 10.", ParameterType.INTEGER),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val box = params["box"]?.toString() ?: "inbox"
        if (box !in setOf("inbox", "sent")) {
            return ToolResult.error("Invalid box '$box'. Use: inbox, sent")
        }
        val address = params["address"]?.toString()
        val since = params["since"]?.toString()
        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 10

        val uri = when (box) {
            "sent" -> Telephony.Sms.Sent.CONTENT_URI
            else -> Telephony.Sms.Inbox.CONTENT_URI
        }

        val selectionParts = mutableListOf<String>()
        val selectionArgs = mutableListOf<String>()

        if (address != null) {
            selectionParts.add("${Telephony.Sms.ADDRESS} LIKE ?")
            selectionArgs.add("%$address%")
        }
        if (since != null) {
            // Previously a bad `since` was silently swallowed, dropping the filter entirely
            // while the caller believed messages were date-filtered — now it's an error.
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val sinceMillis = try {
                dateFormat.parse(since)?.time
            } catch (e: Exception) {
                null
            } ?: return ToolResult.error("Invalid since date '$since'. Use format: YYYY-MM-DD")
            selectionParts.add("${Telephony.Sms.DATE} >= ?")
            selectionArgs.add(sinceMillis.toString())
        }

        val selection = if (selectionParts.isNotEmpty()) selectionParts.joinToString(" AND ") else null
        val args = if (selectionArgs.isNotEmpty()) selectionArgs.toTypedArray() else null
        val sortOrder = "${Telephony.Sms.DATE} DESC"

        val messages = mutableListOf<Map<String, Any?>>()
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        context.contentResolver.query(uri, null, selection, args, sortOrder)?.use { cursor ->
            var count = 0
            while (cursor.moveToNext() && count < limit) {
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                messages.add(mapOf(
                    "id" to cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID)),
                    "address" to cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)),
                    "body" to cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)),
                    "date" to timeFormat.format(Date(date)),
                    "read" to (cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1),
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
