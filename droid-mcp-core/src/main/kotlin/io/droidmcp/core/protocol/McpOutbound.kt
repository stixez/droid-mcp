package io.droidmcp.core.protocol

/**
 * Where server-to-client JSON-RPC messages go while a request is in flight — progress
 * notifications and `elicitation/create` requests. The HTTP transport backs it with the
 * request's SSE stream.
 */
internal fun interface McpOutbound {
    suspend fun send(message: String)
}
