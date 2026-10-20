package io.droidmcp.core

import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Ready-made prompts built on the standard tool modules. Add them with
 * [DroidMcp.Builder.addBuiltInPrompts]. Each one is listed only while the tools it names are
 * registered and enabled (see [McpPrompt.requiredTools]), so hosts can add all of them safely.
 */
object BuiltInPrompts {

    fun all(): List<McpPrompt> = listOf(DailyBriefing, TriageNotifications, MessageContact, DeviceHealthCheck)

    /** Today's (or a given day's) calendar, notifications and messages, summarised. */
    object DailyBriefing : McpPrompt {
        override val name = "daily_briefing"
        override val title = "Daily briefing"
        override val description = "Summarise a day: calendar events, unread notifications and recent messages."
        override val arguments = listOf(
            PromptArgument("date", "Day to brief on, as yyyy-MM-dd. Defaults to today."),
        )
        override val requiredTools = setOf("read_calendar", "get_active_notifications")

        override suspend fun render(arguments: Map<String, String>): PromptResult {
            val date = arguments["date"]?.takeIf { it.isNotBlank() }?.let(::parseDate) ?: LocalDate.now()
            return PromptResult(
                description = "Briefing for $date",
                messages = listOf(
                    PromptMessage(
                        PromptMessage.Role.USER,
                        """
                        Give me a short briefing for $date.
                        1. Call read_calendar with start_date "$date" and list the events in time order, flagging overlaps.
                        2. Call get_active_notifications and group what needs my attention by app.
                        3. If read_messages is available, call it with since "$date" and mention anything that looks like it needs a reply.
                        Keep it under 15 lines and lead with the most time-sensitive item.
                        """.trimIndent(),
                    ),
                ),
            )
        }
    }

    /** Sort active notifications into act-now / later / ignore. */
    object TriageNotifications : McpPrompt {
        override val name = "triage_notifications"
        override val title = "Triage notifications"
        override val description = "Sort the current notifications into act now, later and safe to ignore."
        override val requiredTools = setOf("get_active_notifications")

        override suspend fun render(arguments: Map<String, String>): PromptResult = PromptResult(
            listOf(
                PromptMessage(
                    PromptMessage.Role.USER,
                    """
                    Call get_active_notifications with limit 100, then sort every notification into three groups:
                    "Act now", "Later" and "Safe to ignore". Give each a one-line reason.
                    Don't dismiss or reply to anything unless I ask.
                    """.trimIndent(),
                ),
            ),
        )
    }

    /** Find a contact and draft (not send) a text to them. */
    object MessageContact : McpPrompt {
        override val name = "message_contact"
        override val title = "Message a contact"
        override val description = "Look up a contact and draft a text message to them, sending only after confirmation."
        override val arguments = listOf(
            PromptArgument("contact", "Name (or part of it) of the person to message.", required = true),
            PromptArgument("intent", "What the message should say or achieve.", required = true),
        )
        override val requiredTools = setOf("search_contacts", "send_message")

        override suspend fun render(arguments: Map<String, String>): PromptResult {
            val contact = arguments.getValue("contact")
            val intent = arguments.getValue("intent")
            return PromptResult(
                listOf(
                    PromptMessage(
                        PromptMessage.Role.USER,
                        """
                        I want to text "$contact". The message should: $intent
                        1. Call search_contacts with query "$contact". If several people match, ask me which one.
                        2. Draft the message and show it to me with the number it will go to.
                        3. Only after I confirm, call send_message with that number and the final text.
                        """.trimIndent(),
                    ),
                ),
            )
        }
    }

    /** Battery, storage and connectivity in one report. */
    object DeviceHealthCheck : McpPrompt {
        override val name = "device_health_check"
        override val title = "Device health check"
        override val description = "Report battery, storage and connectivity, and suggest fixes for anything off."
        override val requiredTools = setOf("get_battery_info", "get_storage_info", "get_connectivity")

        override suspend fun render(arguments: Map<String, String>): PromptResult = PromptResult(
            listOf(
                PromptMessage(
                    PromptMessage.Role.USER,
                    """
                    Check this phone's health: call get_battery_info, get_storage_info and get_connectivity.
                    Report each in one line, then list concrete suggestions for anything that looks wrong
                    (battery under 20% or hot, storage over 90% full, no network).
                    """.trimIndent(),
                ),
            ),
        )
    }

    private fun parseDate(value: String): LocalDate = try {
        LocalDate.parse(value)
    } catch (e: DateTimeParseException) {
        throw IllegalArgumentException("date must be yyyy-MM-dd, got '$value'")
    }
}
