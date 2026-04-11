package io.droidmcp.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class SensorReading(
    val values: FloatArray,
    val accuracy: Int,
    val timestamp: Long,
)

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
