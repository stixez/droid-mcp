package io.droidmcp.notification

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput

/**
 * Pure-Kotlin projection of a StatusBarNotification that carries at least one
 * free-form RemoteInput-bearing action. Lifted out of the platform type so
 * tools and tests can manipulate it without touching SDK-only classes.
 *
 * `replyAction` is non-null when at least one [Notification.Action] on the
 * source notification exposes a [RemoteInput] with `allowFreeFormInput = true`.
 * Quick-reply-only actions (choice arrays without free-form text) are not
 * included — they cannot accept arbitrary LLM-authored text.
 */
data class RepliableNotification(
    val key: String,
    val packageName: String,
    val title: String?,
    val text: String?,
    val postedAt: Long,
    val replyAction: ReplyAction?,
)

data class ReplyAction(
    val label: String?,
    val resultKey: String,
    val hintLabel: String?,
    val remoteInputs: Array<RemoteInput>,
    val pendingIntent: PendingIntent,
)

internal fun Notification.Action.toReplyActionOrNull(): ReplyAction? {
    val inputs = remoteInputs ?: return null
    val freeForm = inputs.firstOrNull { it.allowFreeFormInput } ?: return null
    return ReplyAction(
        label = title?.toString(),
        resultKey = freeForm.resultKey,
        hintLabel = freeForm.label?.toString(),
        remoteInputs = inputs,
        pendingIntent = actionIntent,
    )
}
