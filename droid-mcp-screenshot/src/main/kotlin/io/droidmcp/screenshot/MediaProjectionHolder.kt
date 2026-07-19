package io.droidmcp.screenshot

import android.media.projection.MediaProjection

/**
 * Holds the [MediaProjection] token obtained by the host Activity.
 *
 * The host app must call [set] after the user grants screen-capture consent
 * (e.g. via `MediaProjectionManager.createScreenCaptureIntent()`).
 * [CaptureScreenTool] reads the token from here.
 *
 * [set] registers a [MediaProjection.Callback], required for two reasons: the platform requires
 * a callback to be registered before `createVirtualDisplay()` can be called at all — without one,
 * [CaptureScreenTool] throws `IllegalStateException` on every call; and [onStop] lets this holder
 * clear itself when the user revokes consent via the system's screen-capture notification,
 * instead of holding a dead token that looks valid but isn't.
 *
 * @property projection the active projection, or `null` if consent has not been
 *   granted (or has been cleared).
 */
object MediaProjectionHolder {
    @Volatile
    var projection: MediaProjection? = null
        private set

    private val callback = object : MediaProjection.Callback() {
        override fun onStop() {
            projection = null
        }
    }

    /** Stores [mediaProjection] as the active projection token and registers the required callback. */
    fun set(mediaProjection: MediaProjection) {
        mediaProjection.registerCallback(callback, null)
        projection = mediaProjection
    }

    /** Stops and clears the active projection. */
    fun clear() {
        projection?.stop()
        projection = null
    }
}
