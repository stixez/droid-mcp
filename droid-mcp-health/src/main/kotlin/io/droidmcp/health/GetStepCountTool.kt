package io.droidmcp.health

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.droidmcp.core.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.resume

/**
 * Reads the `TYPE_STEP_COUNTER` sensor and returns the cumulative step total since the last
 * reboot (not a per-day count). Registers a one-shot listener with a 3 s timeout. Requires
 * `ACTIVITY_RECOGNITION` on Android 10+ (API 29+); `execute()` checks
 * [HealthTools.hasPermissions] first and returns an explicit permission error rather than
 * letting a missing grant surface as a sensor timeout. Output: `steps_since_reboot`,
 * `timestamp`, `note`. Returns [ToolResult.error] when the permission is missing, no
 * step-counter sensor exists, the listener can't be registered, or the sensor does not report
 * within 3 s.
 */
class GetStepCountTool(private val context: Context) : McpTool {

    override val name = "get_step_count"
    override val description = """
        Get step count data using the device's built-in step counter sensor.
        Returns the total step count accumulated since the device was last rebooted (TYPE_STEP_COUNTER sensor).
        This is not a per-day count — it resets on reboot.
        Requires ACTIVITY_RECOGNITION permission on Android 10+ (API 29+).
    """.trimIndent()
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        if (!HealthTools.hasPermissions(context)) {
            return ToolResult.error(
                "ACTIVITY_RECOGNITION permission not granted. It is required on Android 10+ to read the step counter."
            )
        }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            ?: return ToolResult.error(
                "This device does not have a step counter sensor (TYPE_STEP_COUNTER). " +
                "Step tracking is not available."
            )

        var registered = true
        val steps = withTimeoutOrNull(3000) {
            suspendCancellableCoroutine<Float?> { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        sensorManager.unregisterListener(this)
                        if (cont.isActive) cont.resume(event.values[0])
                    }
                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
                }
                if (!sensorManager.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_UI)) {
                    // Registration refused (sensor unavailable / permission revoked mid-call):
                    // fail now instead of waiting out the timeout.
                    registered = false
                    cont.resume(null)
                    return@suspendCancellableCoroutine
                }
                cont.invokeOnCancellation { sensorManager.unregisterListener(listener) }
            }
        }

        if (!registered) {
            return ToolResult.error("Failed to register a listener on the step counter sensor.")
        }

        if (steps == null) {
            return ToolResult.error(
                "Step counter sensor did not respond within 3 seconds. " +
                "The sensor may not be active yet. Try again after some movement."
            )
        }

        val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())

        return ToolResult.success(mapOf(
            "steps_since_reboot" to steps.toLong(),
            "timestamp" to now,
            "note" to "This is the cumulative step count since last device reboot. " +
                      "For per-day historical data, Health Connect integration is needed.",
        ))
    }
}
