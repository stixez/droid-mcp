package io.droidmcp.biometric

import android.content.Context
import io.droidmcp.core.McpTool

/**
 * Provider for read-only biometric introspection tools: [CheckBiometricAvailabilityTool] and
 * [GetBiometricEnrollmentsTool].
 */
object BiometricTools {

    /** All biometric tools bound to [context]. */
    fun all(context: Context): List<McpTool> = listOf(
        CheckBiometricAvailabilityTool(context),
        GetBiometricEnrollmentsTool(context),
    )

    /** No permissions required. */
    fun requiredPermissions(): List<String> = emptyList()

    /** Always true — these tools need no permissions. */
    fun hasPermissions(context: Context): Boolean = true
}
