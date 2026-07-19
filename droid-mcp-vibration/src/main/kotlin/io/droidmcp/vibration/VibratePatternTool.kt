package io.droidmcp.vibration

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Plays a waveform vibration from `timings` — alternating OFF/ON durations in ms, starting with
 * OFF (`timings[0]` is a delay before the first vibration; matches
 * [VibrationEffect.createWaveform]/legacy `Vibrator.vibrate(long[], int)` semantics exactly).
 * Any non-empty length is valid — there is no even/odd requirement. Optional `repeat` index
 * (-1 = no repeat, clamped to bounds). Requires `VIBRATE`.
 *
 * Output key on success: `success` (true). Errors on invalid timings or a device with no vibrator.
 */
class VibratePatternTool(private val context: Context) : McpTool {

    override val name = "vibrate_pattern"
    override val description = "Vibrate the device with a custom pattern of alternating OFF/ON durations"
    override val parameters = listOf(
        ToolParameter("timings", "Alternating OFF/ON durations in milliseconds, starting with OFF — timings[0] is a delay before the first vibration (e.g. [0, 100, 50, 100] vibrates immediately for 100ms, pauses 50ms, then vibrates 100ms)", ParameterType.ARRAY, required = true),
        ToolParameter("repeat", "Index to repeat from (-1 for no repeat, 0 to restart)", ParameterType.INTEGER, required = false),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        @Suppress("UNCHECKED_CAST")
        val timings = params["timings"] as? List<*>
            ?: return ToolResult.error("timings must be an array")

        val longTimings = timings.mapNotNull { (it as? Number)?.toLong() }
            .toLongArray()

        if (longTimings.isEmpty()) {
            return ToolResult.error("timings must be a non-empty array of alternating OFF/ON durations")
        }
        if (longTimings.any { it < 0 }) {
            return ToolResult.error("timings must not contain negative durations")
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
