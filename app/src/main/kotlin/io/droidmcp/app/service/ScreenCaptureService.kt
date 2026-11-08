package io.droidmcp.app.service

import android.media.projection.MediaProjection
import io.droidmcp.app.R
import io.droidmcp.app.appGraph
import io.droidmcp.screenshot.DroidMcpCaptureService

/**
 * Holds the screen-capture token for `capture_screen` (the SDK's [DroidMcpCaptureService]), with
 * the app's notification, and re-checks permissions so Screenshots shows as on or off right away.
 */
class ScreenCaptureService : DroidMcpCaptureService() {

    override val smallIconRes: Int = R.drawable.ic_stat_server
    override val notificationTitle: String get() = "Screenshots are on"
    override val notificationText: String get() = "Connected clients can capture the screen until you stop it"

    override fun onProjectionStarted(projection: MediaProjection) {
        appGraph.permissions.refresh()
    }

    override fun onProjectionStopped() {
        appGraph.permissions.refresh()
    }
}
