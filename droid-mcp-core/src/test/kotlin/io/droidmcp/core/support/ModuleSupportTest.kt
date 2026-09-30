package io.droidmcp.core.support

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.util.TimeZone

class ModuleSupportTest {

    @Test
    fun `escape makes LIKE wildcards literal`() {
        assertThat(SqlLike.escape("50%_off\\")).isEqualTo("50\\%\\_off\\\\")
        assertThat(SqlLike.escape("plain")).isEqualTo("plain")
    }

    @Test
    fun `strict parse rejects impossible dates, overflow and trailing text`() {
        val utc = TimeZone.getTimeZone("UTC")
        assertThat(StrictDates.parse("yyyy-MM-dd", "2026-02-28", utc)).isNotNull()
        assertThat(StrictDates.parse("yyyy-MM-dd", " 2026-02-28 ", utc)).isNotNull()
        assertThat(StrictDates.parse("yyyy-MM-dd", "2026-02-30", utc)).isNull()
        assertThat(StrictDates.parse("yyyy-MM-dd", "2026-02-28x", utc)).isNull()
        assertThat(StrictDates.parse("yyyy-MM-dd HH:mm", "2026-02-28 24:00", utc)).isNull()
        assertThat(StrictDates.parse("yyyy-MM-dd HH:mm", "2026-02-28 23:59", utc)!!.time)
            .isEqualTo(1772323140000L)
    }
}
