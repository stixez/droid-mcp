package io.droidmcp.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import io.droidmcp.core.DroidMcp

/**
 * Foreground service that keeps the MCP HTTP server running across screen-off
 * and aggressive task-killers. Without it, the embedded server is tied to the
 * app process and dies when the activity is backgrounded.
 *
 * Host apps subclass this, supply a configured [DroidMcp] via [createServer],
 * and declare the concrete service in their manifest:
 *
 * ```xml
 * <service
 *     android:name=".McpServerService"
 *     android:exported="false"
 *     android:foregroundServiceType="specialUse">
 *     <property
 *         android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
 *         android:value="mcp_server" />
 * </service>
 * ```
 *
 * Start/stop with [start] / [stop]. On API 33+ the host must hold
 * `POST_NOTIFICATIONS` for the ongoing notification to be visible (the service
 * still runs without it).
 */
abstract class DroidMcpServerService : Service() {

    /**
     * Build and configure the server. Called once, lazily, on first start —
     * after which the same instance is reused until the service is destroyed.
     * The service calls [DroidMcp.startServer] on the returned instance.
     *
     * Runs on a **background thread** (TLS key generation and the Netty bind are too
     * slow for the main thread), so don't touch views from here.
     */
    protected abstract fun createServer(): DroidMcp

    /** Small icon for the ongoing notification. Override to brand it. */
    protected open val smallIconRes: Int = android.R.drawable.stat_notify_sync

    /** Notification title. Override to customize. */
    protected open val notificationTitle: String get() = "MCP server running"

    /** Notification body. Override to customize (e.g. include the port). */
    protected open val notificationText: String get() = "Listening for MCP tool calls"

    private val lock = Any()
    private var server: DroidMcp? = null
    private var starting = false
    private var destroyed = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        try {
            startForegroundCompat(buildNotification())
        } catch (e: Exception) {
            // e.g. a host manifest missing foregroundServiceType="specialUse" on API 34+, or a
            // ForegroundServiceStartNotAllowedException from the background on API 31+.
            Log.e(TAG, "Could not enter the foreground; stopping", e)
            stopSelf()
            return START_NOT_STICKY
        }
        synchronized(lock) {
            if (server != null || starting) return START_STICKY
            starting = true
        }
        // createServer() may generate a TLS key (seconds on first run) and startServer() binds
        // Netty — keep both off the main thread so the service can't ANR the host.
        Thread({ startServerInBackground() }, "droid-mcp-server-start").start()
        return START_STICKY
    }

    private fun startServerInBackground() {
        val created = try {
            createServer().also { it.startServer() }
        } catch (e: Exception) {
            // createServer()/startServer() can fail (port already bound, bad TLS config, etc.).
            // With START_STICKY the system would restart the service into the same failure — a
            // crash-loop — so stop cleanly instead.
            Log.e(TAG, "MCP server failed to start", e)
            synchronized(lock) { starting = false }
            mainHandler.post {
                onServerStartFailed(e)
                stopSelf()
            }
            return
        }
        val stopImmediately = synchronized(lock) {
            starting = false
            if (destroyed) true else { server = created; false }
        }
        // The service was destroyed while we were starting: don't leak a bound server.
        if (stopImmediately) created.stopServer()
    }

    override fun onDestroy() {
        val running = synchronized(lock) {
            destroyed = true
            server.also { server = null }
        }
        running?.stopServer()
        onServerStopped()
        super.onDestroy()
    }

    /**
     * Called on the main thread when [createServer] or `startServer()` throws; the service
     * stops itself right after. Override to surface the error (toast, status flow). Default no-op.
     */
    protected open fun onServerStartFailed(error: Exception) {}

    /**
     * Called after the server is stopped on service destroy. Override to
     * release host-owned resources tied to the server — e.g. `close()` a
     * `RoomAuditSink` so its DB connection and write scope don't leak across
     * service restarts. Default is a no-op.
     */
    protected open fun onServerStopped() {}

    override fun onBind(intent: Intent?): IBinder? = null

    /** Build the ongoing notification. Override for full control. */
    protected open fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setSmallIcon(smallIconRes)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

    private fun startForegroundCompat(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "MCP server",
                NotificationManager.IMPORTANCE_MIN,
            ).apply { description = "Ongoing notification while the MCP server is running" }
        )
    }

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    companion object {
        private const val TAG = "DroidMcpServerService"
        const val CHANNEL_ID: String = "droid_mcp_server"
        const val NOTIFICATION_ID: Int = 0xC0DE

        /** Start a concrete subclass [serviceClass] as a foreground service. */
        fun start(context: Context, serviceClass: Class<out DroidMcpServerService>) {
            val intent = Intent(context, serviceClass)
            context.startForegroundService(intent)
        }

        /** Stop a running server service. */
        fun stop(context: Context, serviceClass: Class<out DroidMcpServerService>) {
            context.stopService(Intent(context, serviceClass))
        }
    }
}
