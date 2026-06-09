package io.droidmcp.calllog

import android.content.Context
import android.provider.CallLog
import io.droidmcp.core.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Reads recent calls from `CallLog.Calls` via `ContentResolver`, newest first, with optional
 * call-type filtering and cursor-based pagination. Requires `READ_CALL_LOG`. `type` is one of
 * 'all' (default), 'incoming', 'outgoing', 'missed' (any other value returns an error); `limit`
 * clamps to 1–100 (default 10) and `offset` is non-negative (default 0). Output: `calls` (list
 * of {id, number, name (cached display name, may be null), type via [callTypeName], date
 * formatted `yyyy-MM-dd HH:mm`, duration_seconds}), `count`, and the echoed `filter`.
 */
class ReadCallLogTool(private val context: Context) : McpTool {

    override val name = "read_call_log"
    override val description = "Read recent calls from the device call log. Returns phone number, contact name (if available), call type, date, and duration."
    override val parameters = listOf(
        ToolParameter("limit", "Max number of calls to return. Default 10.", ParameterType.INTEGER),
        ToolParameter("type", "Filter by call type: 'all', 'incoming', 'outgoing', 'missed'. Default: 'all'", ParameterType.STRING),
        ToolParameter("offset", "Number of calls to skip for pagination. Default 0.", ParameterType.INTEGER),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val limit = (params["limit"] as? Number)?.toInt()?.coerceIn(1, 100) ?: 10
        val offset = (params["offset"] as? Number)?.toInt()?.coerceAtLeast(0) ?: 0
        val typeFilter = params["type"]?.toString()?.lowercase() ?: "all"

        val callTypeInt = when (typeFilter) {
            "incoming" -> CallLog.Calls.INCOMING_TYPE
            "outgoing" -> CallLog.Calls.OUTGOING_TYPE
            "missed" -> CallLog.Calls.MISSED_TYPE
            "all" -> null
            else -> return ToolResult.error("Invalid type '$typeFilter'. Use: all, incoming, outgoing, missed")
        }

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
        )

        val selection = callTypeInt?.let { "${CallLog.Calls.TYPE} = ?" }
        val selectionArgs = callTypeInt?.let { arrayOf(it.toString()) }
        val sortOrder = "${CallLog.Calls.DATE} DESC"

        val calls = mutableListOf<Map<String, Any?>>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI, projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            var skipped = 0
            var count = 0
            while (cursor.moveToNext()) {
                if (skipped < offset) {
                    skipped++
                    continue
                }
                if (count >= limit) break
                val callType = cursor.getInt(cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE))
                calls.add(mapOf(
                    "id" to cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls._ID)),
                    "number" to cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)),
                    "name" to cursor.getString(cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)),
                    "type" to callTypeName(callType),
                    "date" to dateFormat.format(Date(cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)))),
                    "duration_seconds" to cursor.getLong(cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)),
                ))
                count++
            }
        }

        return ToolResult.success(mapOf(
            "calls" to calls,
            "count" to calls.size,
            "filter" to typeFilter,
        ))
    }

}
