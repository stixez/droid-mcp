package io.droidmcp.keyguard

import android.app.KeyguardManager
import android.content.Context
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class GetKeyguardInfoTool(private val context: Context) : McpTool {

    override val name = "get_keyguard_info"
    override val description = "Get keyguard security details: whether a secure lock (PIN/pattern/password) is configured, and device lock state"
    override val parameters = emptyList<ToolParameter>()

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager

        return ToolResult.success(mapOf(
            "is_device_secure" to keyguardManager.isDeviceSecure,
            "is_device_locked" to keyguardManager.isDeviceLocked,
            "is_keyguard_locked" to keyguardManager.isKeyguardLocked,
            "is_keyguard_secure" to keyguardManager.isKeyguardSecure,
        ))
    }
}
