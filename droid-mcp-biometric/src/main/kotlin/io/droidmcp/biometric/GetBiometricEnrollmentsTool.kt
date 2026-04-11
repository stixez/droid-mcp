package io.droidmcp.biometric

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import io.droidmcp.core.McpTool
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

class GetBiometricEnrollmentsTool(private val context: Context) : McpTool {

    override val name = "get_biometric_enrollments"
    override val description = "Get information about enrolled biometrics on the device"
    override val parameters = emptyList<ToolParameter>()

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
