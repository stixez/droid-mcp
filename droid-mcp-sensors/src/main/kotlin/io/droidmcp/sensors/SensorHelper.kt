package io.droidmcp.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * A single sensor sample captured by [readSensor].
 *
 * @property values defensively-cloned raw sensor values (e.g. x/y/z, or lux/distance in
 *   `values[0]`); meaning depends on the sensor type.
 * @property accuracy the [android.hardware.SensorManager] accuracy code at sample time.
 * @property timestamp the event timestamp in nanoseconds since boot (`SensorEvent.timestamp`),
 *   not wall-clock time.
 */
data class SensorReading(
    val values: FloatArray,
    val accuracy: Int,
    val timestamp: Long,
)

/**
 * Registers a one-shot listener on the default sensor of [sensorType] and collects readings.
 *
 * When [durationMs] is null, returns after the first sample (single-reading mode); otherwise
 * collects until roughly [durationMs] (clamped 1-5000) has elapsed. The whole operation is
 * bounded by an overall timeout of [durationMs] + 500 ms; on timeout, whatever was collected
 * is lost and `null` is returned (indistinguishable from the no-sensor case at this layer).
 *
 * @param context used to resolve [android.hardware.SensorManager].
 * @param sensorType a `Sensor.TYPE_*` constant.
 * @param durationMs sampling window in ms, or null for a single reading.
 * @return the collected [SensorReading]s, or `null` if the sensor is absent or it times out.
 */
suspend fun readSensor(
    context: Context,
    sensorType: Int,
    durationMs: Int? = null,
): List<SensorReading>? {
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val sensor = sensorManager.getDefaultSensor(sensorType) ?: return null

    val clampedDuration = durationMs?.coerceIn(1, 5000) ?: 200

    return withTimeoutOrNull(clampedDuration.toLong() + 500L) {
        suspendCancellableCoroutine { continuation ->
            val readings = mutableListOf<SensorReading>()
            val startTime = System.currentTimeMillis()

            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    event ?: return
                    readings.add(
                        SensorReading(
                            values = event.values.clone(),
                            accuracy = event.accuracy,
                            timestamp = event.timestamp,
                        )
                    )

                    val elapsed = System.currentTimeMillis() - startTime
                    if (durationMs == null && readings.isNotEmpty()) {
                        // Single reading mode
                        sensorManager.unregisterListener(this)
                        continuation.resume(readings.toList())
                    } else if (durationMs != null && elapsed >= durationMs) {
                        sensorManager.unregisterListener(this)
                        continuation.resume(readings.toList())
                    }
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }

            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)

            continuation.invokeOnCancellation {
                sensorManager.unregisterListener(listener)
            }
        }
    }
}
