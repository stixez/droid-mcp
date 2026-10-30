package io.droidmcp.notificationsreply

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.droidmcp.notification.McpNotificationListenerServiceBase
import io.droidmcp.notification.NotificationEvent
import io.droidmcp.notification.NotificationListenerHolder
import io.droidmcp.notification.NotificationStore
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ListNotificationsToolTest {

    private val context: Context = mockk(relaxed = true)

    @BeforeEach
    fun reset() {
        NotificationListenerHolder.clear()
    }

    @AfterEach
    fun cleanup() {
        NotificationListenerHolder.clear()
        unmockkObject(NotificationStore, McpNotificationListenerServiceBase.Companion)
    }

    @Test
    fun `tool declares readOnly idempotent annotations and optional params`() {
        val tool = ListNotificationsTool(context)
        assertThat(tool.annotations.readOnlyHint).isTrue()
        assertThat(tool.annotations.idempotentHint).isTrue()
        assertThat(tool.parameters.map { it.name }).containsExactly("limit", "package_name", "include_ongoing")
        assertThat(tool.parameters.none { it.required }).isTrue()
    }

    @Test
    fun `errors when listener component is not configured`() = runTest {
        val result = ListNotificationsTool(context).execute(emptyMap())
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("NotificationListenerService not configured")
    }

    @Test
    fun `select sorts newest first and skips ongoing by default`() {
        val events = listOf(event("old", postedAt = 1), event("music", postedAt = 3, ongoing = true), event("new", postedAt = 2))
        assertThat(ListNotificationsTool.select(events, 20, null, false).map { it.key })
            .containsExactly("new", "old").inOrder()
        assertThat(ListNotificationsTool.select(events, 20, null, true).map { it.key })
            .containsExactly("music", "new", "old").inOrder()
    }

    @Test
    fun `select filters by package and applies limit`() {
        val events = listOf(event("a", pkg = "com.a", postedAt = 1), event("b", pkg = "com.b"), event("a2", pkg = "com.a", postedAt = 2))
        assertThat(ListNotificationsTool.select(events, 20, "com.a", false).map { it.key }).containsExactly("a2", "a").inOrder()
        assertThat(ListNotificationsTool.select(events, 1, "com.a", false).map { it.key }).containsExactly("a2")
    }

    @Test
    fun `select drops a group summary only while its bundle has other notifications`() {
        val events = listOf(
            event("sum", group = "g", postedAt = 5), event("c1", group = "g", postedAt = 1), event("c2", group = "g", postedAt = 2),
            event("lonely-sum", group = "h", postedAt = 3),
        )
        assertThat(ListNotificationsTool.select(events, 20, null, false, setOf("sum", "lonely-sum")).map { it.key })
            .containsExactly("lonely-sum", "c2", "c1").inOrder()
    }

    @Test
    fun `a summary whose only children are filtered out stays visible`() {
        val events = listOf(event("sum", group = "g", postedAt = 2), event("music", group = "g", postedAt = 1, ongoing = true))
        assertThat(ListNotificationsTool.select(events, 20, null, false, setOf("sum")).map { it.key }).containsExactly("sum")
    }

    @Test
    fun `errors when access is granted but the listener is not connected`() = runTest {
        NotificationListenerHolder.set(mockk<ComponentName>(relaxed = true))
        val nm = mockk<NotificationManager> { every { isNotificationListenerAccessGranted(any()) } returns true }
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns nm
        val result = ListNotificationsTool(context).execute(emptyMap())
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("not connected")
    }

    @Test
    fun `execute returns store contents with big text preferred`() = runTest {
        NotificationListenerHolder.set(mockk<ComponentName>(relaxed = true))
        val nm = mockk<NotificationManager> { every { isNotificationListenerAccessGranted(any()) } returns true }
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns nm
        mockkObject(McpNotificationListenerServiceBase.Companion)
        every { McpNotificationListenerServiceBase.isConnected } returns true
        mockkObject(NotificationStore)
        every { NotificationStore.eventSnapshot() } returns listOf(
            event("k", text = "short", bigText = "the full message", hasReply = true, actions = listOf("Reply", "Mark as read")),
        )

        val result = ListNotificationsTool(context).execute(emptyMap())

        assertThat(result.isSuccess).isTrue()
        assertThat(result.data!!["count"]).isEqualTo(1)
        @Suppress("UNCHECKED_CAST")
        val item = (result.data!!["notifications"] as List<Map<String, Any?>>).single()
        assertThat(item["key"]).isEqualTo("k")
        assertThat(item["text"]).isEqualTo("the full message")
        assertThat(item["has_reply_action"]).isEqualTo(true)
        assertThat(item["action_labels"]).isEqualTo(listOf("Reply", "Mark as read"))
    }

    private fun event(
        key: String,
        pkg: String = "io.test",
        postedAt: Long = 0L,
        ongoing: Boolean = false,
        text: String? = "msg",
        bigText: String? = null,
        hasReply: Boolean = false,
        actions: List<String> = emptyList(),
        group: String? = null,
    ) = NotificationEvent(
        key = key, packageName = pkg, title = "T", text = text, bigText = bigText, subText = null,
        tickerText = null, category = null, channelId = null, groupKey = group, isOngoing = ongoing,
        isClearable = !ongoing, legacyPriority = 0, channelImportance = -1, postedAt = postedAt, `when` = postedAt,
        hasReplyAction = hasReply, actionLabels = actions,
    )
}
