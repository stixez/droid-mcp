package io.droidmcp.calendar

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Deletes the `CalendarContract.Events` row identified by `event_id`. Requires `WRITE_CALENDAR`
 * (plus `READ_CALENDAR` for the lookup). For a recurring event the id names the whole series,
 * so **every occurrence is deleted**, not a single instance.
 *
 * Errors when the event doesn't exist or is already marked deleted (so a repeated call reports
 * not-found rather than succeeding twice), or when its calendar is read-only
 * (`CALENDAR_ACCESS_LEVEL` below `CAL_ACCESS_CONTRIBUTOR`). On a synced calendar the provider
 * marks the row `DELETED` and the sync adapter removes it on its next sync.
 *
 * Output: `event_id`, `deleted` (true), the deleted event's `title`, and `was_recurring`.
 */
class DeleteEventTool(private val context: Context) : McpTool {

    override val name = "delete_event"
    override val description = "Delete a calendar event by ID. For a recurring event this deletes the entire series " +
        "(every occurrence), not just one instance."
    override val parameters = listOf(
        ToolParameter("event_id", "ID of the event to delete (the `id` from read_calendar or search_events)", ParameterType.INTEGER, required = true, minimum = 1.0),
    )
    override val annotations = ToolAnnotations(destructiveHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val eventId = CalendarUtils.parseId(params["event_id"])
            ?: return ToolResult.error("event_id is required and must be a positive number")

        val event = CalendarUtils.findEvent(context, eventId)
            ?: return ToolResult.error("Event not found with ID: $eventId")
        if (event.accessLevel < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) {
            return ToolResult.error("Event $eventId is in a read-only calendar and cannot be deleted")
        }

        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val rows = try {
            context.contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
            return ToolResult.error("Failed to delete event $eventId: ${e.message}")
        }
        if (rows == 0) return ToolResult.error("Failed to delete event $eventId")

        return ToolResult.success(mapOf(
            "event_id" to eventId,
            "deleted" to true,
            "title" to event.title,
            "was_recurring" to event.recurring,
        ))
    }
}
