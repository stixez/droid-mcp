package io.droidmcp.core

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Test

/** Dispatch, timeout, and cancellation semantics of [ToolRegistry.executeTool]. */
class ToolRegistryExecutionTest {

    private fun tool(toolName: String, body: suspend () -> ToolResult) = object : McpTool {
        override val name = toolName
        override val description = "test"
        override val parameters = emptyList<ToolParameter>()
        override suspend fun execute(params: Map<String, Any>): ToolResult = body()
    }

    @Test
    fun `tools run off the calling thread`() = runBlocking {
        val caller = Thread.currentThread()
        var toolThread: Thread? = null
        val registry = ToolRegistry().apply {
            register(tool("where") { toolThread = Thread.currentThread(); ToolResult.success(mapOf()) })
        }
        registry.executeTool("where", emptyMap())
        assertThat(toolThread).isNotSameInstanceAs(caller)
    }

    @Test
    fun `an overrunning tool yields tool_timeout`() = runBlocking {
        val registry = ToolRegistry(toolTimeoutMs = 50).apply {
            register(tool("hang") { awaitCancellation() })
        }
        val result = registry.executeTool("hang", emptyMap())
        assertThat(result.isSuccess).isFalse()
        assertThat(result.errorMessage).contains("tool_timeout")
    }

    @Test
    fun `caller cancellation propagates instead of becoming an error result`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val registry = ToolRegistry().apply {
            register(tool("hang") { started.complete(Unit); awaitCancellation() })
        }
        val job = async { registry.executeTool("hang", emptyMap()) }
        started.await()
        job.cancelAndJoin()
        assertThat(job.isCancelled).isTrue()
    }

    @Test
    fun `a tool-internal cancellation is reported, not rethrown`() = runBlocking {
        val registry = ToolRegistry().apply {
            register(tool("inner") { withTimeout(1) { awaitCancellation() } })
        }
        val result = registry.executeTool("inner", emptyMap())
        assertThat(result.isSuccess).isFalse()
    }

    @Test
    fun `a thrown CancellationException from a live caller is reported`() = runBlocking {
        val registry = ToolRegistry().apply {
            register(tool("boom") { throw CancellationException("tool bug") })
        }
        assertThat(registry.executeTool("boom", emptyMap()).isSuccess).isFalse()
    }
}
