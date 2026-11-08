package io.droidmcp.app.ui

import android.text.format.DateUtils

/** "just now" under a minute (instead of "0 minutes ago"), else "5 minutes ago", "yesterday"... */
fun relativeTime(millis: Long, now: Long = System.currentTimeMillis()): String =
    if (now - millis < DateUtils.MINUTE_IN_MILLIS) "just now"
    else DateUtils.getRelativeTimeSpanString(millis, now, DateUtils.MINUTE_IN_MILLIS).toString()
