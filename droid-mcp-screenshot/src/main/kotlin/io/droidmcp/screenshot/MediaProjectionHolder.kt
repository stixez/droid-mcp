package io.droidmcp.screenshot

import android.media.projection.MediaProjection

/**
 * Holds the [MediaProjection] token obtained by the host Activity.
 *
 * The host app must call [set] after the user grants screen-capture consent
 * (e.g. via `MediaProjectionManager.createScreenCaptureIntent()`).
 * [CaptureScreenTool] reads the token from here.
 *
 * @property projection the active projection, or `null` if consent has not been
 *   granted (or has been cleared).
 */
object MediaProjectionHolder {
    @Volatile
    var projection: MediaProjection? = null
        private set

    /** Stores [mediaProjection] as the active projection token. */
    fun set(mediaProjection: MediaProjection) {
        projection = mediaProjection
    }

    /** Stops and clears the active projection. */
    fun clear() {
        projection?.stop()
        projection = null
    }
}
