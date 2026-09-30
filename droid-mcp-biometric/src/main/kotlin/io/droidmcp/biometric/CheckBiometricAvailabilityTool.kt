package io.droidmcp.biometric

import android.app.KeyguardManager
import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Reports which authenticator classes can currently succeed, using AndroidX [BiometricManager]
 * for biometrics and [KeyguardManager.isDeviceSecure] for device credentials. No permissions
 * required.
 *
 * Android does not expose which biometric *modality* (fingerprint, face, iris) backs each class,
 * so this tool reports authenticator classes rather than guessing sensor types:
 * - `strong_available` — a Class 3 (`BIOMETRIC_STRONG`) biometric is enrolled and usable.
 * - `weak_available` — a Class 2+ (`BIOMETRIC_WEAK`) biometric is usable (true whenever strong is).
 * - `device_credential_available` — a PIN/pattern/password is set.
 *
 * Biometrics and credentials are checked separately. `can_authenticate` is true if any of the
 * three is available (the same union the tool historically queried: weak biometric or device
 * credential). `hardware_type` names the best available biometric class: `"biometric_strong"`,
 * `"biometric_weak"`, or — when no biometric is usable — the reason: `"none"` (no hardware / none
 * enrolled / hardware unavailable), `"update_required"` (security update needed), `"unknown"`.
 * `biometric_status` gives the raw reason for the weak-class check (`success`, `no_hardware`,
 * `hw_unavailable`, `none_enrolled`, `update_required`, `unsupported`, `unknown`).
 *
 * Output map: `can_authenticate`, `hardware_type`, `strong_available`, `weak_available`,
 * `device_credential_available`, `biometric_status`.
 */
class CheckBiometricAvailabilityTool(private val context: Context) : McpTool {

    override val name = "check_biometric_availability"
    override val description = "Check which authenticator classes are available: strong (Class 3) biometric, weak (Class 2) biometric, and device credential (PIN/pattern/password)"
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val biometricManager = BiometricManager.from(context)

        val strongStatus = biometricManager.canAuthenticate(Authenticators.BIOMETRIC_STRONG)
        val weakStatus = biometricManager.canAuthenticate(Authenticators.BIOMETRIC_WEAK)
        val strong = strongStatus == BiometricManager.BIOMETRIC_SUCCESS
        val weak = strong || weakStatus == BiometricManager.BIOMETRIC_SUCCESS

        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        val credential = keyguard?.isDeviceSecure == true

        val hardwareType = when {
            strong -> "biometric_strong"
            weak -> "biometric_weak"
            else -> when (weakStatus) {
                BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
                BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
                BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "none"
                BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "update_required"
                else -> "unknown"
            }
        }

        return ToolResult.success(mapOf(
            "can_authenticate" to (weak || credential),
            "hardware_type" to hardwareType,
            "strong_available" to strong,
            "weak_available" to weak,
            "device_credential_available" to credential,
            "biometric_status" to statusName(if (strong) strongStatus else weakStatus),
        ))
    }

    private fun statusName(status: Int): String = when (status) {
        BiometricManager.BIOMETRIC_SUCCESS -> "success"
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "no_hardware"
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "hw_unavailable"
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "none_enrolled"
        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "update_required"
        BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED -> "unsupported"
        else -> "unknown"
    }
}
