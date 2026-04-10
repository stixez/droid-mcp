package io.droidmcp.core.protocol

interface McpProtocol {
    suspend fun handleMessage(jsonRequest: String): String
}
