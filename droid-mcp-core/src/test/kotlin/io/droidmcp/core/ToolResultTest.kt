package io.droidmcp.core

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class ToolResultTest {

    @Test
    fun `success result contains data`() {
        val result = ToolResult.success(mapOf("events" to listOf("Meeting")))
        assertThat(result.isSuccess).isTrue()
        assertThat(result.data).containsKey("events")
    }

    @Test
    fun `error result contains message`() {
        val result = ToolResult.error("Permission denied")
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).isEqualTo("Permission denied")
    }

    @Test
    fun `success result has null error`() {
        val result = ToolResult.success(mapOf("ok" to true))
        assertThat(result.errorMessage).isNull()
    }

    @Test
    fun `error result has null data`() {
        val result = ToolResult.error("fail")
        assertThat(result.data).isNull()
    }

    @Test
    fun `withImage keeps data intact and records the image`() {
        val result = ToolResult.success(mapOf("img" to "AAAA", "n" to 1)).withImage("img", "image/jpeg")
        assertThat(result.data).containsEntry("img", "AAAA")
        assertThat(result.images).containsExactly(ToolImage("AAAA", "image/jpeg", "img"))
    }

    @Test
    fun `withImage is a no-op when the key is missing or not a string`() {
        val base = ToolResult.success(mapOf("n" to 1))
        assertThat(base.withImage("img", "image/png").images).isEmpty()
        assertThat(base.withImage("n", "image/png").images).isEmpty()
        assertThat(ToolResult.error("x").withImage("img", "image/png").images).isEmpty()
    }
}
