package io.droidmcp.core

data class ToolResult(
    val isSuccess: Boolean,
    val data: Map<String, Any?>?,
    val errorMessage: String?,
) {
    companion object {
        fun success(data: Map<String, Any?>): ToolResult =
            ToolResult(isSuccess = true, data = data, errorMessage = null)

        fun error(message: String): ToolResult =
            ToolResult(isSuccess = false, data = null, errorMessage = message)
    }
}
