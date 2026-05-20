package io.droidmcp.notificationsreply

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.droidmcp.notification.NotificationListenerHolder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class InvokeNotificationActionToolTest {

    private val context: Context = mockk(relaxed = true)

    @BeforeEach
    fun reset() {
        NotificationListenerHolder.clear()
    }

    @AfterEach
    fun cleanup() {
        NotificationListenerHolder.clear()
    }

    @Test
    fun `errors with notification_listener_not_enabled when holder unset`() = runTest {
        val result = InvokeNotificationActionTool(context).execute(mapOf("key" to "k", "action_label" to "Mark"))
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("notification_listener_not_enabled")
    }

    @Test
    fun `tool declares destructive annotation`() {
        val tool = InvokeNotificationActionTool(context)
        assertThat(tool.annotations.destructiveHint).isTrue()
    }

    @Test
    fun `tool declares key + action_label + action_index params`() {
        val byName = InvokeNotificationActionTool(context).parameters.associateBy { it.name }
        assertThat(byName.keys).containsExactly("key", "action_label", "action_index")
        assertThat(byName["key"]?.required).isTrue()
        assertThat(byName["action_label"]?.required).isFalse()
        assertThat(byName["action_index"]?.required).isFalse()
    }
}
