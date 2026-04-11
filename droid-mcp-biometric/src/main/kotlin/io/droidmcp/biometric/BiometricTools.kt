package io.droidmcp.biometric

import android.content.Context
import io.droidmcp.core.McpTool

object BiometricTools {

    fun all(context: Context): List<McpTool> = listOf(
        CheckBiometricAvailabilityTool(context),
        GetBiometricEnrollmentsTool(context),
    )

    fun requiredPermissions(): List<String> = emptyList()

    fun hasPermissions(context: Context): Boolean = true
}
