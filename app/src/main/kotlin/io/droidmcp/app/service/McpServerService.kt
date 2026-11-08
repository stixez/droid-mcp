package io.droidmcp.app.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import io.droidmcp.app.MainActivity
import io.droidmcp.app.R
import io.droidmcp.app.appGraph
import io.droidmcp.app.data.server.LocalNetwork
import io.droidmcp.app.data.server.ServerStatus
import io.droidmcp.core.DroidMcp
import io.droidmcp.server.DroidMcpServerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Keeps the MCP server alive while the screen is off or the app is closed. The server itself is
 * built by the app-wide ServerController, so this also works when Android restarts the service
 * on its own (START_STICKY) with no activity around.
 */
class McpServerService : DroidMcpServerService() {

    private val controller get() = appGraph.server

    /** Refreshes the notification while the service lives: address changes, pauses. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        controller.onServiceCreated(this)
        scope.launch {
            combine(controller.status, appGraph.networkUpdates) { status, net -> status to net }
                .drop(1)
                .collect { (status, net) -> refreshNotification(status, net) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        // START_NOT_STICKY means it couldn't enter the foreground and is stopping itself.
        if (result != START_NOT_STICKY) controller.onServiceForeground(this)
        return result
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun refreshNotification(status: ServerStatus, net: LocalNetwork) {
        if (status is ServerStatus.Stopped || status is ServerStatus.Failed) return
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification(status, net))
    }

    override fun createServer(): DroidMcp = controller.createServer(owner = this)

    override fun onServerStarted(server: DroidMcp) = controller.onServiceStarted(this, server)

    override fun onServerStartFailed(error: Exception) = controller.onServiceFailed(this, error)

    override fun onServerStopped() = controller.onServiceStopped(this)

    override val smallIconRes: Int get() = R.drawable.ic_stat_server

    override fun buildNotification(): Notification =
        buildNotification(controller.status.value, appGraph.network.current())

    private fun buildNotification(status: ServerStatus, net: LocalNetwork): Notification {
        val running = status as? ServerStatus.Running
        val address = net.address
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getBroadcast(
            this,
            0,
            Intent(this, StopServerReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(smallIconRes)
            .setContentTitle(if (status is ServerStatus.Paused) "droid-mcp is paused" else "droid-mcp is on")
            .setContentText(
                when {
                    status is ServerStatus.Paused -> status.reason.removePrefix("Paused: ").replaceFirstChar { it.uppercase() }
                    running == null -> "Starting the server"
                    address != null -> "Listening on $address:${running.port}"
                    else -> "Running, but the phone has no network"
                },
            )
            .setContentIntent(open)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}

/** "Stop" action on the server notification. */
class StopServerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.appGraph.server.stop()
    }
}
