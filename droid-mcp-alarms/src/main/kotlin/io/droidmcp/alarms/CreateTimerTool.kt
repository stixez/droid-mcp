package io.droidmcp.alarms

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class CreateTimerTool(private val context: Context) : McpTool {

    override val name = "create_timer"
    override val description = "Create a countdown timer"
    override val parameters = listOf(
        ToolParameter("seconds", "Duration of the timer in seconds", ParameterType.INTEGER, required = true),
        ToolParameter("message", "Label/message for the timer", ParameterType.STRING),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val seconds = (params["seconds"] as? Number)?.toInt()
            ?: return ToolResult.error("seconds is required")
        if (seconds <= 0) return ToolResult.error("seconds must be greater than 0")

        val message = params["message"]?.toString()

        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            if (message != null) putExtra(AlarmClock.EXTRA_MESSAGE, message)
        }

        return try {
            context.startActivity(intent)
            ToolResult.success(mapOf(
                "success" to true,
                "seconds" to seconds,
                "message" to (message ?: ""),
            ))
        } catch (e: Exception) {
            ToolResult.error("Failed to create timer: ${e.message}")
        }
    }
}
