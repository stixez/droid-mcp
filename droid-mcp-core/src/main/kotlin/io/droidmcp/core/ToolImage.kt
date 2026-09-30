package io.droidmcp.core

/**
 * An image produced by a tool, sent over MCP as an `image` content block
 * (`{"type": "image", "data": <base64>, "mimeType": ...}`). Created by [ToolResult.withImage].
 *
 * @property base64 The image bytes, base64-encoded without line wrapping.
 * @property mimeType e.g. `image/png` or `image/jpeg`.
 * @property dataKey The [ToolResult.data] key holding the same base64, which the MCP transport
 *   omits from the JSON so the image isn't sent twice. Null when the image isn't in [ToolResult.data].
 */
data class ToolImage(
    val base64: String,
    val mimeType: String,
    val dataKey: String? = null,
)
