package io.droidmcp.sample

import android.app.Activity
import java.lang.ref.WeakReference

/**
 * Weak handle to the currently resumed [Activity], set from [MainActivity]'s
 * `onResume` / `onPause`. Backs `PrintTools.all(context, activityProvider)` —
 * `print_content` must launch the system print UI from an Activity, while the
 * ViewModel only holds the application context. Weak so a destroyed activity
 * is never leaked.
 */
object CurrentActivityHolder {
    @Volatile
    private var ref: WeakReference<Activity>? = null

    fun set(activity: Activity) {
        ref = WeakReference(activity)
    }

    /** Clears only if [activity] is still the held one (guards against resume/pause reordering). */
    fun clear(activity: Activity) {
        if (ref?.get() === activity) ref = null
    }

    /** The resumed activity, or `null` if none (app backgrounded, finishing, or destroyed). */
    fun current(): Activity? = ref?.get()?.takeUnless { it.isFinishing || it.isDestroyed }
}
