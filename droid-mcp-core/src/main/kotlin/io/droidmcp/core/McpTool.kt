package io.droidmcp.core

data class ToolAnnotations(
    val readOnlyHint: Boolean = false,
    val destructiveHint: Boolean = false,
    val idempotentHint: Boolean = false,
    val openWorldHint: Boolean = false,
    val title: String? = null,
)

interface McpTool {
    val name: String
    val description: String
    val parameters: List<ToolParameter>
    val annotations: ToolAnnotations get() = ToolAnnotations()

    suspend fun execute(params: Map<String, Any>): ToolResult
}
