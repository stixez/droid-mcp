package io.droidmcp.sensors

import android.content.Context
import android.hardware.Sensor
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetAccelerometerTool(private val context: Context) : McpTool {

    override val name = "get_accelerometer"
    override val description = "Read accelerometer data (x, y, z acceleration in m/s\u00B2)"
    override val parameters = listOf(
        ToolParameter("duration_ms", "Duration to collect readings in ms (1-5000, null for single reading)", ParameterType.INTEGER, required = false),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val durationMs = (params["duration_ms"] as? Number)?.toInt()?.coerceIn(1, 5000)

        val readings = readSensor(context, Sensor.TYPE_ACCELEROMETER, durationMs)
            ?: return@withContext ToolResult.error("Accelerometer not available on this device")

        val latest = readings.lastOrNull()

        ToolResult.success(mapOf(
            "x" to (latest?.values?.get(0) ?: 0f),
            "y" to (latest?.values?.get(1) ?: 0f),
            "z" to (latest?.values?.get(2) ?: 0f),
            "accuracy" to (latest?.accuracy ?: 0),
            "timestamp" to (latest?.timestamp ?: System.currentTimeMillis()),
            "readings" to readings.map { mapOf(
                "x" to it.values[0],
                "y" to it.values[1],
                "z" to it.values[2],
                "timestamp" to it.timestamp,
            )}
        ))
    }
}
