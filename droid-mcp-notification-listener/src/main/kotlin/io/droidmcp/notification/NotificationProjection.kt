package io.droidmcp.notification

import android.app.Notification
import android.service.notification.StatusBarNotification

/**
 * Projects a platform [StatusBarNotification] into a pure-data
 * [NotificationEvent]. The caller passes the resolved
 * `NotificationChannel.importance` (or -1 if unresolvable) — channel lookup
 * lives in [McpNotificationListenerServiceBase] where the
 * `NotificationListenerService.getNotificationChannels(pkg, user)` API is
 * available.
 */
internal fun StatusBarNotification.toEvent(channelImportance: Int): NotificationEvent {
    val n = notification
    val extras = n?.extras
    val actions = n?.actions ?: emptyArray()
    val hasReply = actions.any { action ->
        action.remoteInputs?.any { it.allowFreeFormInput } == true
    }
    return NotificationEvent(
        key = key,
        packageName = packageName,
        title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
        text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
        bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
        subText = extras?.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
        tickerText = n?.tickerText?.toString(),
        category = n?.category,
        channelId = n?.channelId,
        groupKey = groupKey,
        isOngoing = n != null && (n.flags and Notification.FLAG_ONGOING_EVENT) != 0,
        isClearable = isClearable,
        legacyPriority = n?.priority ?: 0,
        channelImportance = channelImportance,
        postedAt = postTime,
        `when` = n?.`when` ?: 0L,
        hasReplyAction = hasReply,
        // Preserve positional alignment with sbn.notification.actions so
        // `invoke_notification_action(action_index = N)` and a caller comparing
        // `actionLabels.indexOf(label)` agree. Null/blank titles become "".
        actionLabels = actions.map { it.title?.toString() ?: "" },
    )
}
