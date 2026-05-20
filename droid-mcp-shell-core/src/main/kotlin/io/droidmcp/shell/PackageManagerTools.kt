package io.droidmcp.shell

import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * `pm install <path>` — silent install from a local file path. The host is
 * trusted to vet the path; we don't sandbox here (the shell backend already
 * provides the privilege boundary).
 */
class InstallApkTool(private val shell: ShellBackend) : McpTool {
    override val name = "install_apk"
    override val description = "Silently install an APK from a local file path. Uses `pm install` via the shell backend (Shizuku or root). Does not prompt the user."
    override val parameters = listOf(
        ToolParameter("path", "Absolute path to the .apk file on the device.", ParameterType.STRING, required = true),
        ToolParameter("replace", "Replace an existing install (default true). Maps to `pm install -r`.", ParameterType.BOOLEAN, required = false),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val path = params["path"]?.toString()?.takeIf { it.isNotBlank() }
            ?: return ToolResult.error("invalid_args", "path is required")
        val replace = (params["replace"] as? Boolean) ?: true
        val args = buildList {
            add("install")
            if (replace) add("-r")
            add(path)
        }
        return shell.gatedExec("pm", args) { result ->
            if (result.isSuccess && result.stdout.contains("Success", ignoreCase = true)) {
                ToolResult.success(mapOf("success" to true, "path" to path))
            } else {
                ToolResult.error("install_failed", result.output.lineSequence().firstOrNull()?.take(200) ?: "unknown")
            }
        }
    }
}

/** `pm uninstall <pkg>` — silent uninstall, no user prompt. */
class UninstallAppTool(private val shell: ShellBackend) : McpTool {
    override val name = "uninstall_app"
    override val description = "Silently uninstall an app by package name via `pm uninstall`. Requires the shell backend."
    override val parameters = listOf(
        ToolParameter("package_name", "Application package name (e.g. 'com.example.app').", ParameterType.STRING, required = true),
        ToolParameter("keep_data", "Keep application data on uninstall (`pm uninstall -k`, default false).", ParameterType.BOOLEAN, required = false),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val pkg = ShellValidation.requirePackageName(params["package_name"]?.toString())
            .getOrElse { return (it as ShellValidationFailure).let { f -> ToolResult.error(f.code, f.detail) } }
        val keepData = (params["keep_data"] as? Boolean) ?: false
        val args = buildList {
            add("uninstall")
            if (keepData) add("-k")
            add(pkg)
        }
        return shell.gatedExec("pm", args) { result ->
            if (result.isSuccess && result.stdout.contains("Success", ignoreCase = true)) {
                ToolResult.success(mapOf("success" to true, "package_name" to pkg))
            } else {
                ToolResult.error("uninstall_failed", result.output.lineSequence().firstOrNull()?.take(200) ?: "unknown")
            }
        }
    }
}

/** `pm clear <pkg>` — wipe app data + cache. */
class ClearAppDataTool(private val shell: ShellBackend) : McpTool {
    override val name = "clear_app_data"
    override val description = "Wipe an app's data and cache via `pm clear`. Equivalent to Settings > Apps > Storage > Clear data."
    override val parameters = listOf(
        ToolParameter("package_name", "Application package name.", ParameterType.STRING, required = true),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val pkg = ShellValidation.requirePackageName(params["package_name"]?.toString())
            .getOrElse { return (it as ShellValidationFailure).let { f -> ToolResult.error(f.code, f.detail) } }
        return shell.gatedExec("pm", listOf("clear", pkg)) { result ->
            if (result.isSuccess && result.stdout.contains("Success", ignoreCase = true)) {
                ToolResult.success(mapOf("success" to true, "package_name" to pkg))
            } else {
                ToolResult.error("clear_failed", result.output.lineSequence().firstOrNull()?.take(200) ?: "unknown")
            }
        }
    }
}

/** `am force-stop <pkg>` — terminate an app's processes. */
class ForceStopAppTool(private val shell: ShellBackend) : McpTool {
    override val name = "force_stop_app"
    override val description = "Force-stop an app via `am force-stop`. Terminates all of its processes. Equivalent to Settings > Apps > Force stop."
    override val parameters = listOf(
        ToolParameter("package_name", "Application package name.", ParameterType.STRING, required = true),
    )
    override val annotations = ToolAnnotations(destructiveHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val pkg = ShellValidation.requirePackageName(params["package_name"]?.toString())
            .getOrElse { return (it as ShellValidationFailure).let { f -> ToolResult.error(f.code, f.detail) } }
        return shell.gatedExec("am", listOf("force-stop", pkg)) { result ->
            // am force-stop is silent on success — no "Success" in stdout. Trust the exit code.
            if (result.isSuccess) {
                ToolResult.success(mapOf("success" to true, "package_name" to pkg))
            } else {
                ToolResult.error("force_stop_failed", result.stderr.lineSequence().firstOrNull()?.take(200) ?: "exit ${result.exitCode}")
            }
        }
    }
}

/** `pm disable-user --user 0 <pkg>` — disable a system / user app without uninstalling. */
class DisableAppTool(private val shell: ShellBackend) : McpTool {
    override val name = "disable_app"
    override val description = "Disable an app for the primary user via `pm disable-user --user 0`. Hides it from the launcher and prevents background activity without uninstalling. Reversible via `enable_app`. Idempotent — disabling an already-disabled app returns success with the same final state."
    override val parameters = listOf(
        ToolParameter("package_name", "Application package name.", ParameterType.STRING, required = true),
    )
    override val annotations = ToolAnnotations(destructiveHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val pkg = ShellValidation.requirePackageName(params["package_name"]?.toString())
            .getOrElse { return (it as ShellValidationFailure).let { f -> ToolResult.error(f.code, f.detail) } }
        return shell.gatedExec("pm", listOf("disable-user", "--user", "0", pkg)) { result ->
            if (result.isSuccess && result.stdout.contains("disabled-user", ignoreCase = true)) {
                ToolResult.success(mapOf("success" to true, "package_name" to pkg, "state" to "disabled"))
            } else {
                ToolResult.error("disable_failed", result.output.lineSequence().firstOrNull()?.take(200) ?: "unknown")
            }
        }
    }
}

/** `pm enable <pkg>` — re-enable a previously disabled app. Idempotent. */
class EnableAppTool(private val shell: ShellBackend) : McpTool {
    override val name = "enable_app"
    override val description = "Re-enable a previously disabled app via `pm enable`. Idempotent — enabling an already-enabled app is a no-op."
    override val parameters = listOf(
        ToolParameter("package_name", "Application package name.", ParameterType.STRING, required = true),
    )
    override val annotations = ToolAnnotations(destructiveHint = true, idempotentHint = true)

    override suspend fun execute(params: Map<String, Any>): ToolResult {
        val pkg = ShellValidation.requirePackageName(params["package_name"]?.toString())
            .getOrElse { return (it as ShellValidationFailure).let { f -> ToolResult.error(f.code, f.detail) } }
        return shell.gatedExec("pm", listOf("enable", pkg)) { result ->
            if (result.isSuccess && result.stdout.contains("enabled", ignoreCase = true)) {
                ToolResult.success(mapOf("success" to true, "package_name" to pkg, "state" to "enabled"))
            } else {
                ToolResult.error("enable_failed", result.output.lineSequence().firstOrNull()?.take(200) ?: "unknown")
            }
        }
    }
}
