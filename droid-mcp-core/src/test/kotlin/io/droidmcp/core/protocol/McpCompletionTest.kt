package io.droidmcp.core.protocol

import com.google.common.truth.Truth.assertThat
import io.droidmcp.core.BuiltInPrompts
import io.droidmcp.core.CompletionContext
import io.droidmcp.core.McpPrompt
import io.droidmcp.core.McpResourceTemplate
import io.droidmcp.core.McpTool
import io.droidmcp.core.PromptArgument
import io.droidmcp.core.PromptRegistry
import io.droidmcp.core.PromptResult
import io.droidmcp.core.ResourceContents
import io.droidmcp.core.ResourceProvider
import io.droidmcp.core.ResourceRegistry
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolCallAudit
import io.droidmcp.core.ToolCallConfirmer
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolRegistry
import io.droidmcp.core.ToolResult
import io.droidmcp.core.support.CompletionSupport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.time.LocalDate

/** `completion/complete` in [McpProtocolImpl], and the built-in completers. */
class McpCompletionTest {

    private fun tool(name: String, readOnly: Boolean = true, data: Map<String, Any?> = mapOf("ran" to name)) = object : McpTool {
        override val name = name
        override val description = name
        override val parameters = emptyList<ToolParameter>()
        override val annotations = ToolAnnotations(readOnlyHint = readOnly, destructiveHint = !readOnly)
        override suspend fun execute(params: Map<String, Any>) = ToolResult.success(data)
    }

    /** Completes `fruit` from a fixed list; `tool:<name>` reports what [CompletionContext.callTool] returned. */
    private val fruitPrompt = object : McpPrompt {
        override val name = "fruit"
        override val description = "pick a fruit"
        override val arguments = listOf(PromptArgument("fruit", "a fruit"), PromptArgument("tool", "a tool to call"))
        override suspend fun render(arguments: Map<String, String>) = PromptResult(emptyList())
        override suspend fun complete(argument: String, value: String, context: CompletionContext): List<String> = when (argument) {
            "fruit" -> listOf("apple", "apricot", "banana", "apple").filter { it.startsWith(value) } + context.arguments.values
            "tool" -> listOf(context.callTool(value, emptyMap())?.data?.get("ran")?.toString() ?: "none")
            "many" -> (1..150).map { "v$it" }
            else -> emptyList()
        }
    }

    private val notes = object : ResourceProvider {
        override val templates = listOf(McpResourceTemplate("note://{n}", "note"))
        override fun handles(uri: String) = false
        override suspend fun read(uri: String): List<ResourceContents>? = null
        override suspend fun complete(uriTemplate: String, argument: String, value: String, context: CompletionContext) =
            listOf("1", "2", "10").filter { it.startsWith(value) }
    }

    private fun protocol(
        tools: List<McpTool> = emptyList(),
        prompts: List<McpPrompt> = listOf(fruitPrompt),
        confirmer: ToolCallConfirmer? = null,
        audits: MutableList<ToolCallAudit>? = null,
    ) = McpProtocolImpl(
        ToolRegistry(confirmer = confirmer).apply { tools.forEach { register(it) } },
        auditSink = audits?.let { list -> { list += it } },
        resources = ResourceRegistry().apply { register(notes) },
        prompts = PromptRegistry().apply { registerAll(prompts) },
    )

    private suspend fun McpProtocolImpl.call(method: String, params: String = "{}"): JsonObject =
        Json.parseToJsonElement(handleMessage("""{"jsonrpc":"2.0","id":3,"method":"$method","params":$params}""")).jsonObject

    private suspend fun McpProtocolImpl.complete(ref: String, name: String, value: String, extra: String = ""): JsonObject =
        call("completion/complete", """{"ref":$ref,"argument":{"name":"$name","value":"$value"}$extra}""")

    private fun JsonObject.values(): List<String> =
        this["result"]!!.jsonObject["completion"]!!.jsonObject["values"]!!.jsonArray.map { it.jsonPrimitive.content }

    private val fruitRef = """{"type":"ref/prompt","name":"fruit"}"""

