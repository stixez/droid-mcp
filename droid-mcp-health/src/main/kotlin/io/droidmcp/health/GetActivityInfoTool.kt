package io.droidmcp.health

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import io.droidmcp.core.*

class GetActivityInfoTool(private val context: Context) : McpTool {

    override val name = "get_activity_info"
    override val description = "Get basic activity and fitness sensor information for the device. Returns which motion sensors are available (step counter, step detector, accelerometer, etc.) and their specifications. Does not require any permissions."
    override val parameters = emptyList<ToolParameter>()

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
