package io.droidmcp.vibration

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class VibratePatternTool(private val context: Context) : McpTool {

    override val name = "vibrate_pattern"
    override val description = "Vibrate the device with a custom pattern of ON/OFF durations"
    override val parameters = listOf(
        ToolParameter("timings", "List of ON/OFF durations in milliseconds (e.g., [100, 50, 100])", ParameterType.ARRAY, required = true),
        ToolParameter("repeat", "Index to repeat from (-1 for no repeat, 0 to restart)", ParameterType.INTEGER, required = false),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        @Suppress("UNCHECKED_CAST")
        val timings = params["timings"] as? List<*>
            ?: return ToolResult.error("timings must be an array")

        val longTimings = timings.mapNotNull { (it as? Number)?.toLong() }
            .toLongArray()

        if (longTimings.isEmpty() || longTimings.size % 2 != 0) {
            return ToolResult.error("timings must contain pairs of ON/OFF durations")
        }

        val repeat = (params["repeat"] as? Number)?.toInt()?.coerceIn(-1, longTimings.size - 1) ?: -1

        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (!vibrator.hasVibrator()) {
            return ToolResult.error("Device does not have a vibrator")
        }

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(longTimings, repeat)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longTimings, repeat)
            }
            ToolResult.success(mapOf("success" to true))
        } catch (e: Exception) {
            ToolResult.error("Failed to vibrate: ${e.message}")
        }
    }
}
