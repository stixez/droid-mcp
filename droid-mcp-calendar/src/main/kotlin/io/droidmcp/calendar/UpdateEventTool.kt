package io.droidmcp.calendar

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import java.util.TimeZone

/**
 * Updates an existing `CalendarContract.Events` row identified by `event_id`, writing only the
 * fields the caller supplies (`title`, `description`, `location`, `start`, `end`, `all_day`).
 * Requires `WRITE_CALENDAR` (plus `READ_CALENDAR` for the lookup).
 *
 * Errors when the event doesn't exist (or is already marked deleted), when its calendar is
 * read-only (`CALENDAR_ACCESS_LEVEL` below `CAL_ACCESS_CONTRIBUTOR`), or when no updatable field
 * is given. Times are parsed strictly like [CreateEventTool] (`yyyy-MM-dd HH:mm`, device
 * timezone); all-day events take `yyyy-MM-dd` at UTC midnight with an exclusive end, matching
 * what `read_calendar` returns. When only one of `start`/`end` is given, `end > start` is checked
 * against the event's stored other side (see [CalendarUtils.resolveTimeUpdate]). Changing
 * `all_day` needs both `start` and `end`. Text changes to a recurring event apply to the whole
 * series; changing a recurring event's times is refused, since its length is stored as a
 * `DURATION` rather than an end time.
 *
 * Output: `event_id`, `updated_fields` (the parameter names that were written), and the event's
 * resulting `title`, `start`, `end` (`yyyy-MM-dd HH:mm`, UTC for all-day events; `end` is null
 * for recurring events) and `all_day`.
 */
class UpdateEventTool(private val context: Context) : McpTool {

    override val name = "update_event"
    override val description = "Update an existing calendar event by ID. Only the fields you pass are changed. " +
        "For all-day events use YYYY-MM-DD dates with an exclusive end (the day after the last day). " +
        "Text changes to a recurring event apply to every occurrence; its times cannot be changed."
    override val parameters = listOf(
        ToolParameter("event_id", "ID of the event to update (the `id` from read_calendar or search_events)", ParameterType.INTEGER, required = true, minimum = 1.0),
        ToolParameter("title", "New event title", ParameterType.STRING),
        ToolParameter("description", "New event description (empty string clears it)", ParameterType.STRING),
        ToolParameter("location", "New event location (empty string clears it)", ParameterType.STRING),
        ToolParameter("start", "New start in YYYY-MM-DD HH:mm format (YYYY-MM-DD for all-day events)", ParameterType.STRING),
        ToolParameter("end", "New end in YYYY-MM-DD HH:mm format (YYYY-MM-DD for all-day events, exclusive)", ParameterType.STRING),
        ToolParameter("all_day", "Make the event all-day (true) or timed (false). Changing it requires both start and end.", ParameterType.BOOLEAN),
    )
    override val annotations = ToolAnnotations(destructiveHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val eventId = CalendarUtils.parseId(params["event_id"])
            ?: return ToolResult.error("event_id is required and must be a positive number")
        val title = params["title"]?.toString()
        val description = params["description"]?.toString()
        val location = params["location"]?.toString()
        val start = params["start"]?.toString()
        val end = params["end"]?.toString()
        val allDay = params["all_day"] as? Boolean
        if (params["all_day"] != null && allDay == null) {
            return ToolResult.error("all_day must be true or false")
        }
        if (title == null && description == null && location == null && start == null && end == null && allDay == null) {
            return ToolResult.error("Nothing to update: pass at least one of title, description, location, start, end, all_day")
        }
        if (title != null && title.isBlank()) {
            return ToolResult.error("title must not be blank")
        }

        val event = CalendarUtils.findEvent(context, eventId)
            ?: return ToolResult.error("Event not found with ID: $eventId")
        if (event.accessLevel < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
            return ToolResult.error("Event $eventId is in a read-only calendar and cannot be modified")
        }

        val timeUpdate = CalendarUtils.resolveTimeUpdate(
            currentStart = event.dtStart,
            currentEnd = event.dtEnd,
            currentAllDay = event.allDay,
            start = start,
            end = end,
            allDay = allDay,
        )
        if (timeUpdate is TimeUpdate.Invalid) return ToolResult.error(timeUpdate.message)
        if (timeUpdate is TimeUpdate.Changed && event.recurring) {
            return ToolResult.error(
                "Changing the times of a recurring event is not supported; title, description and location can still be updated",
            )
        }

        val updatedFields = mutableListOf<String>()
        val values = ContentValues().apply {
            title?.let { put(CalendarContract.Events.TITLE, it); updatedFields += "title" }
            description?.let { put(CalendarContract.Events.DESCRIPTION, it); updatedFields += "description" }
            location?.let { put(CalendarContract.Events.EVENT_LOCATION, it); updatedFields += "location" }
            if (timeUpdate is TimeUpdate.Changed) {
                put(CalendarContract.Events.DTSTART, timeUpdate.startMillis)
                put(CalendarContract.Events.DTEND, timeUpdate.endMillis)
                put(CalendarContract.Events.ALL_DAY, if (timeUpdate.allDay) 1 else 0)
                // The provider requires UTC for all-day events; a timed event switched back from
                // all-day gets the device zone its times were parsed in.
                if (timeUpdate.allDay) {
                    put(CalendarContract.Events.EVENT_TIMEZONE, CalendarUtils.UTC.id)
                } else if (event.allDay) {
                    put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                }
            }
        }
        if (start != null) updatedFields += "start"
        if (end != null) updatedFields += "end"
        if (allDay != null) updatedFields += "all_day"

        // all_day equal to the stored value with no other change leaves nothing to write.
        if (values.size() > 0) {
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            val rows = try {
                context.contentResolver.update(uri, values, null, null)
            } catch (e: Exception) {
                return ToolResult.error("Failed to update event $eventId: ${e.message}")
            }
            if (rows == 0) return ToolResult.error("Failed to update event $eventId")
        }

        val (finalStart, finalEnd, finalAllDay) = when (timeUpdate) {
            is TimeUpdate.Changed -> Triple(timeUpdate.startMillis, timeUpdate.endMillis, timeUpdate.allDay)
            else -> Triple(event.dtStart, event.dtEnd, event.allDay)
        }
        return ToolResult.success(mapOf(
            "event_id" to eventId,
            "updated_fields" to updatedFields,
            "title" to (title ?: event.title),
            "start" to CalendarUtils.formatTime(finalStart, finalAllDay),
            "end" to finalEnd?.takeIf { it > 0 }?.let { CalendarUtils.formatTime(it, finalAllDay) },
            "all_day" to finalAllDay,
        ))
    }
}
