package io.droidmcp.keyguard

import android.app.KeyguardManager
import android.content.Context
import android.os.PowerManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class GetLockStateTool(private val context: Context) : McpTool {

    override val name = "get_lock_state"
    override val description = "Check if the device is currently locked and whether the screen is on"
    override val parameters = emptyList<ToolParameter>()

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        return ToolResult.success(mapOf(
            "is_device_locked" to keyguardManager.isDeviceLocked,
            "is_keyguard_locked" to keyguardManager.isKeyguardLocked,
            "is_screen_on" to powerManager.isInteractive,
        ))
    }
}
