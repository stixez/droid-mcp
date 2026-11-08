package io.droidmcp.app.confirm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.droidmcp.app.MainActivity
import io.droidmcp.app.R
import io.droidmcp.core.ToolCallConfirmer
import io.droidmcp.core.ToolCallRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** A risky call waiting for the user, in words a person can judge. */
data class PendingConfirmation(
    val id: Int,
    /** Short question: "Allow send message?" */
    val title: String,
    /** Who is asking: "A connected client" or the pairing label. */
    val requester: String,
    val detail: String,
    val toolName: String,
    val clientLabel: String?,
    /** Wall-clock time this request is declined automatically. */
    val expiresAt: Long = 0L,
)

/**
 * Asks the person holding the phone before a client runs a tool that sends, deletes or changes
 * something. In the app it's a dialog; otherwise a heads-up notification with Allow and Deny.
 * No answer within [timeoutMs] counts as Deny, and the client gets `tool_call_declined`.
 */
class ConfirmationCenter(
    private val context: Context,
    private val isAppInForeground: () -> Boolean,
    /** Remembered approvals; null in tests that only exercise the prompt. */
    private val policy: ApprovalPolicy? = null,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) : ToolCallConfirmer {

    /**
     * Random per process. Notification buttons carry it, so a button left over from an earlier
     * process can't answer a new request that happens to reuse its id.
     */
    val session: Long = SecureRandom().nextLong()

    private val nextId = AtomicInteger(1)
    private val waiting = ConcurrentHashMap<Int, CompletableDeferred<Boolean>>()
    private val notified = ConcurrentHashMap.newKeySet<Int>()

    private val _pending = MutableStateFlow<List<PendingConfirmation>>(emptyList())

    /** Calls waiting for an answer, oldest first. The app shows the first one as a dialog. */
    val pending: StateFlow<List<PendingConfirmation>> = _pending.asStateFlow()

    init {
        // Approvals from a previous process can no longer be answered; clear them.
        runCatching {
            context.getSystemService(NotificationManager::class.java)?.activeNotifications
                ?.filter { it.tag == NOTIFICATION_TAG }
                ?.forEach { NotificationManagerCompat.from(context).cancel(NOTIFICATION_TAG, it.id) }
        }
    }

    /** An approval is on screen or in the notification shade, waiting for the user. */
    fun hasPending(): Boolean = _pending.value.isNotEmpty()

    override suspend fun confirm(request: ToolCallRequest): Boolean {
        // A model must never answer its own approval: while one waits, tools that can tap, type,
        // press notification buttons or cover the screen are refused, even if approved before.
        if (request.toolName in UI_DRIVERS && hasPending()) return false
        // Approved earlier "for 1 hour" or "always": run without asking.
        if (policy?.isPreApproved(request.toolName) == true) return true
        val id = nextId.getAndIncrement()
        val item = describe(id, request).copy(expiresAt = System.currentTimeMillis() + timeoutMs)
        val answer = CompletableDeferred<Boolean>()
        waiting[id] = answer
        _pending.update { it + item }
        if (!isAppInForeground() && !postNotification(item)) {
            // Nowhere to ask: no app on screen and approvals can't be shown. Refuse rather than wait.
            finish(id)
            return false
        }
        return try {
            withTimeoutOrNull(timeoutMs) { answer.await() } ?: false
        } finally {
            finish(id)
        }
    }

    /**
     * The user's answer, from the dialog or a notification action of this [session]. An Allow
     * with a longer [scope] also covers later calls to the same tool.
     */
    fun answer(id: Int, allow: Boolean, session: Long = this.session, scope: ApprovalScope = ApprovalScope.Once) {
        if (session != this.session) return
        if (allow && scope != ApprovalScope.Once) {
            _pending.value.firstOrNull { it.id == id }?.let { policy?.remember(it.toolName, scope) }
        }
        waiting[id]?.complete(allow)
    }

    /** The app came back: its dialog takes over, so drop the duplicate notifications. */
    fun onAppForegrounded() {
        notified.toList().forEach { id ->
            notified.remove(id)
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_TAG, id)
        }
    }

    /** The app left the screen while requests wait: move them to notifications. */
    fun onAppBackgrounded() {
        _pending.value.filter { it.id !in notified }.forEach { item ->
            // Notifications are off: nowhere left to answer, so refuse now instead of letting the
            // client wait out the timeout (confirm() does the same for a new request).
            if (!postNotification(item)) waiting[item.id]?.complete(false)
        }
    }

    private fun finish(id: Int) {
        waiting.remove(id)
        notified.remove(id)
        _pending.update { list -> list.filterNot { it.id == id } }
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_TAG, id)
    }

    private fun canNotify(): Boolean {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        ensureChannel()
        // The user can block just this channel; notify() would then drop it silently.
        val channel = context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(CHANNEL_ID)
        return channel != null && channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun postNotification(item: PendingConfirmation): Boolean {
        if (!canNotify()) return false
        val openApp = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val open = PendingIntent.getActivity(
            context,
            item.id,
            openApp,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // Approval is for the phone's owner, not whoever picks it up. Android 12+ can require an
        // unlock for a notification button; older versions can't, so there the button opens the
        // app (which needs an unlock) and the user answers in the dialog.
        val secure = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val allowAction = if (secure) {
            NotificationCompat.Action.Builder(0, "Allow", ConfirmationReceiver.pendingIntent(context, item.id, allow = true, session))
                .setAuthenticationRequired(true)
                .build()
        } else {
            NotificationCompat.Action.Builder(0, "Review", open).build()
        }
        val allowHourAction = if (secure) {
            NotificationCompat.Action.Builder(
                0,
                "Allow 1 h",
                ConfirmationReceiver.pendingIntent(context, item.id, allow = true, session, ApprovalScope.OneHour),
            )
                .setAuthenticationRequired(true)
                .build()
        } else {
            null
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_server)
            .setContentTitle(item.title)
            .setSubText(item.requester)
            .setContentText(item.detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.detail))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(open)
            .setAutoCancel(false)
            .setOngoing(true)
            .setTimeoutAfter(timeoutMs)
            // On the lock screen show only that something waits, not what.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_stat_server)
                    .setContentTitle("A client is waiting for your approval")
                    .setContentText("Unlock to see what it wants to do")
                    .build(),
            )
            .addAction(0, "Deny", ConfirmationReceiver.pendingIntent(context, item.id, allow = false, session))
            .apply { if (allowHourAction != null) addAction(allowHourAction) }
            .addAction(allowAction)
            .build()
        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_TAG, item.id, notification)
            notified += item.id
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Approvals", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Asks you before a connected client sends, deletes or changes something"
            },
        )
    }

    /** How long a request waits for an answer, for progress displays. */
    val timeout: Long get() = timeoutMs

    companion object {
        const val DEFAULT_TIMEOUT_MS = 60_000L

        /**
         * Tools that act on whatever is on screen or in the notification shade, which includes
         * the approval dialog and notification. Refused while an approval waits ([confirm]).
         */
        val UI_DRIVERS = setOf(
            // Screen control
            "tap", "long_press", "gesture", "global_action", "find_and_tap", "click_node",
            "long_click_node", "set_node_text", "scroll_node", "scroll_to_find",
            // Keyboard
            "type_text", "delete_text", "commit_keystroke", "set_selection", "switch_to_previous_ime",
            // Notification buttons
            "invoke_notification_action", "reply_to_notification", "dismiss_notification",
            // An overlay could cover the dialog's details
            "guide_user",
        )
        private const val CHANNEL_ID = "droid_mcp_approvals"
        private const val NOTIFICATION_TAG = "approval"

        /** Turns a raw request into the sentence the user decides on. Pure, so it's unit-tested. */
        fun describe(id: Int, request: ToolCallRequest): PendingConfirmation {
            val who = when (val label = request.clientLabel) {
                null, "primary", "anonymous" -> "A connected client"
                else -> label
            }
            val action = request.annotations.title ?: humanize(request.toolName)
            // Every argument, one line each and sorted by name, so a client can neither push the
            // one that matters out of view nor fake extra lines with newlines inside a value.
            val detail = request.arguments.entries
                .sortedBy { it.key }
                .joinToString("\n") { (k, v) -> "${humanize(oneLine(k))}: ${clip(oneLine(v.toString()))}" }
                .ifEmpty { "No details" }
            return PendingConfirmation(
                id = id,
                title = "Allow ${action.replaceFirstChar { it.lowercase() }}?",
                requester = who,
                detail = detail,
                toolName = request.toolName,
                clientLabel = request.clientLabel,
            )
        }

        /** `send_message` -> `Send message`. */
        fun humanize(snake: String): String =
            snake.split('_').filter { it.isNotEmpty() }.joinToString(" ").replaceFirstChar { it.uppercase() }

        /** Line breaks and other control characters shown as visible escapes, never as layout. */
        internal fun oneLine(value: String): String = buildString {
            value.forEach { c ->
                when {
                    c == '\n' -> append("\\n")
                    c == '\r' -> append("\\r")
                    c == '\t' -> append("\\t")
                    c.isISOControl() || c.isInvisibleFormat() -> append("\\u%04x".format(c.code))
                    else -> append(c)
                }
            }
        }

        /**
         * Bidi overrides, zero-width and other format characters, plus line and paragraph
         * separators: they can make a phone number or command look different from what runs.
         * The zero-width joiner stays, it only glues emoji together.
         */
        private fun Char.isInvisibleFormat(): Boolean = this != '\u200D' && when (Character.getType(this).toByte()) {
            Character.FORMAT, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR -> true
            else -> false
        }

        /** Long values are cut, and the cut is stated, never silent. */
        internal fun clip(value: String): String =
            if (value.length <= MAX_VALUE_CHARS) value
            else value.take(MAX_VALUE_CHARS) + "… (+${value.length - MAX_VALUE_CHARS} more characters)"

        private const val MAX_VALUE_CHARS = 300
    }
}
