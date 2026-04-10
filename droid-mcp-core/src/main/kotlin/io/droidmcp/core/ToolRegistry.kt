package io.droidmcp.core

class ToolRegistry {

    private val tools = LinkedHashMap<String, McpTool>()

    fun register(tool: McpTool) {
        tools[tool.name] = tool
    }

    fun registerAll(toolList: List<McpTool>) {
        toolList.forEach { register(it) }
    }

    fun getTool(name: String): McpTool? = tools[name]

    fun listTools(): List<McpTool> = tools.values.toList()

    suspend fun executeTool(name: String, params: Map<String, Any>): ToolResult {
        val tool = tools[name]
            ?: return ToolResult.error("Unknown tool: $name")
        return try {
            tool.execute(params)
        } catch (e: Exception) {
            ToolResult.error("Tool '$name' failed: ${e.message}")
        }
    }
}
