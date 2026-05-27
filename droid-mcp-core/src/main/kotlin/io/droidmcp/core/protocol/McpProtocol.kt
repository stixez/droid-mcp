package io.droidmcp.core.protocol

interface McpProtocol {
    suspend fun handleMessage(jsonRequest: String): String

    /**
     * Handle a message attributing any `tools/call` to [clientLabel] for audit.
     * Defaults to the unattributed form for implementations that don't audit.
     */
    suspend fun handleMessage(jsonRequest: String, clientLabel: String?): String =
        handleMessage(jsonRequest)
}
