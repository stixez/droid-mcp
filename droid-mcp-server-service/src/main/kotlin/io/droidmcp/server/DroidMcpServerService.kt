package io.droidmcp.server

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
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
     */
    protected abstract fun createServer(): DroidMcp

    /** Small icon for the ongoing notification. Override to brand it. */
    protected open val smallIconRes: Int = android.R.drawable.stat_notify_sync

    /** Notification title. Override to customize. */
    protected open val notificationTitle: String get() = "MCP server running"

    /** Notification body. Override to customize (e.g. include the port). */
    protected open val notificationText: String get() = "Listening for MCP tool calls"

    @Volatile
    private var server: DroidMcp? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        startForegroundCompat(buildNotification())
        if (server == null) {
            server = createServer().also { it.startServer() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        server?.stopServer()
        server = null
        onServerStopped()
        super.onDestroy()
    }

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

    companion object {
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
