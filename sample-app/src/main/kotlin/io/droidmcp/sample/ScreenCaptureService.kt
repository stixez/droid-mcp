package io.droidmcp.sample

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat
import io.droidmcp.screenshot.MediaProjectionHolder

/**
 * Foreground service of type `mediaProjection` that owns the screen-capture token for
 * `capture_screen`.
 *
 * Since Android 14 (targetSdk 34+), `MediaProjectionManager.getMediaProjection()` throws
 * `SecurityException` unless the caller is running a foreground service of type
 * `mediaProjection` — so the Activity can't obtain the projection itself. Instead it
 * forwards the consent result (code + data) here via [start]; the service enters the
 * foreground FIRST, then obtains the projection and hands it to [MediaProjectionHolder].
 *
 * When the projection stops (user revokes via the system chip / notification, or
 * [stop] is called), the service stops itself.
 */
class ScreenCaptureService : Service() {

    private var projection: MediaProjection? = null

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            projection = null
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must reach the foreground before getMediaProjection() — and promptly, since the
        // service was started with startForegroundService().
        ensureChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("droid-mcp screen capture")
            .setContentText("capture_screen is available to MCP clients")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_RESULT_DATA, Intent::class.java) }
        if (resultCode != Activity.RESULT_OK || data == null) {
            // Restarted without a consent token (or bad extras) — nothing to hold.
            stopSelf()
            return START_NOT_STICKY
        }

        // A new consent replaces any previous projection.
        releaseProjection()
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val newProjection = try {
            manager.getMediaProjection(resultCode, data)
        } catch (e: SecurityException) {
            Log.w(TAG, "getMediaProjection refused", e)
            null
        }
        if (newProjection == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        newProjection.registerCallback(projectionCallback, Handler(Looper.getMainLooper()))
        projection = newProjection
        MediaProjectionHolder.set(newProjection)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseProjection()
        super.onDestroy()
    }

    private fun releaseProjection() {
        val current = projection ?: return
        projection = null
        current.unregisterCallback(projectionCallback)
        if (MediaProjectionHolder.projection === current) {
            MediaProjectionHolder.clear()
        } else {
            current.stop()
        }
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Screen capture", NotificationManager.IMPORTANCE_LOW)
                .apply { description = "Shown while droid-mcp holds a screen-capture token" }
        )
    }

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val CHANNEL_ID = "droid_mcp_screen_capture"
        private const val NOTIFICATION_ID = 0xCA97
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"

        /** Start the service with the consent result from `createScreenCaptureIntent()`. */
        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)
            context.startForegroundService(intent)
        }

        /** Stop the service, releasing the projection. */
        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenCaptureService::class.java))
        }
    }
}
