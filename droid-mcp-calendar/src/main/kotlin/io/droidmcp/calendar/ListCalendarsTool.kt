package io.droidmcp.calendar

import android.content.Context
import android.provider.CalendarContract
import io.droidmcp.core.*
import io.droidmcp.core.support.CalendarSupport

/**
 * Lists the device's calendars via `ContentResolver` on `CalendarContract.Calendars`, so a model
 * can pass a `calendar_id` to `create_event`. Requires `READ_CALENDAR`. Output: `calendars` (list
 * of {id, name, account_name, account_type, color, writable, visible, is_default}) and `count`.
 * `writable` means at least contributor access; `is_default` marks the calendar `create_event`
 * uses when `calendar_id` is omitted ([CalendarSupport.findWritableCalendarId]). `color` is
 * `#RRGGBB`. Param `writable_only` (default false) drops read-only calendars such as holidays
 * and birthdays.
 */
class ListCalendarsTool(private val context: Context) : McpTool {

    override val name = "list_calendars"
    override val description = "List the calendars on this device with their ids, accounts and whether events can be added. Pass an id as calendar_id to create_event to use a calendar other than the default."
    override val parameters = listOf(
        ToolParameter("writable_only", "Only return calendars events can be added to. Default false.", ParameterType.BOOLEAN),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)
    override val outputSchema = OutputSchema.obj {
        array("calendars") {
            integer("id")
            string("name", nullable = true)
            string("account_name", nullable = true)
            string("account_type", nullable = true)
            string("color", "#RRGGBB", nullable = true)
            boolean("writable")
            boolean("visible")
            boolean("is_default", "The calendar create_event uses when calendar_id is omitted")
        }
        integer("count")
    }

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val writableOnly = params["writable_only"] as? Boolean ?: false

        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE,
        )
        val defaultId = CalendarSupport.findWritableCalendarId(context)
        val calendars = mutableListOf<Map<String, Any?>>()

        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, null, null,
            "${CalendarContract.Calendars._ID} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
                val accessLevel = cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL))
                val writable = accessLevel >= CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR
                if (writableOnly && !writable) continue
                val colorIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_COLOR)
                calendars.add(mapOf(
                    "id" to id,
                    "name" to cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)),
                    "account_name" to cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)),
                    "account_type" to cursor.getString(cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_TYPE)),
                    "color" to (if (cursor.isNull(colorIndex)) null else CalendarUtils.formatColor(cursor.getInt(colorIndex))),
                    "writable" to writable,
                    "visible" to (cursor.getInt(cursor.getColumnIndexOrThrow(CalendarContract.Calendars.VISIBLE)) == 1),
                    "is_default" to (id == defaultId),
                ))
            }
        }

        return ToolResult.success(mapOf(
            "calendars" to calendars,
            "count" to calendars.size,
        ))
    }
}
