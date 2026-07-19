package io.droidmcp.notificationwatch

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.droidmcp.notification.NotificationListenerHolder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NotificationWatchToolsTest {

    private val context: Context = mockk(relaxed = true)

    @BeforeEach
    fun reset() {
        NotificationListenerHolder.clear()
        WatchRegistry.clearForTest()
    }

    @AfterEach
    fun cleanup() {
        NotificationListenerHolder.clear()
        WatchRegistry.clearForTest()
    }

    @Test
    fun `provider exposes the three watch tools`() {
        val names = NotificationWatchTools.all(context).map { it.name }
        assertThat(names).containsExactly(
            "watch_notifications",
            "unwatch_notifications",
            "list_notification_watches",
        )
    }

    @Test
    fun `watch_notifications errors when listener not configured`() = runTest {
        val result = WatchNotificationsTool(context).execute(mapOf("keyword" to "hi"))
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("notification_listener_not_enabled")
    }

    @Test
    fun `unwatch is idempotent on unknown ids`() = runTest {
        val result = UnwatchNotificationsTool(context).execute(mapOf("watch_id" to "nope"))
        assertThat(result.isSuccess).isTrue()
        assertThat(result.data?.get("removed")).isEqualTo(false)
    }

    @Test
    fun `list returns watches with expires_in_seconds`() = runTest {
        WatchRegistry.register(
            WatchSpec(
                id = "x1",
                packageName = "com.x",
                senderPattern = null,
                keyword = null,
                ttlSeconds = 3600,
                fireOnUpdate = false,
                createdAt = System.currentTimeMillis(),
            )
        )
        val result = ListNotificationWatchesTool(context).execute(emptyMap())
        assertThat(result.isSuccess).isTrue()
        @Suppress("UNCHECKED_CAST")
        val watches = result.data?.get("watches") as List<Map<String, Any?>>
        assertThat(watches).hasSize(1)
        assertThat(watches[0]["watch_id"]).isEqualTo("x1")
        val expiresIn = (watches[0]["expires_in_seconds"] as Long)
        assertThat(expiresIn).isGreaterThan(3500L)
        assertThat(expiresIn).isAtMost(3600L)
    }

    @Test
    fun `list tool declares readOnly + idempotent annotations`() {
        val tool = ListNotificationWatchesTool(context)
        assertThat(tool.annotations.readOnlyHint).isTrue()
        assertThat(tool.annotations.idempotentHint).isTrue()
    }

    @Test
    fun `unwatch tool declares destructive + idempotent`() {
        val tool = UnwatchNotificationsTool(context)
        assertThat(tool.annotations.destructiveHint).isTrue()
        assertThat(tool.annotations.idempotentHint).isTrue()
    }
}
