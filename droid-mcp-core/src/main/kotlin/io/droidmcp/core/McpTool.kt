package io.droidmcp.core

interface McpTool {
    val name: String
    val description: String
    val parameters: List<ToolParameter>

    suspend fun execute(params: Map<String, Any>): ToolResult
}
