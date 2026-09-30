package io.droidmcp.vibration

import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Stops any ongoing vibration started by this app (e.g. a looping `vibrate_pattern` with
 * `repeat >= 0`) via [android.os.Vibrator.cancel], and clears the pending auto-cancel from
 * [RepeatingVibrationWatchdog]. Only affects this app's own vibrations. Requires `VIBRATE`.
 *
 * Output key on success: `success` (true). Errors if the device has no vibrator.
 */
class CancelVibrationTool(private val context: Context) : McpTool {

    override val name = "cancel_vibration"
    override val description = "Stop any ongoing vibration started by this app (e.g. a repeating vibrate_pattern)"
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val vibrator = defaultVibrator(context)
        if (vibrator == null || !vibrator.hasVibrator()) {
            return ToolResult.error("Device does not have a vibrator")
        }
        return try {
            RepeatingVibrationWatchdog.disarm()
            vibrator.cancel()
            ToolResult.success(mapOf("success" to true))
        } catch (e: Exception) {
            ToolResult.error("Failed to cancel vibration: ${e.message}")
        }
    }
}
