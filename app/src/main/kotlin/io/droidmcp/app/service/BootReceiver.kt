package io.droidmcp.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.droidmcp.app.appGraph
import kotlinx.coroutines.launch

/**
 * Starts the server after the phone boots (or the app updates) when the user asked for it.
 * Android allows a foreground service to start from these broadcasts.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val graph = context.appGraph
        val pending = goAsync()
        graph.appScope.launch {
            try {
                val s = graph.settings.snapshot()
                if (s.startOnBoot && s.setupCompleted) graph.server.start()
            } finally {
                pending.finish()
            }
        }
    }
}
