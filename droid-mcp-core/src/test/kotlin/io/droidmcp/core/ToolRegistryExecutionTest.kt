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

    private fun destructive(toolName: String, onRun: () -> Unit) = object : McpTool {
        override val name = toolName
        override val description = "test"
        override val parameters = emptyList<ToolParameter>()
        override val annotations = ToolAnnotations(destructiveHint = true)
        override suspend fun execute(params: Map<String, Any>): ToolResult {
            onRun()
            return ToolResult.success(mapOf("ok" to true))
        }
    }

    @Test
    fun `an approved destructive call runs and the confirmer sees the request`() = runBlocking {
        var seen: ToolCallRequest? = null
        var ran = false
        val registry = ToolRegistry(confirmer = { seen = it; true }).apply { register(destructive("send") { ran = true }) }
        val result = registry.executeTool("send", mapOf("to" to "x"), clientLabel = "laptop")
        assertThat(result.isSuccess).isTrue()
        assertThat(ran).isTrue()
        assertThat(seen).isEqualTo(ToolCallRequest("send", mapOf("to" to "x"), ToolAnnotations(destructiveHint = true), "laptop"))
    }

    @Test
    fun `a declined call never runs`() = runBlocking {
        var ran = false
        val registry = ToolRegistry(confirmer = { false }).apply { register(destructive("send") { ran = true }) }
        val result = registry.executeTool("send", emptyMap())
        assertThat(result.errorMessage).startsWith("tool_call_declined")
        assertThat(ran).isFalse()
    }

    @Test
    fun `a confirmer that throws or never answers declines`() = runBlocking {
        var ran = false
        val throwing = ToolRegistry(confirmer = { error("boom") }).apply { register(destructive("a") { ran = true }) }
        assertThat(throwing.executeTool("a", emptyMap()).errorMessage).startsWith("tool_call_declined")
        val silent = ToolRegistry(toolTimeoutMs = 50, confirmer = { awaitCancellation() }).apply { register(destructive("b") { ran = true }) }
        assertThat(silent.executeTool("b", emptyMap()).errorMessage).startsWith("tool_call_declined")
        assertThat(ran).isFalse()
    }

    @Test
    fun `non-destructive tools skip confirmation by default`() = runBlocking {
        val registry = ToolRegistry(confirmer = { error("must not be asked") }).apply {
            register(tool("read") { ToolResult.success(mapOf()) })
        }
        assertThat(registry.executeTool("read", emptyMap()).isSuccess).isTrue()
    }
}
