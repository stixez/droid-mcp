package io.droidmcp.notificationsreply

import android.content.ComponentName
import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.droidmcp.core.ParameterType
import io.droidmcp.notification.NotificationListenerHolder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NotificationsReplyToolsTest {

    private val context: Context = mockk(relaxed = true)

    @BeforeEach
    fun clearHolder() {
        NotificationListenerHolder.clear()
    }

    @AfterEach
    fun resetHolder() {
        NotificationListenerHolder.clear()
    }

    @Test
    fun `provider exposes the four reply tools`() {
        val names = NotificationsReplyTools.all(context).map { it.name }
        assertThat(names).containsExactly(
            "list_repliable_notifications",
            "reply_to_notification",
            "dismiss_notification",
            "invoke_notification_action",
        )
    }

    @Test
    fun `list tool declares readOnly idempotent annotations and integer limit`() {
        val tool = ListRepliableNotificationsTool(context)
        assertThat(tool.annotations.readOnlyHint).isTrue()
        assertThat(tool.annotations.idempotentHint).isTrue()
        assertThat(tool.parameters.single().name).isEqualTo("limit")
        assertThat(tool.parameters.single().type).isEqualTo(ParameterType.INTEGER)
    }

    @Test
    fun `reply tool declares destructive annotation and required params`() {
        val tool = ReplyToNotificationTool(context)
        assertThat(tool.annotations.destructiveHint).isTrue()
        val byName = tool.parameters.associateBy { it.name }
        assertThat(byName["key"]?.required).isTrue()
        assertThat(byName["text"]?.required).isTrue()
    }

    @Test
    fun `dismiss tool declares destructive annotation and required key`() {
        val tool = DismissNotificationTool(context)
        assertThat(tool.annotations.destructiveHint).isTrue()
        assertThat(tool.parameters.single().name).isEqualTo("key")
        assertThat(tool.parameters.single().required).isTrue()
    }

    @Test
    fun `list tool errors when listener component is not configured`() = runTest {
        val result = ListRepliableNotificationsTool(context).execute(emptyMap())
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("NotificationListenerService not configured")
    }

    @Test
    fun `reply tool errors when listener component is not configured`() = runTest {
        val result = ReplyToNotificationTool(context).execute(mapOf("key" to "k", "text" to "hi"))
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("NotificationListenerService not configured")
    }

    @Test
    fun `dismiss tool errors when listener component is not configured`() = runTest {
        val result = DismissNotificationTool(context).execute(mapOf("key" to "k"))
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("NotificationListenerService not configured")
    }

    @Test
    fun `holder can be set and cleared`() {
        val component: ComponentName = mockk(relaxed = true)
        NotificationListenerHolder.set(component)
        assertThat(NotificationListenerHolder.componentName).isSameInstanceAs(component)
        NotificationListenerHolder.clear()
        assertThat(NotificationListenerHolder.componentName).isNull()
    }
}
