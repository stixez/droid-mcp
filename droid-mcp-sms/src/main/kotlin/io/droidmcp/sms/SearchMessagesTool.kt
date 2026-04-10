package io.droidmcp.sms

import android.content.Context
import android.provider.Telephony
import io.droidmcp.core.*
import java.text.SimpleDateFormat
import java.util.*

class SearchMessagesTool(private val context: Context) : McpTool {

    override val name = "search_messages"
    override val description = "Search SMS messages by keyword in message body"
    override val parameters = listOf(
        ToolParameter("query", "Search keyword", ParameterType.STRING, required = true),
        ToolParameter("limit", "Max results. Default 20.", ParameterType.INTEGER),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val query = params["query"]?.toString()
            ?: return ToolResult.error("query is required")
        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 20

        val selection = "${Telephony.Sms.BODY} LIKE ?"
        val selectionArgs = arrayOf("%$query%")
        val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $limit"

        val messages = mutableListOf<Map<String, Any?>>()
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI, null, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val date = cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms.DATE))
                messages.add(mapOf(
                    "id" to cursor.getLong(cursor.getColumnIndexOrThrow(Telephony.Sms._ID)),
                    "address" to cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)),
                    "body" to cursor.getString(cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)),
                    "date" to timeFormat.format(Date(date)),
                ))
            }
        }

        return ToolResult.success(mapOf(
            "messages" to messages,
            "count" to messages.size,
            "query" to query,
        ))
    }
}
