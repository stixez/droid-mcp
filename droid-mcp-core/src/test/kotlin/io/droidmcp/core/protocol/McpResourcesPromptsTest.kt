package io.droidmcp.core.protocol

import com.google.common.truth.Truth.assertThat
import io.droidmcp.core.BuiltInPrompts
import io.droidmcp.core.McpPrompt
import io.droidmcp.core.McpResource
import io.droidmcp.core.McpResourceTemplate
import io.droidmcp.core.McpTool
import io.droidmcp.core.PromptArgument
import io.droidmcp.core.PromptMessage
import io.droidmcp.core.PromptRegistry
import io.droidmcp.core.PromptResult
import io.droidmcp.core.ResourceContents
import io.droidmcp.core.ResourceProvider
import io.droidmcp.core.ResourceRegistry
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolCallAudit
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolRegistry
import io.droidmcp.core.ToolResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test

/** Resource and prompt methods in [McpProtocolImpl]. */
class McpResourcesPromptsTest {

    private fun tool(name: String, readOnly: Boolean = true) = object : McpTool {
        override val name = name
        override val description = name
        override val parameters = emptyList<ToolParameter>()
        override val annotations = ToolAnnotations(readOnlyHint = readOnly)
        override suspend fun execute(params: Map<String, Any>) = ToolResult.success(emptyMap())
    }

    /** Serves `note://<n>` for n in 0 until [count]; `note://secret` exists but can't be read. */
    private class Notes(private val count: Int) : ResourceProvider {
        override val templates = listOf(McpResourceTemplate("note://{n}", "note", description = "A note"))
        override suspend fun list() = (0 until count).map { McpResource("note://$it", "note-$it", mimeType = "text/plain", size = 4) }
        override fun handles(uri: String) = uri.startsWith("note://")
        override suspend fun read(uri: String): List<ResourceContents>? = when (val n = uri.removePrefix("note://")) {
            "secret" -> null
            "pic" -> listOf(ResourceContents.Blob(uri, "AAEC", "image/png"))
            else -> n.toIntOrNull()?.takeIf { it < count }?.let { listOf(ResourceContents.Text(uri, "note $it")) }
        }
    }

    private val echoPrompt = object : McpPrompt {
        override val name = "echo_prompt"
        override val description = "echoes"
        override val arguments = listOf(PromptArgument("text", "what to echo", required = true))
        override val requiredTools = setOf("reader")
        override suspend fun render(arguments: Map<String, String>): PromptResult {
            require(arguments.getValue("text") != "bad") { "text can't be 'bad'" }
            return PromptResult(listOf(PromptMessage(PromptMessage.Role.USER, "say ${arguments["text"]}")), "echo")
        }
    }

    private val writerPrompt = object : McpPrompt {
        override val name = "writer_prompt"
        override val description = "needs a mutating tool"
        override val requiredTools = setOf("writer")
        override suspend fun render(arguments: Map<String, String>) = PromptResult(emptyList())
    }

    private fun protocol(notes: Int = 3, readOnly: Boolean = false) = McpProtocolImpl(
        ToolRegistry().apply { register(tool("reader")); register(tool("writer", readOnly = false)) },
        readOnly = readOnly,
        resources = ResourceRegistry().apply { register(Notes(notes)) },
        prompts = PromptRegistry().apply { register(echoPrompt); register(writerPrompt) },
    )

    private suspend fun McpProtocolImpl.call(method: String, params: String = "{}"): JsonObject =
        Json.parseToJsonElement(handleMessage("""{"jsonrpc":"2.0","id":7,"method":"$method","params":$params}""")).jsonObject

    @Test
    fun `resources list returns provider resources`() = runTest {
        val result = protocol().call("resources/list")["result"]!!.jsonObject
        val resources = result["resources"]!!.jsonArray
        assertThat(resources).hasSize(3)
        val first = resources[0].jsonObject
        assertThat(first["uri"]!!.jsonPrimitive.content).isEqualTo("note://0")
        assertThat(first["mimeType"]!!.jsonPrimitive.content).isEqualTo("text/plain")
        assertThat(first["size"]!!.jsonPrimitive.int).isEqualTo(4)
        assertThat(result).doesNotContainKey("nextCursor")
    }

