package io.droidmcp.core

/**
 * A tool call waiting for the host's approval. See [DroidMcp.Builder.confirmToolCalls].
 *
 * @property toolName The tool about to run.
 * @property arguments The arguments it will receive (already converted from JSON).
 * @property annotations The tool's hints, e.g. to phrase the prompt ("send", "delete", …).
 * @property clientLabel Which HTTP client asked (the pairing label, or `"primary"` for the main
 *   token); null for in-process calls and for servers without auth.
 */
data class ToolCallRequest(
    val toolName: String,
    val arguments: Map<String, Any>,
    val annotations: ToolAnnotations,
    val clientLabel: String?,
)

/**
 * Host callback that approves or declines a tool call before it runs — typically by showing the
 * user a dialog ("Allow send_message to +1 555…?"). Return true to run the tool.
 *
 * Called on the caller's coroutine (switch to `Dispatchers.Main` yourself for UI). If it doesn't
 * answer within the registry's tool timeout, or throws, the call is declined.
 */
fun interface ToolCallConfirmer {
    suspend fun confirm(request: ToolCallRequest): Boolean
}
