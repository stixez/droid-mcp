package io.droidmcp.biometric

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolResult

class CheckBiometricAvailabilityTool(private val context: Context) : McpTool {

    override val name = "check_biometric_availability"
    override val description = "Check if biometric authentication is available on the device"
    override val parameters = emptyList<io.droidmcp.core.ToolParameter>()

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val biometricManager = BiometricManager.from(context)

        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        )

        val (canAuth, hardwareType) = when (canAuthenticate) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                val hasFingerprint = hasHardware(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                val hasFace = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    hasHardware(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                } else false

                val type = when {
                    hasFingerprint && hasFace -> "fingerprint,face"
                    hasFingerprint -> "fingerprint"
                    hasFace -> "face"
                    else -> "none"
                }
                true to type
            }
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> false to "none"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> true to "none"
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> true to "update_required"
            else -> false to "unknown"
        }

        return ToolResult.success(mapOf(
            "can_authenticate" to canAuth,
            "hardware_type" to hardwareType
        ))
    }

    private fun hasHardware(authenticators: Int): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
    }
}
