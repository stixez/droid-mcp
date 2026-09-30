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

    @Test
    fun `dispatch buffers matched events and poll drains them`() {
        WatchRegistry.register(makeSpec(id = "p1", keyword = "urgent"))
        WatchRegistry.dispatch(event(key = "a", text = "urgent one"))
        WatchRegistry.dispatch(event(key = "b", text = "boring"))
        WatchRegistry.dispatch(event(key = "a", text = "urgent again")) // same key, fire-once

        val peek = WatchRegistry.poll("p1", clear = false)!!
        assertThat(peek.events.map { it.key }).containsExactly("a")
        val drained = WatchRegistry.poll("p1", clear = true)!!
        assertThat(drained.events).hasSize(1)
        assertThat(WatchRegistry.poll("p1", clear = true)!!.events).isEmpty()
    }

    @Test
    fun `buffer is bounded and counts dropped events`() {
        WatchRegistry.register(makeSpec(id = "p2", fireOnUpdate = true))
        repeat(WatchRegistry.MAX_BUFFERED_EVENTS + 5) { i -> WatchRegistry.dispatch(event(key = "k$i")) }
        val polled = WatchRegistry.poll("p2", clear = true)!!
        assertThat(polled.events).hasSize(WatchRegistry.MAX_BUFFERED_EVENTS)
        assertThat(polled.dropped).isEqualTo(5)
        assertThat(polled.events.first().key).isEqualTo("k5")
    }

    @Test
    fun `dispatch after unregister does not resurrect watch state`() {
        WatchRegistry.register(makeSpec(id = "gone"))
        WatchRegistry.unregister("gone")
        WatchRegistry.dispatch(event())
        assertThat(WatchRegistry.firedCount("gone")).isEqualTo(0)
        assertThat(WatchRegistry.poll("gone", clear = true)).isNull()
    }

    @Test
    fun `register refuses beyond the active watch cap`() {
        repeat(WatchRegistry.MAX_WATCHES) { i ->
            assertThat(WatchRegistry.register(makeSpec(id = "w$i"))).isTrue()
        }
        assertThat(WatchRegistry.register(makeSpec(id = "overflow"))).isFalse()
        assertThat(WatchRegistry.get("overflow")).isNull()
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