    @Test
    fun `resources list paginates with an opaque cursor`() = runTest {
        val p = protocol(notes = 150)
        val page1 = p.call("resources/list")["result"]!!.jsonObject
        assertThat(page1["resources"]!!.jsonArray).hasSize(100)
        val cursor = page1["nextCursor"]!!.jsonPrimitive.content
        val page2 = p.call("resources/list", """{"cursor":"$cursor"}""")["result"]!!.jsonObject
        assertThat(page2["resources"]!!.jsonArray).hasSize(50)
        assertThat(page2).doesNotContainKey("nextCursor")
    }

    @Test
    fun `later pages come from the first page's snapshot`() = runTest {
        var listings = 0
        val counting = object : ResourceProvider {
            override suspend fun list(): List<McpResource> {
                listings++
                return (0 until 150).map { McpResource("c://$it", "c$it") }
            }
            override fun handles(uri: String) = false
            override suspend fun read(uri: String): List<ResourceContents>? = null
        }
        val p = McpProtocolImpl(ToolRegistry(), resources = ResourceRegistry().apply { register(counting) })
        val cursor = p.call("resources/list")["result"]!!.jsonObject["nextCursor"]!!.jsonPrimitive.content
        val page2 = p.call("resources/list", """{"cursor":"$cursor"}""")["result"]!!.jsonObject
        assertThat(page2["resources"]!!.jsonArray.first().jsonObject["uri"]!!.jsonPrimitive.content).isEqualTo("c://100")
        assertThat(listings).isEqualTo(1)
    }

    @Test
    fun `later pages drop resources whose provider was gated off`() = runTest {
        val tools = ToolRegistry().apply { register(tool("reader")) }
        val gated = object : ResourceProvider {
            override val requiredTools = setOf("reader")
            override suspend fun list() = (0 until 150).map { McpResource("g://$it", "g$it") }
            override fun handles(uri: String) = false
            override suspend fun read(uri: String): List<ResourceContents>? = null
        }
        val p = McpProtocolImpl(tools, resources = ResourceRegistry().apply { register(gated) })
        val cursor = p.call("resources/list")["result"]!!.jsonObject["nextCursor"]!!.jsonPrimitive.content
        tools.setToolEnabled("reader", false)
        val page2 = p.call("resources/list", """{"cursor":"$cursor"}""")["result"]!!.jsonObject
        assertThat(page2["resources"]!!.jsonArray).isEmpty()
    }

    @Test
    fun `a declined confirmation blocks the resource read`() = runTest {
        val asked = mutableListOf<String>()
        val tools = ToolRegistry(
            confirmer = { request -> asked += request.toolName; false },
            requiresConfirmation = { it.name == "reader" },
        ).apply { register(tool("reader")) }
        val p = McpProtocolImpl(tools, resources = ResourceRegistry().apply { register(Gated()) })
        val error = p.call("resources/read", """{"uri":"gated://x"}""")["error"]!!.jsonObject
        assertThat(error["code"]!!.jsonPrimitive.int).isEqualTo(-32001)
        assertThat(asked).containsExactly("reader")
    }

    @Test
    fun `a provider's own leaked timeout is an error, not a dropped request`() = runTest {
        val leaky = object : ResourceProvider {
            override fun handles(uri: String) = true
            override suspend fun read(uri: String): List<ResourceContents>? =
                withTimeout(1) { delay(1_000); null }
        }
        val p = McpProtocolImpl(ToolRegistry(), resources = ResourceRegistry().apply { register(leaky) })
        val error = p.call("resources/read", """{"uri":"any://x"}""")["error"]!!.jsonObject
        assertThat(error["code"]!!.jsonPrimitive.int).isEqualTo(-32002)
    }

    @Test
    fun `a bad cursor is invalid params`() = runTest {
        val error = protocol().call("resources/list", """{"cursor":"nope"}""")["error"]!!.jsonObject
        assertThat(error["code"]!!.jsonPrimitive.int).isEqualTo(-32602)
    }

    @Test
    fun `templates are listed`() = runTest {
        val templates = protocol().call("resources/templates/list")["result"]!!.jsonObject["resourceTemplates"]!!.jsonArray
        assertThat(templates.single().jsonObject["uriTemplate"]!!.jsonPrimitive.content).isEqualTo("note://{n}")
    }

