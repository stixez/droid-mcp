package io.droidmcp.core

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class OutputSchemaTest {

    private val schema = OutputSchema.obj {
        integer("count")
        string("next", nullable = true)
        boolean("truncated", optional = true)
        array("events") {
            integer("id")
            string("title", nullable = true)
        }
        arrayOf("tags", OutputSchema.string())
        obj("window") {
            number("score")
        }
    }

    @Test
    fun `fields are required unless optional`() {
        assertThat(schema["required"] as List<*>).containsExactly("count", "next", "events", "tags", "window").inOrder()
    }

    @Test
    fun `nullable fields allow null in their type`() {
        val next = (schema["properties"] as Map<*, *>)["next"] as Map<*, *>
        assertThat(next["type"]).isEqualTo(listOf("string", "null"))
    }

    @Test
    fun `array of objects nests an object schema under items`() {
        val events = (schema["properties"] as Map<*, *>)["events"] as Map<*, *>
        assertThat(events["type"]).isEqualTo("array")
        val items = events["items"] as Map<*, *>
        assertThat(items["type"]).isEqualTo("object")
        assertThat(items["required"] as List<*>).containsExactly("id", "title")
    }

    @Test
    fun `duplicate field names are rejected`() {
        assertThrows<IllegalArgumentException> {
            OutputSchema.obj {
                string("a")
                integer("a")
            }
        }
    }

    @Test
    fun `conforming result has no violations`() {
        val result = mapOf(
            "count" to 2,
            "next" to null,
            "events" to listOf(mapOf("id" to 1L, "title" to "Standup"), mapOf("id" to 2, "title" to null)),
            "tags" to listOf("a", "b"),
            "window" to mapOf("score" to 0.5),
        )
        assertThat(OutputSchema.violations(schema, result)).isEmpty()
    }

    @Test
    fun `integer satisfies number`() {
        val result = mapOf("count" to 0, "next" to "x", "events" to emptyList<Any>(), "tags" to emptyList<Any>(), "window" to mapOf("score" to 3))
        assertThat(OutputSchema.violations(schema, result)).isEmpty()
    }

    @Test
    fun `missing keys, wrong types and bad items are reported with paths`() {
        val result = mapOf(
            "count" to "two",
            "events" to listOf(mapOf("title" to 5)),
            "tags" to listOf("a", 1),
            "window" to mapOf("score" to 1.5),
        )
        assertThat(OutputSchema.violations(schema, result)).containsExactly(
            "$.next: missing required key",
            "$.count: expected integer, got string",
            "$.events[0].id: missing required key",
            "$.events[0].title: expected string|null, got integer",
            "$.tags[1]: expected string, got integer",
        )
    }

    @Test
    fun `optional key may be absent but must match when present`() {
        val base = mapOf("count" to 1, "next" to null, "events" to emptyList<Any>(), "tags" to emptyList<Any>(), "window" to mapOf("score" to 1))
        assertThat(OutputSchema.violations(schema, base)).isEmpty()
        assertThat(OutputSchema.violations(schema, base + ("truncated" to "yes")))
            .containsExactly("$.truncated: expected boolean, got string")
    }
}
