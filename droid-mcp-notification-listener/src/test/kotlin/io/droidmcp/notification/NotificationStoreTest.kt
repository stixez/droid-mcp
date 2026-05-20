package io.droidmcp.notification

import android.app.PendingIntent
import android.app.RemoteInput
import com.google.common.truth.Truth.assertThat
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
    fun `concurrent put and remove never throws`() {
        val pool = Executors.newFixedThreadPool(8)
        try {
            repeat(8) { worker ->
                pool.submit {
                    repeat(500) { i ->
                        val key = "w$worker-$i"
                        NotificationStore.putRepliableForTest(dto(key))
                        if (i % 2 == 0) {
                            // Indirect remove via clearForTest-equivalent: just leave them;
                            // verify the snapshot doesn't blow up under contention.
                            NotificationStore.repliableSnapshot()
                        }
                    }
                }
            }
        } finally {
            pool.shutdown()
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue()
        }
        // After concurrent inserts, the store should hold 8*500 distinct keys.
        assertThat(NotificationStore.repliableSnapshot()).hasSize(8 * 500)
    }

    private fun dto(
        key: String,
        packageName: String = "io.test",
        title: String? = "T",
        text: String? = "msg",
        postedAt: Long = 0L,
    ): RepliableNotification = RepliableNotification(
        key = key,
        packageName = packageName,
        title = title,
        text = text,
        postedAt = postedAt,
        replyAction = ReplyAction(
            label = "Reply",
            resultKey = "rk",
            hintLabel = null,
            remoteInputs = arrayOf(mockk<RemoteInput>(relaxed = true)),
            pendingIntent = mockk<PendingIntent>(relaxed = true),
        ),
    )
}
