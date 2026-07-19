package io.droidmcp.sample

import android.content.Intent
import io.droidmcp.core.DroidMcp
import io.droidmcp.server.DroidMcpServerService

/**
 * Process-wide handle to the configured [DroidMcp] so the foreground service can
 * pick it up. The ViewModel builds and owns the instance (it drives in-process
 * tool calls, live gating, and the audit sink); the service only needs a
 * reference to run its HTTP listener. Mirrors the SDK's `NotificationListenerHolder`
 * / `MediaProjectionHolder` host-wiring pattern.
 */
object McpServerHolder {
    @Volatile
    var server: DroidMcp? = null
}

/**
 * Concrete foreground service for the sample. Keeps the MCP HTTP server alive
 * across screen-off and aggressive task-killers — without it the embedded server
 * dies when the app process is reaped in the background.
 *
 * Server ownership stays with the ViewModel (shared via [McpServerHolder]); this
 * service just holds the process up and starts/stops the HTTP listener. It does
 * NOT close the DroidMcp or audit sink on stop — the ViewModel owns those.
 */
class McpServerService : DroidMcpServerService() {

    override fun createServer(): DroidMcp =
        McpServerHolder.server
            ?: error("McpServerHolder.server must be set before starting McpServerService")

    override val notificationTitle: String get() = "droid-mcp server running"
    override val notificationText: String get() = "Listening for MCP tool calls over HTTP"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The server config is UI-driven and shared via the holder. If the
        // process was restarted headless (e.g. START_STICKY after a kill), the
        // holder is empty — there's nothing to run, so bail cleanly instead of
        // letting createServer() throw. And don't ask to be auto-restarted:
        // this sample's server is controlled from the activity, not headless.
        if (McpServerHolder.server == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }
}
