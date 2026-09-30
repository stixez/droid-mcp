package io.droidmcp.settings

import io.droidmcp.core.support.ActivityLaunch

/** Error text returned when [ActivityLaunch.canStartActivity] is false. */
internal const val BACKGROUND_LAUNCH_ERROR =
    "Cannot open a system screen while the host app is in the background: Android 10+ blocks " +
        "background activity starts. Bring the host app to the foreground or grant it " +
        "'Display over other apps' (SYSTEM_ALERT_WINDOW)."
