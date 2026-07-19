package io.droidmcp.core.protocol

/**
 * JSON-RPC 2.0 request handler for the MCP surface (`initialize`, `tools/list`, `tools/call`,
 * `ping`). Transports feed raw request strings in and write the returned response string back.
 * The default implementation is [McpProtocolImpl].
 */
interface McpProtocol {
    /** Handle one JSON-RPC request and return the JSON-RPC response string. */
    suspend fun handleMessage(jsonRequest: String): String

    /**
     * Handle a message attributing any `tools/call` to [clientLabel] for audit.
     * Defaults to the unattributed form for implementations that don't audit.
     */
    suspend fun handleMessage(jsonRequest: String, clientLabel: String?): String =
        handleMessage(jsonRequest)
}
