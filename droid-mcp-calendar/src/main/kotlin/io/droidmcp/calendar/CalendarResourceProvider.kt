package io.droidmcp.calendar

import android.content.Context
import io.droidmcp.core.CompletionContext
import io.droidmcp.core.McpResource
import io.droidmcp.core.McpResourceTemplate
import io.droidmcp.core.ResourceContents
import io.droidmcp.core.ResourceProvider
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Serves a day's agenda as JSON — the shape `read_calendar` returns, up to 100 events.
 * `droidmcp://calendar/today` is listed so clients can attach it directly; any other day is
 * `droidmcp://calendar/{date}` with a `yyyy-MM-dd` date. Needs `READ_CALENDAR`; without it the
 * listing is empty and reads fail as not found. Hidden while `read_calendar` is gated off.
 */
class CalendarResourceProvider(private val context: Context) : ResourceProvider {

    private val reader = ReadCalendarTool(context)

    override val requiredTools = setOf("read_calendar")

    override val templates = listOf(
        McpResourceTemplate(
            "$PREFIX{date}",
            name = "agenda",
            title = "Agenda for a day",
            description = "Calendar events on a date (yyyy-MM-dd, or 'today'), including recurring ones.",
            mimeType = "application/json",
        ),
    )

    override suspend fun list(): List<McpResource> =
        if (!CalendarTools.hasPermissions(context)) {
            emptyList()
        } else {
            listOf(
                McpResource(
                    uri = "${PREFIX}today",
                    name = "today",
                    title = "Today's agenda",
                    description = "Calendar events for today",
                    mimeType = "application/json",
                ),
            )
        }

    override fun handles(uri: String): Boolean = dateOf(uri) != null

    /** `today`, then the next six days and yesterday as `yyyy-MM-dd`, filtered by what was typed. */
    override suspend fun complete(uriTemplate: String, argument: String, value: String, context: CompletionContext): List<String> {
        if (argument != "date") return emptyList()
        val today = LocalDate.now()
        return (listOf("today") + ((0L..6L).map { today.plusDays(it) } + today.minusDays(1)).map { it.toString() })
            .filter { it.startsWith(value.trim()) }
    }

    override suspend fun read(uri: String): List<ResourceContents>? {
        val date = dateOf(uri) ?: return null
        if (!CalendarTools.hasPermissions(context)) return null
        val result = try {
            reader.execute(mapOf("start_date" to date.toString(), "limit" to 100))
        } catch (_: SecurityException) {
            return null // revoked between the check and the query
        }
        val data = result.data?.takeIf { result.isSuccess } ?: return null
        return listOf(ResourceContents.json(uri, data))
    }

    private fun dateOf(uri: String): LocalDate? {
        if (!uri.startsWith(PREFIX)) return null
        val raw = uri.removePrefix(PREFIX)
        if (raw == "today") return LocalDate.now()
        return try {
            LocalDate.parse(raw)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

private const val PREFIX = "droidmcp://calendar/"
