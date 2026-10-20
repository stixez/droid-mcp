package io.droidmcp.all

import com.google.common.truth.Truth.assertWithMessage
import io.droidmcp.core.McpTool
import io.droidmcp.core.OutputSchema
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolParameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * Every tool's [McpTool.outputSchema] is well formed, and whatever a tool returns when run against
 * relaxed mocks (Android stubs return defaults, so most tools fail early or return zeros) matches
 * it. Clients may validate `structuredContent` against the schema, so a mismatch breaks them.
 */
class ToolOutputSchemaTest {

    private val tools = ToolDiscovery.discoverTools()

    @Test
    fun `every tool declares a well-formed object schema`() {
        for (tool in tools) {
            val schema = tool.outputSchema
            assertWithMessage("${tool.name} has no outputSchema").that(schema).isNotNull()
            checkObject(tool.name, schema!!)
        }
    }

    private fun checkObject(path: String, schema: Map<*, *>) {
        val type = schema["type"]
        assertWithMessage("$path type").that(type == "object" || (type as? List<*>)?.contains("object") == true).isTrue()
        val properties = schema["properties"] as? Map<*, *>
        assertWithMessage("$path properties").that(properties).isNotNull()
        val required = (schema["required"] as? List<*>).orEmpty()
        assertWithMessage("$path required keys must be declared properties").that(properties!!.keys).containsAtLeastElementsIn(required)
        properties.forEach { (key, sub) ->
            sub as Map<*, *>
            when {
                sub["properties"] != null -> checkObject("$path.$key", sub)
                (sub["items"] as? Map<*, *>)?.get("properties") != null -> checkObject("$path.$key[]", sub["items"] as Map<*, *>)
            }
        }
    }

    @TestFactory
    fun `results from mocked dependencies match the declared schema`(): List<DynamicTest> = tools.map { tool ->
        dynamicTest(tool.name) {
            val result = runBlocking {
                withTimeoutOrNull(CALL_TIMEOUT_MS) {
                    withContext(Dispatchers.Default) { runCatching { tool.execute(plausibleArguments(tool)) }.getOrNull() }
                }
            }
            if (result == null || !result.isSuccess) return@dynamicTest
            // Image keys travel as image blocks, not in structuredContent.
            val structured = result.data.orEmpty() - result.images.mapNotNull { it.dataKey }.toSet()
            assertWithMessage("${tool.name} returned ${result.data}")
                .that(OutputSchema.violations(tool.outputSchema!!, structured))
                .isEmpty()
        }
    }

    /** Arguments that pass basic validation: required strings, enums, in-range numbers. */
    private fun plausibleArguments(tool: McpTool): Map<String, Any> =
        tool.parameters.filter { it.required }.associate { it.name to sampleValue(it) }

    private fun sampleValue(p: ToolParameter): Any = when (p.type) {
        ParameterType.STRING -> p.enumValues?.first() ?: "x"
        ParameterType.INTEGER -> (p.minimum ?: 1.0).toLong().coerceAtLeast(1)
        ParameterType.NUMBER -> p.minimum ?: 1.0
        ParameterType.BOOLEAN -> false
        ParameterType.ARRAY -> listOf(10L, 10L)
        ParameterType.OBJECT -> emptyMap<String, Any>()
    }

    private companion object {
        /** Tools that wait on hardware (sensors, camera) give up here; only finished results are checked. */
        const val CALL_TIMEOUT_MS = 2_000L
    }
}
