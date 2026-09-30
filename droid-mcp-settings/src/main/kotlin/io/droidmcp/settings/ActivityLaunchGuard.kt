package io.droidmcp.settings

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * Whether this process may start an Activity right now. On API 29+ background activity starts
 * are silently blocked unless the app is foreground/visible or holds `SYSTEM_ALERT_WINDOW`
 * (checked via [Settings.canDrawOverlays]). Always true below API 29.
 */
internal fun canStartActivityNow(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
    val info = ActivityManager.RunningAppProcessInfo()
    ActivityManager.getMyMemoryState(info)
    if (info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) return true
    return Settings.canDrawOverlays(context)
}

/** Error text returned when [canStartActivityNow] is false. */
internal const val BACKGROUND_LAUNCH_ERROR =
    "Cannot open a system screen while the host app is in the background: Android 10+ blocks " +
        "background activity starts. Bring the host app to the foreground or grant it " +
        "'Display over other apps' (SYSTEM_ALERT_WINDOW)."
