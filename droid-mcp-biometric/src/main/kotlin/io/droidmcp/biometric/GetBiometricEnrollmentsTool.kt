package io.droidmcp.biometric

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Reports which biometric modalities are present/enrolled. No permissions declared by the module
 * (the [FingerprintManager.hasEnrolledFingerprints] call is wrapped in try/catch and degrades to
 * `false` if access is denied; `FEATURE_FACE` is a hardware-feature check, not enrollment state).
 *
 * `enrolled_count` is the number of modalities flagged true (0–2: fingerprint + face), NOT a count
 * of individually enrolled credentials.
 *
 * Output map: `enrolled_count` (Int), `has_fingerprint` (Boolean), `has_face` (Boolean).
 */
class GetBiometricEnrollmentsTool(private val context: Context) : McpTool {

    override val name = "get_biometric_enrollments"
    override val description = "Get information about present/enrolled biometric modalities (fingerprint, face)"
    override val parameters = emptyList<ToolParameter>()
    override val annotations = ToolAnnotations(readOnlyHint = true, idempotentHint = true)

    @Suppress("DEPRECATION")
    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val hasFingerprint = try {
            val fm = context.getSystemService(Context.FINGERPRINT_SERVICE) as? FingerprintManager
            fm?.hasEnrolledFingerprints() == true
        } catch (e: Exception) {
            false
        }

        val hasFace = context.packageManager.hasSystemFeature(PackageManager.FEATURE_FACE)

        val enrolledCount = (if (hasFingerprint) 1 else 0) + (if (hasFace) 1 else 0)

        return ToolResult.success(mapOf(
            "enrolled_count" to enrolledCount,
            "has_fingerprint" to hasFingerprint,
            "has_face" to hasFace,
        ))
    }
}
