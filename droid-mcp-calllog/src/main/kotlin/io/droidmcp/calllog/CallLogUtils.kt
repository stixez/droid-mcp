package io.droidmcp.calllog

import android.provider.CallLog

/**
 * Maps a `CallLog.Calls.TYPE` integer to a lowercase label: incoming, outgoing, missed,
 * rejected, blocked, or "unknown" for anything unrecognized.
 *
 * @param type the raw `CallLog.Calls.TYPE` value
 * @return the human-readable call-type name
 */
internal fun callTypeName(type: Int): String = when (type) {
    CallLog.Calls.INCOMING_TYPE -> "incoming"
    CallLog.Calls.OUTGOING_TYPE -> "outgoing"
    CallLog.Calls.MISSED_TYPE -> "missed"
    CallLog.Calls.REJECTED_TYPE -> "rejected"
    CallLog.Calls.BLOCKED_TYPE -> "blocked"
    else -> "unknown"
}
