package io.droidmcp.playback

import android.content.ComponentName

/**
 * Holds the ComponentName of the host app's NotificationListenerService.
 * The host app must call NotificationListenerHolder.set(componentName) with
 * its own service class before using playback tools.
 *
 * Example:
 *   NotificationListenerHolder.set(ComponentName(context, MyListenerService::class.java))
 */
object NotificationListenerHolder {
    @Volatile
    var componentName: ComponentName? = null
        private set

    fun set(name: ComponentName) {
        componentName = name
    }

    fun clear() {
        componentName = null
    }
}
