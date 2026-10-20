package io.droidmcp.health

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import io.droidmcp.core.*

/**
 * Reports which motion/fitness sensors the device exposes (step counter, step detector,
 * accelerometer, gyroscope, heart rate, significant motion) and their specs. No permissions
 * required (this only enumerates hardware; it does not read sensor values). Output:
 * `has_step_counter`, `has_heart_rate_sensor`, `note`, and `sensors` — a list of per-sensor
 * maps (`key`, `available`, plus `name`/`vendor`/`version`/`max_range`/`resolution`/`power_ma`
 * when present).
 */
class GetActivityInfoTool(private val context: Context) : McpTool {

    override val name = "get_activity_info"
    override val description = "List which motion and fitness sensors the device has (step counter, heart rate, accelerometer, …) and their specs."
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)
    override val outputSchema = OutputSchema.obj {
        boolean("has_step_counter")
        boolean("has_heart_rate_sensor")
        array("sensors") {
            string("key")
            boolean("available")
            string("name", "Set when available", optional = true)
            string("vendor", optional = true)
            integer("version", optional = true)
            number("max_range", optional = true)
            number("resolution", optional = true)
            number("power_ma", optional = true)
        }
        string("note")
    }

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val sensorsOfInterest = listOf(
            Sensor.TYPE_STEP_COUNTER to "step_counter",
            Sensor.TYPE_STEP_DETECTOR to "step_detector",
            Sensor.TYPE_ACCELEROMETER to "accelerometer",
            Sensor.TYPE_GYROSCOPE to "gyroscope",
            Sensor.TYPE_HEART_RATE to "heart_rate",
            Sensor.TYPE_SIGNIFICANT_MOTION to "significant_motion",
        )

        val sensorInfo = sensorsOfInterest.map { (type, key) ->
            val sensor = sensorManager.getDefaultSensor(type)
            if (sensor != null) {
                mapOf(
                    "key" to key,
                    "available" to true,
                    "name" to sensor.name,
                    "vendor" to sensor.vendor,
                    "version" to sensor.version,
                    "max_range" to sensor.maximumRange,
                    "resolution" to sensor.resolution,
                    "power_ma" to sensor.power,
                )
            } else {
                mapOf(
                    "key" to key,
                    "available" to false,
                )
            }
        }

        val hasStepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
        val hasHeartRate = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE) != null

        return ToolResult.success(mapOf(
            "has_step_counter" to hasStepCounter,
            "has_heart_rate_sensor" to hasHeartRate,
            "sensors" to sensorInfo,
            "note" to "For historical health data (steps by day, workouts, etc.), Health Connect integration is required. " +
                      "Use get_step_count for current since-boot step total.",
        ))
    }
}
