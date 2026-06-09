package io.droidmcp.shell

import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * `am set-standby-bucket <pkg> <bucket>` — set the app's standby bucket, which
 * controls how aggressively the system throttles its background work.
 * Idempotent. The `bucket` value is lowercased and validated against
 * [VALID_BUCKETS] (`active` / `working_set` / `frequent` / `rare` /
 * `restricted`) before issuing the command; an unknown bucket fails with
 * `invalid_args`. Treated as a command failure on non-zero exit or non-empty
 * stderr.
 *
 * Privilege: requires a working [ShellBackend].
 *
 * Params: `package_name` (required), `bucket` (required).
 *
 * On success the result map carries `success` (true), `package_name`, and
 * `bucket`.
 */
class SetAppStandbyBucketTool(private val shell: ShellBackend) : McpTool {

    override val name = "set_app_standby_bucket"
    override val description = "Set an app's standby bucket via `am set-standby-bucket`. Buckets are 'active', 'working_set', 'frequent', 'rare', or 'restricted' (lowercase). 'restricted' is the most aggressive throttling. Idempotent."
    override val parameters = listOf(
        ToolParameter("package_name", "Application package name.", ParameterType.STRING, required = true),
        ToolParameter("bucket", "active | working_set | frequent | rare | restricted", ParameterType.STRING, required = true),
    )
    override val annotations = ToolAnnotations(destructiveHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val pkg = ShellValidation.requirePackageName(params["package_name"]?.toString())
            .getOrElse { return (it as ShellValidationFailure).let { f -> ToolResult.error(f.code, f.detail) } }
        val bucket = (params["bucket"] as? String)?.lowercase()?.takeIf { it in VALID_BUCKETS }
            ?: return ToolResult.error("invalid_args", "bucket must be one of: ${VALID_BUCKETS.joinToString()}")
        return shell.gatedExec("am", listOf("set-standby-bucket", pkg, bucket)) { result ->
            if (result.isSuccess && result.stderr.isEmpty()) {
                ToolResult.success(mapOf("success" to true, "package_name" to pkg, "bucket" to bucket))
            } else {
                ToolResult.error("set_bucket_failed", result.stderr.lineSequence().firstOrNull()?.take(200) ?: "exit ${result.exitCode}")
            }
        }
    }

    private companion object {
        val VALID_BUCKETS = setOf("active", "working_set", "frequent", "rare", "restricted")
    }
}

/**
 * `am set-inactive <pkg> true` — flag an app as idle so the system applies
 * aggressive Doze restrictions immediately rather than waiting for natural
 * idle accumulation (useful for testing Doze behaviour). Idempotent. Treated
 * as a command failure on non-zero exit or non-empty stderr.
 *
 * Privilege: requires a working [ShellBackend].
 *
 * Params: `package_name` (required).
 *
 * On success the result map carries `success` (true), `package_name`, and
 * `inactive` (true).
 */
class MakeAppInactiveTool(private val shell: ShellBackend) : McpTool {

    override val name = "make_app_inactive"
    override val description = "Force-mark an app as idle for Doze via `am set-inactive <pkg> true`. Useful for testing Doze-mode behaviour. Idempotent."
    override val parameters = listOf(
        ToolParameter("package_name", "Application package name.", ParameterType.STRING, required = true),
    )
    override val annotations = ToolAnnotations(destructiveHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val pkg = ShellValidation.requirePackageName(params["package_name"]?.toString())
            .getOrElse { return (it as ShellValidationFailure).let { f -> ToolResult.error(f.code, f.detail) } }
        return shell.gatedExec("am", listOf("set-inactive", pkg, "true")) { result ->
            if (result.isSuccess && result.stderr.isEmpty()) {
                ToolResult.success(mapOf("success" to true, "package_name" to pkg, "inactive" to true))
            } else {
                ToolResult.error("set_inactive_failed", result.stderr.lineSequence().firstOrNull()?.take(200) ?: "exit ${result.exitCode}")
            }
        }
    }
}
