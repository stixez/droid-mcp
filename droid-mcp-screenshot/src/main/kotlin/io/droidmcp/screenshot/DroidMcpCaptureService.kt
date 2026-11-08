package io.droidmcp.screenshot

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.IntentCompat

/**
 * Foreground service of type `mediaProjection` that holds the screen-capture token for
 * `capture_screen` and hands it to [MediaProjectionHolder].
 *
 * Since Android 14 (targetSdk 34+), `MediaProjectionManager.getMediaProjection()` throws
 * `SecurityException` unless the caller runs a `mediaProjection` foreground service, so an
 * Activity can't create the projection itself. It forwards the consent result instead:
 *
 * ```kotlin
 * val consent = registerForActivityResult(StartActivityForResult()) { result ->
 *     val data = result.data
 *     if (result.resultCode == RESULT_OK && data != null) {
 *         DroidMcpCaptureService.start(context, DroidMcpCaptureService::class.java, result.resultCode, data)
 *     }
 * }
 * consent.launch(DroidMcpCaptureService.consentIntent(context))
 * ```
 *
 * The service enters the foreground first, then creates the projection. It stops itself when the
 * projection ends (the user taps Stop in the status bar, or [stop] is called), and a new consent
 * replaces the previous projection.
 *
 * Declare it in the host manifest, either this class directly or a subclass that changes the
 * notification or listens through [onProjectionStarted] / [onProjectionStopped]:
 *
 * ```xml
 * <service
 *     android:name="io.droidmcp.screenshot.DroidMcpCaptureService"
 *     android:exported="false"
 *     android:foregroundServiceType="mediaProjection" />
 * ```
 */
open class DroidMcpCaptureService : Service() {

    /** Small icon for the ongoing notification. Override to brand it. */
    protected open val smallIconRes: Int = android.R.drawable.ic_menu_camera

    /** Notification title. Override to customize. */
    protected open val notificationTitle: String get() = "Screen capture is on"

    /** Notification body. Override to customize. */
    protected open val notificationText: String get() = "MCP clients can capture the screen until you stop it"

    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Must reach the foreground before getMediaProjection(), and promptly, since the service
        // was started with startForegroundService().
        ensureChannel()
        try {
            // Typed foreground services start at Android 10; before that the type is not needed.
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION else 0
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), type)
        } catch (e: Exception) {
            // e.g. a manifest without foregroundServiceType="mediaProjection".
            Log.e(TAG, "Could not enter the foreground; stopping", e)
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.let { IntentCompat.getParcelableExtra(it, EXTRA_RESULT_DATA, Intent::class.java) }
        if (resultCode != Activity.RESULT_OK || data == null) {
            // Restarted without a consent token, or bad extras: nothing to hold.
            stopSelf()
            return START_NOT_STICKY
        }

        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val created = try {
            manager.getMediaProjection(resultCode, data)
        } catch (e: SecurityException) {
            Log.w(TAG, "getMediaProjection refused", e)
            null
        }
        if (created == null) {
            // Keep a projection from an earlier consent working; only stop when there is none.
            if (projection == null) stopSelf()
            return START_NOT_STICKY
        }
        // The new consent replaces the previous projection, now that it is known to work.
        releaseProjection()
        val callback = object : MediaProjection.Callback() {
            override fun onStop() {
                // The system ended it (status-bar Stop, or another projection took over). Ignore a
                // late stop of a projection already replaced. MediaProjectionHolder clears itself.
                if (projection !== created) return
                projection = null
                projectionCallback = null
                stopSelf()
            }
        }
        created.registerCallback(callback, Handler(Looper.getMainLooper()))
        projectionCallback = callback
        projection = created
        MediaProjectionHolder.set(created)
        onProjectionStarted(created)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        releaseProjection()
        onProjectionStopped()
        super.onDestroy()
    }

    /**
     * Called on the main thread once the projection is in [MediaProjectionHolder], so
     * `capture_screen` works. Override to update your UI or permission state. Default no-op.
     */
    protected open fun onProjectionStarted(projection: MediaProjection) {}

    /**
     * Called on the main thread when the service is destroyed and the projection released, so
     * `capture_screen` needs consent again. Override to update your UI. Default no-op.
     */
    protected open fun onProjectionStopped() {}

    /** Build the ongoing notification. Override for full control; use [CHANNEL_ID]. */
    protected open fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setSmallIcon(smallIconRes)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    private fun releaseProjection() {
        val current = projection ?: return
        projection = null
        projectionCallback?.let(current::unregisterCallback)
        projectionCallback = null
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
                .apply { description = "Shown while the app holds a screen-capture token" },
        )
    }

    companion object {
        private const val TAG = "DroidMcpCaptureService"
        private const val EXTRA_RESULT_CODE = "io.droidmcp.screenshot.RESULT_CODE"
        private const val EXTRA_RESULT_DATA = "io.droidmcp.screenshot.RESULT_DATA"

        const val CHANNEL_ID: String = "droid_mcp_screen_capture"
        const val NOTIFICATION_ID: Int = 0xCA97

        /**
         * The screen-capture consent intent to launch for a result. On Android 14+ it asks for the
         * whole screen: the system's default there is sharing a single app, which `capture_screen`
         * can't use once that app leaves the screen.
         */
        fun consentIntent(context: Context): Intent {
            val manager = context.getSystemService(MediaProjectionManager::class.java)
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                manager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForDefaultDisplay())
            } else {
                manager.createScreenCaptureIntent()
            }
        }

        /**
         * Start [serviceClass] with the consent result from [consentIntent]
         * ([resultCode] and [data] from the activity result).
         */
        fun start(context: Context, serviceClass: Class<out DroidMcpCaptureService>, resultCode: Int, data: Intent) {
            val intent = Intent(context, serviceClass)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)
            context.startForegroundService(intent)
        }

        /** Stop [serviceClass], releasing the projection. */
        fun stop(context: Context, serviceClass: Class<out DroidMcpCaptureService>) {
            context.stopService(Intent(context, serviceClass))
        }
    }
}
