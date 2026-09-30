package io.droidmcp.core

/**
 * The droid-mcp SDK version, reported as the server version in the MCP `initialize`
 * handshake and broadcast over mDNS. Must equal `VERSION_NAME` in the root
 * `gradle.properties` (the single source for the published version) — `VersionTest` enforces it.
 */
const val DROID_MCP_VERSION: String = "0.11.0"
