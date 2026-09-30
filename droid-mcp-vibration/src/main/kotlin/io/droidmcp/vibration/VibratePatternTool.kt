package io.droidmcp.vibration

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Plays a waveform vibration from `timings` — alternating OFF/ON durations in ms, starting with
 * OFF (`timings[0]` is a delay before the first vibration; matches
 * [VibrationEffect.createWaveform]/legacy `Vibrator.vibrate(long[], int)` semantics exactly).
 * Any non-empty length is valid — there is no even/odd requirement. Every entry must be a
 * non-negative number (non-numeric entries are rejected, not dropped); each entry is capped at
 * [MAX_TIMING_MS] and the whole pattern must total at most [MAX_PATTERN_MS].
 *
 * Optional `repeat` index (-1 = no repeat, clamped to bounds). A pattern with `repeat >= 0`
 * loops until cancelled: call `cancel_vibration` ([CancelVibrationTool]) to stop it. As a
 * safety net it is automatically cancelled after [MAX_REPEAT_DURATION_MS]. Requires `VIBRATE`.
 *
 * Output keys on success: `success` (true), `total_duration_ms` (one pass of the pattern),
 * `auto_cancel_after_ms` (only when repeating). Errors on invalid timings or a device with no
 * vibrator.
 */
class VibratePatternTool(private val context: Context) : McpTool {

    override val name = "vibrate_pattern"
    override val description = "Vibrate the device with a custom pattern of alternating OFF/ON durations"
    override val parameters = listOf(
        ToolParameter("timings", "Alternating OFF/ON durations in milliseconds, starting with OFF — timings[0] is a delay before the first vibration (e.g. [0, 100, 50, 100] vibrates immediately for 100ms, pauses 50ms, then vibrates 100ms). Each entry ≤ 10000ms, total ≤ 30000ms", ParameterType.ARRAY, required = true, itemsType = ParameterType.INTEGER),
        ToolParameter("repeat", "Index to repeat from (-1 for no repeat, 0 to restart). A repeating pattern loops until cancel_vibration is called (auto-stops after 60s)", ParameterType.INTEGER, required = false),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        @Suppress("UNCHECKED_CAST")
        val timings = params["timings"] as? List<*>
            ?: return ToolResult.error("timings must be an array")

        if (timings.isEmpty()) {
            return ToolResult.error("timings must be a non-empty array of alternating OFF/ON durations")
        }
        val badIndex = timings.indexOfFirst { it !is Number }
        if (badIndex >= 0) {
            return ToolResult.error("timings[$badIndex] is not a number: ${timings[badIndex]}")
        }
        val longTimings = timings.map { (it as Number).toLong() }.toLongArray()

        if (longTimings.any { it < 0 }) {
            return ToolResult.error("timings must not contain negative durations")
        }
        if (longTimings.any { it > MAX_TIMING_MS }) {
            return ToolResult.error("each timing must be at most ${MAX_TIMING_MS}ms")
        }
        val total = longTimings.sum()
        if (total > MAX_PATTERN_MS) {
            return ToolResult.error("total pattern duration ${total}ms exceeds the ${MAX_PATTERN_MS}ms limit")
        }

        val repeat = (params["repeat"] as? Number)?.toInt()?.coerceIn(-1, longTimings.size - 1) ?: -1

        val vibrator = defaultVibrator(context)
        if (vibrator == null || !vibrator.hasVibrator()) {
            return ToolResult.error("Device does not have a vibrator")
        }

        return try {
            RepeatingVibrationWatchdog.disarm()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val effect = VibrationEffect.createWaveform(longTimings, repeat)
                vibrator.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longTimings, repeat)
            }
            val result = mutableMapOf<String, Any>("success" to true, "total_duration_ms" to total)
            if (repeat >= 0) {
                RepeatingVibrationWatchdog.arm(vibrator, MAX_REPEAT_DURATION_MS)
                result["auto_cancel_after_ms"] = MAX_REPEAT_DURATION_MS
            }
            ToolResult.success(result)
        } catch (e: Exception) {
            ToolResult.error("Failed to vibrate: ${e.message}")
        }
    }

    companion object {
        /** Maximum length of a single OFF/ON entry. */
        const val MAX_TIMING_MS = 10_000L

        /** Maximum total length of one pass of the pattern. */
        const val MAX_PATTERN_MS = 30_000L

        /** A repeating pattern is automatically cancelled after this long. */
        const val MAX_REPEAT_DURATION_MS = 60_000L
    }
}
