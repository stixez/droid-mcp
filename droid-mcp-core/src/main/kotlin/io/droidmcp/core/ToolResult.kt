package io.droidmcp.core

/**
 * The outcome of an [McpTool.execute] call. Exactly one of [data] / [errorMessage] is
 * meaningful, selected by [isSuccess]. The protocol layer maps a result onto the MCP
 * `tools/call` response: success becomes content, failure becomes an `isError: true` payload.
 *
 * Construct via the [success] / [error] factories rather than the primary constructor.
 *
 * @property isSuccess Whether the tool completed its work.
 * @property data Result map on success; its keys are part of the tool's wire contract. Null on failure.
 * @property errorMessage Failure description on error; null on success. See the [error] overloads
 *   for the prose vs. short-form (`code: detail`) conventions.
 * @property images Images to send to MCP clients as `image` content blocks. Attach them with
 *   [withImage]; in-process callers still read the base64 from [data].
 */
data class ToolResult(
    val isSuccess: Boolean,
    val data: Map<String, Any?>?,
    val errorMessage: String?,
    val images: List<ToolImage> = emptyList(),
) {
    /**
     * Marks the base64 string at `data[dataKey]` as an image of [mimeType]. Over MCP it is sent as
     * an `image` content block and [dataKey] is left out of the JSON text and `structuredContent`,
     * so the payload travels once and clients render it as a picture. [data] is unchanged, so
     * in-process callers keep reading `data[dataKey]`. No-op if the key isn't a string.
     */
    fun withImage(dataKey: String, mimeType: String): ToolResult {
        val base64 = data?.get(dataKey) as? String ?: return this
        return copy(images = images + ToolImage(base64, mimeType, dataKey))
    }

    companion object {
        /** A successful result carrying [data]. The map's keys are the tool's documented output shape. */
        fun success(data: Map<String, Any?>): ToolResult =
            ToolResult(isSuccess = true, data = data, errorMessage = null)

        /** A failed result with a human-readable [message]. Used by tools that predate 0.7.0. */
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