    @Test
    fun `initialize advertises completions`() = runTest {
        val caps = protocol().call("initialize", """{"protocolVersion":"2025-06-18"}""")["result"]!!.jsonObject["capabilities"]!!.jsonObject
        assertThat(caps).containsKey("completions")
    }

    @Test
    fun `prompt argument completes by prefix, without duplicates, with context arguments`() = runTest {
        val p = protocol()
        assertThat(p.complete(fruitRef, "fruit", "ap").values()).containsExactly("apple", "apricot").inOrder()
        assertThat(p.complete(fruitRef, "fruit", "ban", ""","context":{"arguments":{"other":"kiwi"}}""").values())
            .containsExactly("banana", "kiwi").inOrder()
        assertThat(p.complete(fruitRef, "colour", "r").values()).isEmpty()
    }

    @Test
    fun `values are capped at 100 with total and hasMore`() = runTest {
        val completion = protocol().complete(fruitRef, "many", "")["result"]!!.jsonObject["completion"]!!.jsonObject
        assertThat(completion["values"]!!.jsonArray).hasSize(100)
        assertThat(completion["total"]!!.jsonPrimitive.int).isEqualTo(150)
        assertThat(completion["hasMore"]!!.jsonPrimitive.boolean).isTrue()
    }

    @Test
    fun `resource template variable completes`() = runTest {
        val p = protocol()
        assertThat(p.complete("""{"type":"ref/resource","uri":"note://{n}"}""", "n", "1").values()).containsExactly("1", "10")
    }

    @Test
    fun `bad refs are invalid params`() = runTest {
        val p = protocol()
        for (ref in listOf(
            """{"type":"ref/prompt","name":"nope"}""",
            """{"type":"ref/resource","uri":"other://{x}"}""",
            """{"type":"ref/tool","name":"fruit"}""",
        )) {
            assertThat(p.complete(ref, "fruit", "")["error"]!!.jsonObject["code"]!!.jsonPrimitive.int).isEqualTo(-32602)
        }
        assertThat(p.call("completion/complete", """{"ref":$fruitRef}""")["error"]!!.jsonObject["code"]!!.jsonPrimitive.int).isEqualTo(-32602)
    }

    @Test
    fun `completions read only through visible read-only tools, and are audited`() = runTest {
        val audits = mutableListOf<ToolCallAudit>()
        val p = protocol(tools = listOf(tool("reader"), tool("writer", readOnly = false)), audits = audits)
        assertThat(p.complete(fruitRef, "tool", "reader").values()).containsExactly("reader")
        assertThat(p.complete(fruitRef, "tool", "writer").values()).containsExactly("none")
        assertThat(p.complete(fruitRef, "tool", "missing").values()).containsExactly("none")
        assertThat(audits.map { it.toolName }).containsExactly(McpProtocolImpl.COMPLETION_COMPLETE)
        assertThat(audits.single().argumentsJson).contains("\"tool\":\"reader\"")
    }

    @Test
    fun `completions skip tools the host confirms`() = runTest {
        val p = protocol(
            tools = listOf(tool("reader")),
            confirmer = ToolCallConfirmer { true },
        )
        // The default rule confirms only destructive tools, so a read-only one still runs.
        assertThat(p.complete(fruitRef, "tool", "reader").values()).containsExactly("reader")
        val strict = McpProtocolImpl(
            ToolRegistry(confirmer = { true }, requiresConfirmation = { true }).apply { register(tool("reader")) },
            prompts = PromptRegistry().apply { register(fruitPrompt) },
        )
        assertThat(strict.complete(fruitRef, "tool", "reader").values()).containsExactly("none")
    }

    @Test
    fun `a slow completer times out with no values`() = runTest {
        val slow = object : McpPrompt {
            override val name = "slow"
            override val description = "never answers"
            override suspend fun render(arguments: Map<String, String>) = PromptResult(emptyList())
            override suspend fun complete(argument: String, value: String, context: CompletionContext): List<String> {
                awaitCancellation()
            }
        }
        val p = protocol(prompts = listOf(slow))
        val started = System.nanoTime()
        assertThat(p.complete("""{"type":"ref/prompt","name":"slow"}""", "x", "").values()).isEmpty()
        assertThat((System.nanoTime() - started) / 1_000_000).isAtLeast(McpProtocolImpl.COMPLETION_TIMEOUT_MS - 100)
    }

