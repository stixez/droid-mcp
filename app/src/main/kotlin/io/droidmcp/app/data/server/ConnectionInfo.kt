package io.droidmcp.app.data.server

/**
 * How a desktop client reaches this phone. Pure functions over plain values, so the exact
 * strings users paste into their terminal are unit-tested.
 */
data class ConnectionInfo(
    val host: String,
    val port: Int,
    val tls: Boolean,
    val token: String,
) {
    val url: String get() = "${if (tls) "https" else "http"}://$host:$port/mcp"

    /** `claude mcp add` for Claude Code. */
    fun claudeCodeCommand(name: String = SERVER_NAME): String =
        "claude mcp add --transport http $name $url --header \"Authorization: Bearer $token\""

    /** Block for Codex CLI's `~/.codex/config.toml`. */
    fun codexConfig(name: String = SERVER_NAME): String = buildString {
        appendLine("[mcp_servers.$name]")
        appendLine("url = \"$url\"")
        append("http_headers = { Authorization = \"Bearer $token\" }")
    }

    /** `mcpServers` JSON for Cursor, Claude Desktop config files and most other clients. */
    fun jsonConfig(name: String = SERVER_NAME): String = """
        {
          "mcpServers": {
            "$name": {
              "type": "http",
              "url": "$url",
              "headers": { "Authorization": "Bearer $token" }
            }
          }
        }
    """.trimIndent()

    companion object {
        const val SERVER_NAME = "phone"

        /** USB route: the client connects to localhost and adb forwards the port to the phone. */
        fun adbForwardCommand(port: Int): String = "adb forward tcp:$port tcp:$port"
    }
}
