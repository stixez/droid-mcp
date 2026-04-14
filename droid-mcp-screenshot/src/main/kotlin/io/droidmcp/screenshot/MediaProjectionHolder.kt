package io.droidmcp.screenshot

import android.media.projection.MediaProjection

/**
 * Holds the MediaProjection token obtained by the host Activity.
 * The host app must call MediaProjectionHolder.set(projection) after
 * the user grants screen capture consent.
 */
object MediaProjectionHolder {
    @Volatile
    var projection: MediaProjection? = null
        private set

    fun set(mediaProjection: MediaProjection) {
        projection = mediaProjection
    }

    fun clear() {
        projection?.stop()
        projection = null
    }
}
