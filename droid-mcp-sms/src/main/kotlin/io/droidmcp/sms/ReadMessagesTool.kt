package io.droidmcp.sms

import android.content.Context
import android.provider.Telephony
import io.droidmcp.core.*
import java.text.SimpleDateFormat
import java.util.*

class ReadMessagesTool(private val context: Context) : McpTool {

    override val name = "read_messages"
    override val description = "Read SMS messages. Filter by inbox/sent, contact number, or date range."
    override val parameters = listOf(
        ToolParameter("box", "Message box: 'inbox' or 'sent'. Default 'inbox'.", ParameterType.STRING),
        ToolParameter("address", "Filter by phone number", ParameterType.STRING),
        ToolParameter("since", "Only messages after this date (YYYY-MM-DD)", ParameterType.STRING),
        ToolParameter("limit", "Max results. Default 20.", ParameterType.INTEGER),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val box = params["box"]?.toString() ?: "inbox"
        val address = params["address"]?.toString()
        val since = params["since"]?.toString()
        val limit = (params["limit"] as? Number)?.toInt() ?: 20

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
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            try {
                val sinceMillis = dateFormat.parse(since)?.time
                if (sinceMillis != null) {
                    selectionParts.add("${Telephony.Sms.DATE} >= ?")
                    selectionArgs.add(sinceMillis.toString())
                }
            } catch (_: Exception) { }
        }

        val selection = if (selectionParts.isNotEmpty()) selectionParts.joinToString(" AND ") else null
        val args = if (selectionArgs.isNotEmpty()) selectionArgs.toTypedArray() else null
        val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $limit"

        val messages = mutableListOf<Map<String, Any?>>()
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        context.contentResolver.query(uri, null, selection, args, sortOrder)?.use { cursor ->
            while (cursor.moveToNext()) {
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                messages.add(mapOf(
                    "id" to cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID)),
                    "address" to cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)),
                    "body" to cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)),
                    "date" to timeFormat.format(Date(date)),
                    "read" to (cursor.getInt(cursor.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1),
                ))
            }
        }

        return ToolResult.success(mapOf(
            "messages" to messages,
            "count" to messages.size,
            "box" to box,
        ))
    }
}
