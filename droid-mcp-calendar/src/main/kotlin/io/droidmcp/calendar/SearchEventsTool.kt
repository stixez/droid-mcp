package io.droidmcp.calendar

import android.content.Context
import android.provider.CalendarContract
import io.droidmcp.core.*
import java.text.SimpleDateFormat
import java.util.*

class SearchEventsTool(private val context: Context) : McpTool {

    override val name = "search_events"
    override val description = "Search calendar events by keyword in title or description"
    override val parameters = listOf(
        ToolParameter("query", "Search keyword", ParameterType.STRING, required = true),
        ToolParameter("limit", "Max results. Default 20.", ParameterType.INTEGER),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val query = params["query"]?.toString()
            ?: return ToolResult.error("query is required")
        val limit = (params["limit"] as? Number)?.toInt() ?: 20

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION,
        )

        val selection = "${CalendarContract.Events.TITLE} LIKE ? OR ${CalendarContract.Events.DESCRIPTION} LIKE ?"
        val selectionArgs = arrayOf("%$query%", "%$query%")
        val sortOrder = "${CalendarContract.Events.DTSTART} DESC LIMIT $limit"

        val events = mutableListOf<Map<String, Any?>>()
        val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI, projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val dtStart = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART))
                val dtEnd = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND))
                events.add(mapOf(
                    "id" to cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)),
                    "title" to cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)),
                    "start" to timeFormat.format(Date(dtStart)),
                    "end" to timeFormat.format(Date(dtEnd)),
                    "location" to cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)),
                    "description" to cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Events.DESCRIPTION)),
                ))
            }
        }

        return ToolResult.success(mapOf(
            "events" to events,
            "count" to events.size,
            "query" to query,
        ))
    }
}
