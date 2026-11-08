package io.droidmcp.app.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.droidmcp.app.MainActivity
import io.droidmcp.app.R
import io.droidmcp.app.appGraph
import io.droidmcp.app.data.server.ServerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Quick Settings tile: turn the server on or off from the notification shade. */
class ServerTileService : TileService() {

    private var listening: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        listening = scope
        scope.launch { appGraph.server.status.collect(::render) }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        val server = appGraph.server
        when (server.status.value) {
            is ServerStatus.Running, is ServerStatus.Paused, ServerStatus.Starting -> server.stop()
            else -> {
                server.start()
                // If Android refused a start from here, open the app so the user can start it there.
                if (server.status.value is ServerStatus.Failed) openApp()
            }
        }
    }

    private fun render(status: ServerStatus) {
        val tile = qsTile ?: return
        tile.icon = Icon.createWithResource(this, R.drawable.ic_stat_server)
        tile.label = "droid-mcp"
        tile.state = when (status) {
            is ServerStatus.Running, ServerStatus.Starting, is ServerStatus.Paused -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when (status) {
                is ServerStatus.Running -> "On"
                ServerStatus.Starting -> "Starting"
                is ServerStatus.Paused -> "Paused"
                is ServerStatus.Failed -> "Error"
                ServerStatus.Stopped -> "Off"
            }
        }
        tile.updateTile()
    }

    // The Intent overload is the only one before Android 14, and only that branch uses it.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            startActivityAndCollapse(intent)
        }
    }
}
