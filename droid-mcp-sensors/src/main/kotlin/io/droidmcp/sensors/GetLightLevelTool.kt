package io.droidmcp.sensors

import android.content.Context
import android.hardware.Sensor
import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GetLightLevelTool(private val context: Context) : McpTool {

    override val name = "get_light_level"
    override val description = "Read ambient light level in lux"
    override val parameters = listOf(
        ToolParameter("duration_ms", "Duration to collect readings in ms (1-5000, null for single reading)", ParameterType.INTEGER, required = false),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult = withContext(Dispatchers.IO) {
        val durationMs = (params["duration_ms"] as? Number)?.toInt()?.coerceIn(1, 5000)

        val readings = readSensor(context, Sensor.TYPE_LIGHT, durationMs)
            ?: return@withContext ToolResult.error("Light sensor not available on this device")

        val latest = readings.lastOrNull()

        ToolResult.success(mapOf(
            "lux" to (latest?.values?.get(0) ?: 0f),
            "accuracy" to (latest?.accuracy ?: 0),
            "timestamp" to (latest?.timestamp ?: System.currentTimeMillis()),
            "readings" to readings.map { mapOf(
                "lux" to it.values[0],
                "timestamp" to it.timestamp,
            )}
        ))
    }
}
