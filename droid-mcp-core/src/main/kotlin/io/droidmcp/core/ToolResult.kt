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

        /**
         * Short-form error envelope: `code` or `code: detail`. Used by tools
         * added in 0.7.0 so the harness can map machine-readable codes
         * (`accessibility_not_enabled`, `node_not_found`, ...) without parsing
         * prose. Wire shape is unchanged — `errorMessage` is still a single
         * string.
         */
        fun error(code: String, detail: String?): ToolResult =
            error(if (detail != null) "$code: $detail" else code)
    }
}
