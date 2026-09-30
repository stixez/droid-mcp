package io.droidmcp.vibration

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.os.VibratorManager

/** Resolves the device's default [Vibrator] (via [VibratorManager] on API 31+), or null. */
internal fun defaultVibrator(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

/**
 * Safety net for repeating waveforms: `vibrate_pattern` with `repeat >= 0` loops until
 * cancelled, so [arm] schedules an automatic [Vibrator.cancel] after a maximum duration.
 * Any new vibration from this module (or `cancel_vibration`) [disarm]s the pending cancel,
 * since a new vibration replaces the previous one anyway.
 */
internal object RepeatingVibrationWatchdog {
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null

    @Synchronized
    fun arm(vibrator: Vibrator, afterMs: Long) {
        disarm()
        val r = Runnable {
            synchronized(this) { pending = null }
            try { vibrator.cancel() } catch (_: Exception) {}
        }
        pending = r
        handler.postDelayed(r, afterMs)
    }

    @Synchronized
    fun disarm() {
        pending?.let { handler.removeCallbacks(it) }
        pending = null
    }
}
