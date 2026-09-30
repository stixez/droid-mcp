package io.droidmcp.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * A single sensor sample captured by [sampleSensor].
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

/** Outcome of [sampleSensor]: distinguishes a missing sensor from one that stayed silent. */
sealed interface SensorSampleResult {
    /** The device has no default sensor of the requested type (or registration failed). */
    object NoSensor : SensorSampleResult

    /** The sensor exists but delivered no event within the sampling window / timeout. */
    data class NoReading(val waitedMs: Long) : SensorSampleResult

    /** At least one sample was collected. */
    data class Readings(val readings: List<SensorReading>) : SensorSampleResult
}

/** How long single-reading mode waits for the first event before giving up. */
internal const val SINGLE_READING_TIMEOUT_MS = 2_000L

/** Extra grace period after a timed window if no event has arrived yet. */
internal const val WINDOW_GRACE_MS = 500L

/**
 * Registers a listener on the default sensor of [sensorType] and collects readings.
 *
 * When [durationMs] is null, returns as soon as the first sample arrives (waiting at most
 * [SINGLE_READING_TIMEOUT_MS]). Otherwise collects for [durationMs] (clamped 1-5000) measured by
 * a timer — not by incoming events — so on-change sensors (light, proximity) that only report
 * when their value changes still complete on time with whatever was collected. If a timed window
 * elapses with no samples, waits an extra [WINDOW_GRACE_MS] for a first one.
 *
 * The listener is unregistered on every path, including coroutine cancellation.
 *
 * @param context used to resolve [android.hardware.SensorManager].
 * @param sensorType a `Sensor.TYPE_*` constant.
 * @param durationMs sampling window in ms, or null for a single reading.
 */
suspend fun sampleSensor(
    context: Context,
    sensorType: Int,
    durationMs: Int? = null,
): SensorSampleResult {
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        ?: return SensorSampleResult.NoSensor
    val sensor = sensorManager.getDefaultSensor(sensorType) ?: return SensorSampleResult.NoSensor

    val readings = mutableListOf<SensorReading>()
    val firstReading = CompletableDeferred<Unit>()

    val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            event ?: return
            synchronized(readings) {
                readings.add(
                    SensorReading(
                        values = event.values.clone(),
                        accuracy = event.accuracy,
                        timestamp = event.timestamp,
                    )
                )
            }
            firstReading.complete(Unit)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    if (!sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
        return SensorSampleResult.NoSensor
    }

    val waited: Long
    try {
        if (durationMs == null) {
            waited = SINGLE_READING_TIMEOUT_MS
            withTimeoutOrNull(SINGLE_READING_TIMEOUT_MS) { firstReading.await() }
        } else {
            val window = durationMs.coerceIn(1, 5000).toLong()
            delay(window)
            waited = if (firstReading.isCompleted) {
                window
            } else {
                withTimeoutOrNull(WINDOW_GRACE_MS) { firstReading.await() }
                window + WINDOW_GRACE_MS
            }
        }
    } finally {
        sensorManager.unregisterListener(listener)
    }

    val snapshot = synchronized(readings) {
        if (durationMs == null) readings.take(1) else readings.toList()
    }
    return if (snapshot.isEmpty()) {
        SensorSampleResult.NoReading(waited)
    } else {
        SensorSampleResult.Readings(snapshot)
    }
}

/**
 * Backwards-compatible wrapper over [sampleSensor]: returns the readings, or `null` if the sensor
 * is absent or produced no reading in time. Prefer [sampleSensor], which distinguishes the two.
 */
suspend fun readSensor(
    context: Context,
    sensorType: Int,
    durationMs: Int? = null,
): List<SensorReading>? =
    (sampleSensor(context, sensorType, durationMs) as? SensorSampleResult.Readings)?.readings

/**
 * Maps a non-[SensorSampleResult.Readings] outcome to a user-facing error, naming the sensor by
 * [label] (e.g. "Light sensor"). Returns `null` for [SensorSampleResult.Readings].
 */
internal fun SensorSampleResult.toErrorOrNull(label: String): ToolResult? = when (this) {
    SensorSampleResult.NoSensor -> ToolResult.error("$label not available on this device")
    is SensorSampleResult.NoReading -> ToolResult.error(
        "$label is present but reported no reading within ${waitedMs}ms " +
            "(on-change sensors only report when the value changes; try a longer duration_ms)",
    )
    is SensorSampleResult.Readings -> null
}
