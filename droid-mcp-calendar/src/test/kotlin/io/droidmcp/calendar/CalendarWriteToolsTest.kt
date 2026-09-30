package io.droidmcp.calendar

import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/** Input validation of update_event / delete_event (a relaxed Context's provider finds no rows). */
class CalendarWriteToolsTest {

    private val update = UpdateEventTool(mockk(relaxed = true))
    private val delete = DeleteEventTool(mockk(relaxed = true))

    @Test
    fun `update_event validates event_id and requires a field`() = runTest {
        assertThat(update.execute(emptyMap()).errorMessage).contains("event_id is required")
        assertThat(update.execute(mapOf("event_id" to 0, "title" to "x")).errorMessage).contains("event_id is required")
        assertThat(update.execute(mapOf("event_id" to 5)).errorMessage).startsWith("Nothing to update")
        assertThat(update.execute(mapOf("event_id" to 5, "title" to "  ")).errorMessage).isEqualTo("title must not be blank")
        assertThat(update.execute(mapOf("event_id" to 5, "all_day" to "yes")).errorMessage).isEqualTo("all_day must be true or false")
    }

    @Test
    fun `update_event and delete_event report missing events`() = runTest {
        assertThat(update.execute(mapOf("event_id" to 5, "title" to "New")).errorMessage).isEqualTo("Event not found with ID: 5")
        assertThat(delete.execute(mapOf("event_id" to "5")).errorMessage).isEqualTo("Event not found with ID: 5")
        assertThat(delete.execute(mapOf("event_id" to "abc")).errorMessage).contains("event_id is required")
    }

    @Test
    fun `write tools advertise destructive and idempotent hints`() {
        listOf(update, delete).forEach {
            assertThat(it.annotations.destructiveHint).isTrue()
            assertThat(it.annotations.idempotentHint).isTrue()
            assertThat(it.annotations.readOnlyHint).isFalse()
        }
    }
}
