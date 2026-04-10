package io.droidmcp.alarms

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class CreateAlarmTool(private val context: Context) : McpTool {

    override val name = "create_alarm"
    override val description = "Create an alarm at the specified time. Note: Reading existing alarms is not supported by a standard Android API — each clock app stores them differently."
    override val parameters = listOf(
        ToolParameter("hour", "Hour of the alarm (0-23)", ParameterType.INTEGER, required = true),
        ToolParameter("minute", "Minute of the alarm (0-59)", ParameterType.INTEGER, required = true),
        ToolParameter("message", "Label/message for the alarm", ParameterType.STRING),
        ToolParameter("days", "Comma-separated days to repeat (e.g. mon,tue,wed). Leave empty for one-time alarm.", ParameterType.STRING),
    )

    private val dayMap = mapOf(
        "mon" to java.util.Calendar.MONDAY,
        "tue" to java.util.Calendar.TUESDAY,
        "wed" to java.util.Calendar.WEDNESDAY,
        "thu" to java.util.Calendar.THURSDAY,
        "fri" to java.util.Calendar.FRIDAY,
        "sat" to java.util.Calendar.SATURDAY,
        "sun" to java.util.Calendar.SUNDAY,
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val hour = (params["hour"] as? Number)?.toInt()
            ?: return ToolResult.error("hour is required")
        val minute = (params["minute"] as? Number)?.toInt()
            ?: return ToolResult.error("minute is required")

        if (hour !in 0..23) return ToolResult.error("hour must be 0-23")
        if (minute !in 0..59) return ToolResult.error("minute must be 0-59")

        val message = params["message"]?.toString()
        val daysStr = params["days"]?.toString()

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (message != null) putExtra(AlarmClock.EXTRA_MESSAGE, message)
            if (!daysStr.isNullOrBlank()) {
                val days = daysStr.split(",")
                    .map { it.trim().lowercase() }
                    .mapNotNull { dayMap[it] }
                    .toIntArray()
                if (days.isNotEmpty()) {
                    putExtra(AlarmClock.EXTRA_DAYS, days.toList() as ArrayList<Int>)
                }
            }
        }

        return try {
            context.startActivity(intent)
            ToolResult.success(mapOf(
                "success" to true,
                "hour" to hour,
                "minute" to minute,
                "message" to (message ?: ""),
                "days" to (daysStr ?: ""),
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to create alarm: ${e.message}")
        }
    }
}
