package io.droidmcp.notification

import android.app.PendingIntent
import android.app.RemoteInput
import com.google.common.truth.Truth.assertThat
import android.service.notification.StatusBarNotification
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class NotificationStoreTest {

    @BeforeEach
    fun reset() {
        NotificationStore.clearForTest()
    }

    @AfterEach
    fun teardown() {
        NotificationStore.clearForTest()
    }

    @Test
    fun `put adds DTO that snapshot returns`() {
        NotificationStore.putRepliableForTest(dto("a", postedAt = 1))
        assertThat(NotificationStore.repliableSnapshot()).hasSize(1)
        assertThat(NotificationStore.findRepliable("a")?.key).isEqualTo("a")
    }

    @Test
    fun `put on existing key replaces the value`() {
        NotificationStore.putRepliableForTest(dto("a", title = "first"))
        NotificationStore.putRepliableForTest(dto("a", title = "second"))
        assertThat(NotificationStore.repliableSnapshot()).hasSize(1)
        assertThat(NotificationStore.findRepliable("a")?.title).isEqualTo("second")
    }

    @Test
    fun `findRepliable returns null for unknown key`() {
        assertThat(NotificationStore.findRepliable("nope")).isNull()
    }

    @Test
    fun `repliableSnapshot is a stable copy`() {
        NotificationStore.putRepliableForTest(dto("a"))
        val first = NotificationStore.repliableSnapshot()
        NotificationStore.putRepliableForTest(dto("b"))
        assertThat(first.map { it.key }).containsExactly("a")
        assertThat(NotificationStore.repliableSnapshot().map { it.key })
            .containsExactly("a", "b")
    }

    @Test
    fun `eventSnapshot returns every event and clear empties it`() {
        NotificationStore.putEventForTest(event("a"))
        NotificationStore.putEventForTest(event("b"))
        assertThat(NotificationStore.eventSnapshot().map { it.key }).containsExactly("a", "b")
        NotificationStore.clearForTest()
        assertThat(NotificationStore.eventSnapshot()).isEmpty()
    }

    @Test
    fun `reset keeps the other notifications when one can't be projected`() {
        val bad = mockk<StatusBarNotification> {
            every { key } returns "bad"
            every { notification } throws IllegalStateException("malformed extras")
        }
        val good = mockk<StatusBarNotification>(relaxed = true) { every { key } returns "good" }
        NotificationStore.reset(arrayOf(bad, good))
        assertThat(NotificationStore.containsKey("bad")).isTrue()
        assertThat(NotificationStore.eventSnapshot().map { it.key }).containsExactly("good")
    }

    @Test
    fun `concurrent put and snapshot never throw`() {
        // Built before the threads start: making mocks inside the workers made this slow enough
        // to time out under load, and workers still running then leaked entries into later tests.
        val reply = replyAction()
        val batches = List(WORKERS) { worker -> List(PER_WORKER) { i -> dto("w$worker-$i", reply = reply) } }
        val pool = Executors.newFixedThreadPool(WORKERS)
        val runs = try {
            batches.map { batch ->
                pool.submit {
                    batch.forEachIndexed { i, item ->
                        NotificationStore.putRepliableForTest(item)
                        if (i % 2 == 0) NotificationStore.repliableSnapshot()
                    }
                }
            }
        } finally {
            pool.shutdown()
            if (!pool.awaitTermination(30, TimeUnit.SECONDS)) pool.shutdownNow()
        }
        // get() rethrows anything a worker threw; submit() alone would swallow it.
        runs.forEach { it.get(0, TimeUnit.SECONDS) }
        assertThat(NotificationStore.repliableSnapshot()).hasSize(WORKERS * PER_WORKER)
    }

    private fun event(key: String): NotificationEvent = NotificationEvent(
        key = key, packageName = "io.test", title = "T", text = "msg", bigText = null, subText = null,
        tickerText = null, category = null, channelId = null, groupKey = null, isOngoing = false,
        isClearable = true, legacyPriority = 0, channelImportance = -1, postedAt = 0L, `when` = 0L,
        hasReplyAction = false, actionLabels = emptyList(),
    )

    private fun dto(
        key: String,
        packageName: String = "io.test",
        title: String? = "T",
        text: String? = "msg",
        postedAt: Long = 0L,
        reply: ReplyAction = replyAction(),
    ): RepliableNotification = RepliableNotification(
        key = key,
        packageName = packageName,
        title = title,
        text = text,
        postedAt = postedAt,
        replyAction = reply,
    )

    private fun replyAction() = ReplyAction(
        label = "Reply",
        resultKey = "rk",
        hintLabel = null,
        remoteInputs = arrayOf(mockk<RemoteInput>(relaxed = true)),
        pendingIntent = mockk<PendingIntent>(relaxed = true),
    )

    private companion object {
        const val WORKERS = 8
        const val PER_WORKER = 500
    }
}
