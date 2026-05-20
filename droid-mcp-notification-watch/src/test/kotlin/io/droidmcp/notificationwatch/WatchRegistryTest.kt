package io.droidmcp.notificationwatch

import com.google.common.truth.Truth.assertThat
import io.droidmcp.notification.NotificationEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WatchRegistryTest {

    @BeforeEach
    fun clear() {
        WatchRegistry.clearForTest()
    }

    @AfterEach
    fun cleanup() {
        WatchRegistry.clearForTest()
    }

    @Test
    fun `register and retrieve by id`() {
        val spec = makeSpec(id = "abc")
        WatchRegistry.register(spec)
        assertThat(WatchRegistry.get("abc")).isSameInstanceAs(spec)
    }

    @Test
    fun `unregister returns true once then false`() {
        val spec = makeSpec(id = "xyz")
        WatchRegistry.register(spec)
        assertThat(WatchRegistry.unregister("xyz")).isTrue()
        assertThat(WatchRegistry.unregister("xyz")).isFalse()
    }

    @Test
    fun `list omits expired watches`() {
        WatchRegistry.register(makeSpec(id = "alive", ttlSeconds = 3600))
        WatchRegistry.register(makeSpec(id = "dead", ttlSeconds = 60, createdAt = System.currentTimeMillis() - 120_000L))
        val ids = WatchRegistry.list().map { it.id }
        assertThat(ids).containsExactly("alive")
    }

    @Test
    fun `matches AND-combines package and keyword`() {
        val spec = makeSpec(packageName = "com.x", keyword = "urgent")
        assertThat(spec.matches(event(packageName = "com.x", text = "urgent message"))).isTrue()
        // pkg mismatch
        assertThat(spec.matches(event(packageName = "com.y", text = "urgent message"))).isFalse()
        // keyword mismatch
        assertThat(spec.matches(event(packageName = "com.x", text = "trivial"))).isFalse()
    }

    @Test
    fun `keyword search is case-insensitive across text bigText subText tickerText`() {
        val spec = makeSpec(keyword = "ALARM")
        assertThat(spec.matches(event(text = "alarm raised"))).isTrue()
        assertThat(spec.matches(event(text = null, bigText = "alarm context"))).isTrue()
        assertThat(spec.matches(event(text = null, subText = "alarm channel"))).isTrue()
        assertThat(spec.matches(event(text = null, tickerText = "alarm ticker"))).isTrue()
        assertThat(spec.matches(event(text = "noop"))).isFalse()
    }

    @Test
    fun `sender_pattern matches against title`() {
        val spec = makeSpec(senderPattern = "Alice")
        assertThat(spec.matches(event(title = "alice (work)"))).isTrue()
        assertThat(spec.matches(event(title = "Bob"))).isFalse()
    }

    @Test
    fun `null filters in WatchSpec are wildcards`() {
        val spec = makeSpec()  // all filters null
        assertThat(spec.matches(event())).isTrue()
    }

    private fun makeSpec(
        id: String = "w1",
        packageName: String? = null,
        senderPattern: String? = null,
        keyword: String? = null,
        ttlSeconds: Int = 3600,
        fireOnUpdate: Boolean = false,
        createdAt: Long = System.currentTimeMillis(),
    ): WatchSpec = WatchSpec(
        id = id,
        packageName = packageName,
        senderPattern = senderPattern,
        keyword = keyword,
        ttlSeconds = ttlSeconds,
        fireOnUpdate = fireOnUpdate,
        createdAt = createdAt,
    )

    private fun event(
        key: String = "k1",
        packageName: String = "com.x",
        title: String? = null,
        text: String? = "msg",
        bigText: String? = null,
        subText: String? = null,
        tickerText: String? = null,
    ): NotificationEvent = NotificationEvent(
        key = key,
        packageName = packageName,
        title = title,
        text = text,
        bigText = bigText,
        subText = subText,
        tickerText = tickerText,
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