    @Test
    fun `read returns text and blob contents`() = runTest {
        val p = protocol()
        val text = p.call("resources/read", """{"uri":"note://1"}""")["result"]!!.jsonObject["contents"]!!.jsonArray.single().jsonObject
        assertThat(text["text"]!!.jsonPrimitive.content).isEqualTo("note 1")
        assertThat(text["mimeType"]!!.jsonPrimitive.content).isEqualTo("text/plain")

        val blob = p.call("resources/read", """{"uri":"note://pic"}""")["result"]!!.jsonObject["contents"]!!.jsonArray.single().jsonObject
        assertThat(blob["blob"]!!.jsonPrimitive.content).isEqualTo("AAEC")
        assertThat(blob).doesNotContainKey("text")
    }

    @Test
    fun `unknown or unreadable resources are -32002 with the uri`() = runTest {
        val p = protocol()
        for (uri in listOf("other://x", "note://secret", "note://99")) {
            val error = p.call("resources/read", """{"uri":"$uri"}""")["error"]!!.jsonObject
            assertThat(error["code"]!!.jsonPrimitive.int).isEqualTo(-32002)
            assertThat(error["data"]!!.jsonObject["uri"]!!.jsonPrimitive.content).isEqualTo(uri)
        }
    }

    /** Serves `gated://x`, but only while the `reader` tool is visible. */
    private class Gated : ResourceProvider {
        override val requiredTools = setOf("reader")
        override val templates = listOf(McpResourceTemplate("gated://{x}", "gated"))
        override suspend fun list() = listOf(McpResource("gated://x", "x"))
        override fun handles(uri: String) = uri.startsWith("gated://")
        override suspend fun read(uri: String) = listOf(ResourceContents.Text(uri, "secret"))
    }

    @Test
    fun `gating a provider's required tool hides its resources`() = runTest {
        val tools = ToolRegistry().apply { register(tool("reader")) }
        val p = McpProtocolImpl(tools, resources = ResourceRegistry().apply { register(Gated()) })
        assertThat(p.call("resources/list")["result"]!!.jsonObject["resources"]!!.jsonArray).hasSize(1)

        tools.setToolEnabled("reader", false)
        assertThat(p.call("resources/list")["result"]!!.jsonObject["resources"]!!.jsonArray).isEmpty()
        assertThat(p.call("resources/templates/list")["result"]!!.jsonObject["resourceTemplates"]!!.jsonArray).isEmpty()
        val error = p.call("resources/read", """{"uri":"gated://x"}""")["error"]!!.jsonObject
        assertThat(error["code"]!!.jsonPrimitive.int).isEqualTo(-32002)
    }

    @Test
    fun `read-only mode hides providers that need a mutating tool`() = runTest {
        val tools = ToolRegistry().apply { register(tool("reader", readOnly = false)) }
        val p = McpProtocolImpl(tools, readOnly = true, resources = ResourceRegistry().apply { register(Gated()) })
        assertThat(p.call("resources/list")["result"]!!.jsonObject["resources"]!!.jsonArray).isEmpty()
    }

    @Test
    fun `a throwing provider is not found on read and skipped on list`() = runTest {
        val broken = object : ResourceProvider {
            override suspend fun list(): List<McpResource> = error("boom")
            override fun handles(uri: String) = uri.startsWith("broken://")
            override suspend fun read(uri: String): List<ResourceContents>? = throw SecurityException("no permission")
        }
        val p = McpProtocolImpl(ToolRegistry(), resources = ResourceRegistry().apply { register(broken); register(Notes(2)) })
        assertThat(p.call("resources/list")["result"]!!.jsonObject["resources"]!!.jsonArray).hasSize(2)
        val error = p.call("resources/read", """{"uri":"broken://1"}""")["error"]!!.jsonObject
        assertThat(error["code"]!!.jsonPrimitive.int).isEqualTo(-32002)
    }

    @Test
    fun `resource reads are audited`() = runTest {
        val entries = mutableListOf<ToolCallAudit>()
        val p = McpProtocolImpl(
            ToolRegistry(),
            auditSink = { entries += it },
            resources = ResourceRegistry().apply { register(Notes(2)) },
        )
        p.handleMessage("""{"jsonrpc":"2.0","id":1,"method":"resources/read","params":{"uri":"note://1"}}""", "laptop")
        p.handleMessage("""{"jsonrpc":"2.0","id":2,"method":"resources/read","params":{"uri":"note://9"}}""", "laptop")
        assertThat(entries.map { it.toolName }).containsExactly("resources/read", "resources/read")
        assertThat(entries.map { it.success }).containsExactly(true, false).inOrder()
        assertThat(entries[0].clientLabel).isEqualTo("laptop")
        assertThat(entries[0].argumentsJson).contains("note://1")
    }

