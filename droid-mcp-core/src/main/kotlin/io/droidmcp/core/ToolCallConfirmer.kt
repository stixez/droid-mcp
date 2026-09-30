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

    companion object {
        /**
         * Asks the MCP client's user (via [elicit]) to approve the call. When the client can't
         * elicit — in-process calls, clients without the capability — [fallback] decides, and
         * with no fallback the call is declined.
         *
         * Note the trust model: this asks whoever is at the *client* (e.g. the desktop), which
         * is the right person for "did you mean to do this?" but not a defence against a
         * compromised client. For that, confirm on the phone with your own [ToolCallConfirmer].
         */
        fun viaClientElicitation(fallback: ToolCallConfirmer? = null): ToolCallConfirmer = ToolCallConfirmer { request ->
            val args = request.arguments.entries.joinToString { (k, v) -> "$k=$v" }.take(MAX_ARGS_CHARS)
            val answer = elicit(
                message = "Allow ${request.toolName}${if (args.isEmpty()) "" else " ($args)"}?",
                requestedSchema = mapOf("type" to "object", "properties" to emptyMap<String, Any>()),
            )
            when {
                answer != null -> answer.action == ElicitationResult.Action.ACCEPT
                fallback != null -> fallback.confirm(request)
                else -> false
            }
        }

        private const val MAX_ARGS_CHARS = 300
    }
}
