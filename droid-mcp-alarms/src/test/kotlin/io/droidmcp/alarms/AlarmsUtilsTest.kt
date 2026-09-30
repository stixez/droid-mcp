package io.droidmcp.alarms

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test
import java.util.Calendar

class AlarmsUtilsTest {

    private fun fields(input: String): List<Int>? = AlarmsUtils.parseDateTime(input)?.let {
        val c = Calendar.getInstance().apply { time = it }
        listOf(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH),
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    @Test
    fun `parses valid date-times in the device timezone`() {
        assertThat(fields("2026-03-01 07:30")).containsExactly(2026, 3, 1, 7, 30).inOrder()
        assertThat(fields("2026-12-31 23:59")).containsExactly(2026, 12, 31, 23, 59).inOrder()
        assertThat(fields("2024-02-29 00:00")).containsExactly(2024, 2, 29, 0, 0).inOrder()
        assertThat(fields("  2026-03-01 07:30\n")).containsExactly(2026, 3, 1, 7, 30).inOrder()
    }

    @Test
    fun `rejects impossible dates and times instead of rolling them over`() {
        listOf("2026-02-30 10:00", "2026-02-29 10:00", "2026-13-01 10:00", "2026-04-31 10:00",
            "2026-03-01 24:00", "2026-03-01 25:00", "2026-03-01 10:60", "2026-03-01 -1:00")
            .forEach { assertWithMessage(it).that(AlarmsUtils.parseDateTime(it)).isNull() }
    }

    @Test
    fun `rejects partial input, trailing garbage and other formats`() {
        listOf("2026-03-01", "2026-03-01 10", "2026-03-01 10:00:00", "2026-03-01 10:00x", "2026-03-01T10:00",
            "2026-03-01 10:00 PM", "03/01/2026 10:00", "", "  ")
            .forEach { assertWithMessage(it).that(AlarmsUtils.parseDateTime(it)).isNull() }
    }
}
