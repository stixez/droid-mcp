package io.droidmcp.apps

import com.google.common.truth.Truth.assertThat
import io.droidmcp.apps.UsageTransition.Kind
import io.mockk.mockk
import org.junit.jupiter.api.Test

class ScreenTimeTest {

    private fun fg(pkg: String, at: Long, cls: String = "Main") = UsageTransition(Kind.FOREGROUND, at, pkg, cls)
    private fun bg(pkg: String, at: Long, cls: String = "Main") = UsageTransition(Kind.BACKGROUND, at, pkg, cls)
    private fun stopAll(at: Long) = UsageTransition(Kind.STOP_ALL, at)
    private fun startup(at: Long) = UsageTransition(Kind.STARTUP, at)

    @Test
    fun `sums resumed stretches per app, most used first`() {
        val result = ScreenTime.aggregate(
            listOf(fg("a", 10), bg("a", 20), fg("b", 20), bg("b", 50), fg("a", 60), bg("a", 70)),
            start = 0, end = 100,
        )
        assertThat(result).containsExactly(
            AppScreenTime("b", 30, 50),
            AppScreenTime("a", 20, 70),
        ).inOrder()
    }

    @Test
    fun `switching activities inside one app is one stretch`() {
        val result = ScreenTime.aggregate(
            listOf(fg("a", 10, "One"), fg("a", 15, "Two"), bg("a", 16, "One"), bg("a", 40, "Two")),
            start = 0, end = 100,
        )
        assertThat(result.single().foregroundMillis).isEqualTo(30)
    }

    @Test
    fun `app open at window start counts from start, app still open counts to end`() {
        val result = ScreenTime.aggregate(listOf(bg("a", 30), fg("b", 30)), start = 0, end = 100)
        assertThat(result.associate { it.packageName to it.foregroundMillis }).containsExactly("b", 70L, "a", 30L)
        assertThat(result.first { it.packageName == "b" }.lastUsed).isEqualTo(100)
    }

    @Test
    fun `app resumed during the lookback counts from the window start`() {
        val result = ScreenTime.aggregate(listOf(fg("video", -30)), start = 0, end = 20)
        assertThat(result).containsExactly(AppScreenTime("video", 20, 20))
    }

    @Test
    fun `screen off and shutdown close every open app`() {
        val result = ScreenTime.aggregate(
            listOf(fg("a", 10), stopAll(15), fg("b", 50), stopAll(60), fg("b", 90), bg("b", 95)),
            start = 0, end = 100,
        )
        assertThat(result.associate { it.packageName to it.foregroundMillis }).containsExactly("b", 15L, "a", 5L)
    }

    @Test
    fun `a boot without shutdown ends open apps at the last event before it`() {
        // The phone died at about 12 with "a" open; after booting at 50, "a" is used 60-61.
        val result = ScreenTime.aggregate(
            listOf(fg("a", 10), fg("b", 11), bg("b", 12), startup(50), fg("a", 60), bg("a", 61)),
            start = 0, end = 100,
        )
        assertThat(result.first { it.packageName == "a" }.foregroundMillis).isEqualTo(3)
    }

    @Test
    fun `unmatched pause after earlier events is ignored and zero time apps are dropped`() {
        val result = ScreenTime.aggregate(listOf(fg("a", 10), bg("a", 20), bg("a", 30, "Other"), fg("z", 50), bg("z", 50)), 0, 100)
        assertThat(result).containsExactly(AppScreenTime("a", 10, 30))
    }

    @Test
    fun `tool declares bounded params and is read only`() {
        val tool = GetAppUsageTool(mockk(relaxed = true))
        val byName = tool.parameters.associateBy { it.name }
        assertThat(byName.keys).containsExactly("days", "limit", "package_name")
        assertThat(byName.getValue("days").maximum).isEqualTo(7.0)
        assertThat(tool.annotations.readOnlyHint).isTrue()
    }
}
