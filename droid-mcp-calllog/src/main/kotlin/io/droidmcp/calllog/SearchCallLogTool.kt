package io.droidmcp.calllog

import android.content.Context
import android.provider.CallLog
import io.droidmcp.core.*
import java.text.SimpleDateFormat
import java.util.*

class SearchCallLogTool(private val context: Context) : McpTool {

    override val name = "search_call_log"
    override val description = "Search the call log by phone number or contact name. Returns matching call records."
    override val parameters = listOf(
        ToolParameter("query", "Phone number or contact name to search for (substring match)", ParameterType.STRING, required = true),
        ToolParameter("limit", "Max number of results to return. Default 10.", ParameterType.INTEGER),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val query = params["query"]?.toString()
            ?: return ToolResult.error("query is required")
        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 10

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
        )

        val selection = "${CallLog.Calls.NUMBER} LIKE ? OR ${CallLog.Calls.CACHED_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%", "%$query%")
        val sortOrder = "${CallLog.Calls.DATE} DESC LIMIT $limit"

        val calls = mutableListOf<Map<String, Any?>>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI, projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val callType = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                calls.add(mapOf(
                    "id" to cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls._ID)),
                    "number" to cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)),
                    "name" to cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)),
                    "type" to callTypeName(callType),
                    "date" to dateFormat.format(Date(cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)))),
                    "duration_seconds" to cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)),
                ))
            }
        }

        return ToolResult.success(mapOf(
            "query" to query,
            "calls" to calls,
            "count" to calls.size,
        ))
    }

}