    @Test
    fun `read without a uri is invalid params`() = runTest {
        assertThat(protocol().call("resources/read")["error"]!!.jsonObject["code"]!!.jsonPrimitive.int).isEqualTo(-32602)
    }

    @Test
    fun `prompts list includes arguments`() = runTest {
        val prompts = protocol().call("prompts/list")["result"]!!.jsonObject["prompts"]!!.jsonArray
        assertThat(prompts.map { it.jsonObject["name"]!!.jsonPrimitive.content }).containsExactly("echo_prompt", "writer_prompt")
        val arg = prompts[0].jsonObject["arguments"]!!.jsonArray.single().jsonObject
        assertThat(arg["name"]!!.jsonPrimitive.content).isEqualTo("text")
        assertThat(arg["required"]!!.jsonPrimitive.content).isEqualTo("true")
    }

    @Test
    fun `prompts needing hidden tools are not listed or gettable`() = runTest {
        val p = protocol(readOnly = true)
        val names = p.call("prompts/list")["result"]!!.jsonObject["prompts"]!!.jsonArray.map { it.jsonObject["name"]!!.jsonPrimitive.content }
        assertThat(names).containsExactly("echo_prompt")
        assertThat(p.call("prompts/get", """{"name":"writer_prompt"}""")["error"]!!.jsonObject["code"]!!.jsonPrimitive.int).isEqualTo(-32602)
    }

    @Test
    fun `prompts get renders messages`() = runTest {
        val result = protocol().call("prompts/get", """{"name":"echo_prompt","arguments":{"text":"hi"}}""")["result"]!!.jsonObject
        assertThat(result["description"]!!.jsonPrimitive.content).isEqualTo("echo")
        val message = result["messages"]!!.jsonArray.single().jsonObject
        assertThat(message["role"]!!.jsonPrimitive.content).isEqualTo("user")
        val content = message["content"]!!.jsonObject
        assertThat(content["type"]!!.jsonPrimitive.content).isEqualTo("text")
        assertThat(content["text"]!!.jsonPrimitive.content).isEqualTo("say hi")
    }

    @Test
    fun `missing, non-string or rejected prompt arguments are invalid params`() = runTest {
        val p = protocol()
        for (params in listOf(
            """{"name":"echo_prompt"}""",
            """{"name":"echo_prompt","arguments":{"text":5}}""",
            """{"name":"echo_prompt","arguments":{"text":"bad"}}""",
            """{"name":"nope"}""",
        )) {
            val error = p.call("prompts/get", params)["error"]!!.jsonObject
            assertThat(error["code"]!!.jsonPrimitive.int).isEqualTo(-32602)
        }
    }

    @Test
    fun `built-in prompts render with their required arguments`() = runTest {
        for (prompt in BuiltInPrompts.all()) {
            val args = prompt.arguments.filter { it.required }.associate { it.name to "x" }
            val result = prompt.render(args)
            assertThat(result.messages).isNotEmpty()
            assertThat(result.messages.first().text).isNotEmpty()
        }
    }

    @Test
    fun `daily briefing rejects a malformed date`() = runTest {
        val result = runCatching { BuiltInPrompts.DailyBriefing.render(mapOf("date" to "tomorrow")) }
        assertThat(result.exceptionOrNull()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `registry changes notify listeners`() {
        val tools = ToolRegistry()
        var changes = 0
        tools.addChangeListener { changes++ }
        tools.register(tool("a"))
        tools.registerAll(listOf(tool("b"), tool("c")))
        tools.setToolEnabled("a", false)
        tools.setToolEnabled("a", false) // no change
        tools.setToolEnabled("ghost", false) // not registered: no visible change
        tools.setDisabledTools(setOf("c"))
        tools.setDisabledTools(setOf("c")) // no change
        assertThat(tools.unregister("b")).isTrue()
        assertThat(tools.unregister("b")).isFalse()
        assertThat(changes).isEqualTo(5)
    }

    @Test
    fun `a throwing listener doesn't block the others`() {
        val tools = ToolRegistry()
        var called = false
        tools.addChangeListener { error("boom") }
        tools.addChangeListener { called = true }
        tools.register(tool("a"))
        assertThat(called).isTrue()
    }
}
