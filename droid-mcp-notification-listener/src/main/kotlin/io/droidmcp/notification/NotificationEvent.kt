package io.droidmcp.notification

/**
 * Push-side projection of a posted notification. Emitted on
 * [NotificationListenerBus] when [McpNotificationListenerServiceBase] receives
 * `onNotificationPosted`. Pure-data — no `Notification` reference,
 * no `PendingIntent`s, no large image extras leak across the bus.
 *
 * `priority` is intentionally split so the user-channel signal stays
 * distinguishable from the app's self-reported urgency.
 */
data class NotificationEvent(
    /** Unique per-active notification id from the framework. */
    val key: String,
    val packageName: String,
    val title: String?,
    val text: String?,
    /** EXTRA_BIG_TEXT — messaging apps put the full message body here. */
    val bigText: String?,
    /** EXTRA_SUB_TEXT — usually conversation name on messaging apps. */
    val subText: String?,
    /** Marquee / one-shot text. */
    val tickerText: String?,
    /**
     * `Notification.category` — primary filter signal. Values like
     * `Notification.CATEGORY_MESSAGE`, `CATEGORY_CALL`, `CATEGORY_EMAIL`,
     * `CATEGORY_PROMO`, etc. Null when the source app didn't set one.
     */
    val category: String?,
    /** Disambiguates "messages" vs "calls" within one app. */
    val channelId: String?,
    /** Conversation-bundle key. Same value across grouped notifications. */
    val groupKey: String?,
    /** True for foreground-service / always-running notifications. */
    val isOngoing: Boolean,
    val isClearable: Boolean,
    /** `Notification.priority` — only meaningful pre-O; usually 0 post-O. */
    val legacyPriority: Int,
    /**
     * `NotificationChannel.importance` for the channel that posted this. This
     * is the user-settable "how much do I care" signal that dominates on O+.
     * `-1` when the channel can't be resolved (older device, unknown channel).
     */
    val channelImportance: Int,
    /** When the system received and posted this notification. */
    val postedAt: Long,
    /** `notification.when` — app-set timestamp, often the message send time. */
    val `when`: Long,
    /** True if any action carries a `RemoteInput` with `allowFreeFormInput`. */
    val hasReplyAction: Boolean,
    /** Action button labels in order — labels only, no PendingIntents. */
    val actionLabels: List<String>,
)
