package io.droidmcp.notification

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Test

class NotificationListenerBusTest {

    @Test
    fun `events emits published values to a collector`() = runTest {
        val collected = async { NotificationListenerBus.events.first() }
        // Yield until the collector is suspended on first()
        yield()
        val event = sampleEvent(key = "k1")
        NotificationListenerBus.publish(event)
        assertThat(collected.await()).isEqualTo(event)
    }

    @Test
    fun `replay cache is empty - new collector does not see previously-emitted events`() = runTest {
        NotificationListenerBus.publish(sampleEvent(key = "ghost"))
        assertThat(NotificationListenerBus.events.replayCache).isEmpty()
    }

    private fun sampleEvent(key: String): NotificationEvent = NotificationEvent(
        key = key,
        packageName = "com.x",
        title = "t",
        text = "x",
        bigText = null,
        subText = null,
        tickerText = null,
        category = null,
        channelId = null,
        groupKey = null,
        isOngoing = false,
        isClearable = true,
        legacyPriority = 0,
        channelImportance = -1,
        postedAt = 1L,
        `when` = 1L,
        hasReplyAction = false,
        actionLabels = emptyList(),
    )
}
