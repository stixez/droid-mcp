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
}