    @Test
    fun `notifications cancelled stops an in-flight completion with no reply`() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val blocking = object : McpPrompt {
            override val name = "blocking"
            override val description = "waits"
            override suspend fun render(arguments: Map<String, String>) = PromptResult(emptyList())
            override suspend fun complete(argument: String, value: String, context: CompletionContext): List<String> {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        val p = protocol(prompts = listOf(blocking))
        val reply = async {
            p.handleMessage("""{"jsonrpc":"2.0","id":9,"method":"completion/complete","params":{"ref":{"type":"ref/prompt","name":"blocking"},"argument":{"name":"x","value":""}}}""")
        }
        started.await()
        p.handleMessage("""{"jsonrpc":"2.0","method":"notifications/cancelled","params":{"requestId":9}}""")
        assertThat(withTimeout(2_000) { reply.await() }).isEmpty()
    }

    @Test
    fun `a completion cut short while reading through a tool is still audited`() = runBlocking {
        val audits = mutableListOf<ToolCallAudit>()
        val reading = CompletableDeferred<Unit>()
        val slowReader = object : McpTool {
            override val name = "slow_reader"
            override val description = "slow"
            override val parameters = emptyList<ToolParameter>()
            override val annotations = ToolAnnotations(readOnlyHint = true)
            override suspend fun execute(params: Map<String, Any>): ToolResult {
                reading.complete(Unit)
                awaitCancellation()
            }
        }
        val p = protocol(tools = listOf(slowReader), audits = audits)
        val reply = async {
            p.handleMessage("""{"jsonrpc":"2.0","id":4,"method":"completion/complete","params":{"ref":$fruitRef,"argument":{"name":"tool","value":"slow_reader"}}}""")
        }
        reading.await()
        p.handleMessage("""{"jsonrpc":"2.0","method":"notifications/cancelled","params":{"requestId":4}}""")
        withTimeout(2_000) { reply.await() }
        assertThat(audits.single().toolName).isEqualTo(McpProtocolImpl.COMPLETION_COMPLETE)
        assertThat(audits.single().success).isFalse()
    }

    @Test
    fun `daily briefing completes dates from today`() = runTest {
        val today = LocalDate.now()
        val all = BuiltInPrompts.DailyBriefing.complete("date", "", CompletionContext())
        assertThat(all.first()).isEqualTo(today.toString())
        assertThat(all).hasSize(8)
        assertThat(BuiltInPrompts.DailyBriefing.complete("date", today.toString(), CompletionContext())).containsExactly(today.toString())
        assertThat(BuiltInPrompts.DailyBriefing.complete("date", "x", CompletionContext())).isEmpty()
    }

    @Test
    fun `upcomingDates lists today, the next six days, then yesterday`() {
        val dates = CompletionSupport.upcomingDates(" 2026-1", today = LocalDate.of(2026, 10, 30))
        assertThat(dates).containsExactly(
            "2026-10-30", "2026-10-31", "2026-11-01", "2026-11-02", "2026-11-03", "2026-11-04", "2026-11-05", "2026-10-29",
        ).inOrder()
        assertThat(CompletionSupport.upcomingDates("2026-11", today = LocalDate.of(2026, 10, 30))).hasSize(5)
    }

    @Test
    fun `message contact completes names through search_contacts`() = runTest {
        val searchContacts = tool(
            "search_contacts",
            data = mapOf("contacts" to listOf(mapOf("name" to "Ana Kovač"), mapOf("name" to null), mapOf("name" to "Ana Kovač"), mapOf("name" to "Anabel"))),
        )
        val p = protocol(tools = listOf(searchContacts, tool("send_message", readOnly = false)), prompts = listOf(BuiltInPrompts.MessageContact))
        val ref = """{"type":"ref/prompt","name":"message_contact"}"""
        assertThat(p.complete(ref, "contact", "Ana").values()).containsExactly("Ana Kovač", "Anabel").inOrder()
        assertThat(p.complete(ref, "contact", " ").values()).isEmpty()
        assertThat(p.complete(ref, "intent", "say hi").values()).isEmpty()
    }
}
