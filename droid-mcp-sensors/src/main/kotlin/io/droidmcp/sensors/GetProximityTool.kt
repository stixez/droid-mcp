package io.droidmcp.sensors

import android.content.Context
import android.hardware.Sensor
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the `TYPE_PROXIMITY` sensor. No permissions. Many devices report only binary
 * near/far rather than a true centimetre distance. With `duration_ms` (1-5000) it collects a
 * series of samples; otherwise a single reading. See [sampleSensor]. Output: latest
 * `distance_cm`, `is_near` (`distance < 5cm`), `accuracy`, `timestamp`, plus a `readings`
 * list of `{distance_cm,is_near,timestamp}`. Returns [ToolResult.error] (with distinct messages) if no reading arrives in time or when the device has
 * no proximity sensor.
 */
class GetProximityTool(private val context: Context) : McpTool {

    override val name = "get_proximity"
    override val description = "Read proximity sensor distance (typically 0-5cm for near/far)"
    override val parameters = listOf(
        ToolParameter("duration_ms", "Duration to collect readings in ms (1-5000, null for single reading)", ParameterType.INTEGER, required = false, minimum = 1.0, maximum = 5000.0),
    )
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val durationMs = (params["duration_ms"] as? Number)?.toInt()?.coerceIn(1, 5000)

        val result = sampleSensor(context, Sensor.TYPE_PROXIMITY, durationMs)
        result.toErrorOrNull("Proximity sensor")?.let { return@withContext it }
        val readings = (result as SensorSampleResult.Readings).readings

        val latest = readings.lastOrNull()
        val distance = latest?.values?.get(0) ?: Float.MAX_VALUE

        ToolResult.success(mapOf(
            "distance_cm" to distance,
            "is_near" to (distance < 5f),
            "accuracy" to (latest?.accuracy ?: 0),
            "timestamp" to (latest?.timestamp ?: System.currentTimeMillis()),
            "readings" to readings.map { mapOf(
                "distance_cm" to it.values[0],
                "is_near" to (it.values[0] < 5f),
                "timestamp" to it.timestamp,
            )}
        ))
    }
}
