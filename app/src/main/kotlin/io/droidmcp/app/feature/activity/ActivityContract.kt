package io.droidmcp.app.feature.activity

import io.droidmcp.core.ToolCallAudit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import io.droidmcp.app.core.time.startOfDay

enum class ActivityFilter(val label: String) {
    All("All"),
    Failed("Failed"),
}

/** One row of the feed, keyed by timestamp + name (the audit table has no exposed id). */
data class ActivityItem(
    val key: String,
    val toolName: String,
    val client: String?,
    val timestamp: Long,
    val success: Boolean,
    val error: String?,
    val durationMs: Long,
    /** Pretty-printed arguments, or null when the call had none. */
    val arguments: String?,
) {
    /** The user said no on the phone (or didn't answer); shown apart from real failures. */
    val declined: Boolean get() = !success && error?.startsWith(DECLINED_PREFIX) == true

    companion object {
        const val DECLINED_PREFIX = "tool_call_declined"
    }
}

/** A day heading and its calls. */
data class ActivityDay(val dayStart: Long, val items: List<ActivityItem>)

data class ActivityState(
    val loading: Boolean = true,
    val items: List<ActivityItem> = emptyList(),
    val filter: ActivityFilter = ActivityFilter.All,
    val selected: ActivityItem? = null,
    val confirmClear: Boolean = false,
) {
    val visible: List<ActivityItem>
        get() = if (filter == ActivityFilter.Failed) items.filterNot { it.success } else items

    val days: List<ActivityDay>
        get() = visible.groupBy { startOfDay(it.timestamp) }.map { (day, items) -> ActivityDay(day, items) }

    val failedCount: Int get() = items.count { !it.success }
}

sealed interface ActivityIntent {
    data class SetFilter(val filter: ActivityFilter) : ActivityIntent
    data class Select(val item: ActivityItem) : ActivityIntent
    data object DismissDetail : ActivityIntent
    data object AskClear : ActivityIntent
    data object CancelClear : ActivityIntent
    data object ConfirmClear : ActivityIntent
    data object Export : ActivityIntent
}

sealed interface ActivityEffect {
    data class ShareJson(val json: String) : ActivityEffect
    data class Message(val text: String) : ActivityEffect

    /** Remove exported files, which hold the same sensitive arguments as the log. */
    data object DeleteExports : ActivityEffect
}

private val prettyJson = Json { prettyPrint = true }

/**
 * Pure: audit rows (newest first) to feed items. Two calls can share timestamp, name and duration,
 * so repeats get a counter; counting from the oldest keeps existing keys stable as rows arrive.
 */
fun List<ToolCallAudit>.toItems(): List<ActivityItem> {
    val seen = HashMap<String, Int>()
    return asReversed()
        .map { row ->
            val base = "${row.timestamp}-${row.toolName}-${row.durationMs}"
            val n = (seen[base] ?: 0) + 1
            seen[base] = n
            row.toItem(if (n == 1) base else "$base#$n")
        }
        .asReversed()
}

fun ToolCallAudit.toItem(key: String): ActivityItem = ActivityItem(
    key = key,
    toolName = toolName,
    client = clientLabel,
    timestamp = timestamp,
    success = success,
    error = errorMessage,
    durationMs = durationMs,
    arguments = argumentsJson?.let(::prettyArguments),
)

internal fun prettyArguments(raw: String): String? {
    val parsed = runCatching { Json.parseToJsonElement(raw) }.getOrNull() ?: return raw
    val text = prettyJson.encodeToString(JsonElement.serializer(), parsed)
    return if (text == "{}") null else text
}

