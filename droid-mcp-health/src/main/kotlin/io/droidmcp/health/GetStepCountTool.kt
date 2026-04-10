package io.droidmcp.health

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.droidmcp.core.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class GetStepCountTool(private val context: Context) : McpTool {

    override val name = "get_step_count"
    override val description = """
        Get step count data using the device's built-in step counter sensor.
        Returns the total step count accumulated since the device was last rebooted (TYPE_STEP_COUNTER sensor).
        This is not a per-day count — it resets on reboot.
        LIMITATION: For historical daily step data (e.g. steps on a specific date), Health Connect integration
        is required. That is not included in this build. Pass start_date/end_date to document the intent,
        but the current implementation always returns the since-boot total from the hardware sensor.
        Requires ACTIVITY_RECOGNITION permission on Android 10+ (API 29+).
    """.trimIndent()
    override val parameters = listOf(
        ToolParameter("start_date", "Start date (YYYY-MM-DD). Currently informational only — sensor returns since-boot total.", ParameterType.STRING),
        ToolParameter("end_date", "End date (YYYY-MM-DD). Currently informational only — sensor returns since-boot total.", ParameterType.STRING),
    )

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            ?: return ToolResult.error(
                "This device does not have a step counter sensor (TYPE_STEP_COUNTER). " +
                "Step tracking is not available."
            )

        val latch = CountDownLatch(1)
        var stepCount: Float? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                    stepCount = event.values[0]
                    latch.countDown()
                }
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }

        sensorManager.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
        try {
            val received = latch.await(3, TimeUnit.SECONDS)
            if (!received || stepCount == null) {
                return ToolResult.error(
                    "Step counter sensor did not respond within 3 seconds. " +
                    "The sensor may not be active yet. Try again after some movement."
                )
            }
        } finally {
            sensorManager.unregisterListener(listener)
        }

        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        return ToolResult.success(mapOf(
            "steps_since_reboot" to stepCount!!.toLong(),
            "timestamp" to now,
            "note" to "This is the cumulative step count since last device reboot. " +
                      "For per-day historical data, Health Connect integration is needed.",
            "start_date" to params["start_date"],
            "end_date" to params["end_date"],
        ))
    }
}
