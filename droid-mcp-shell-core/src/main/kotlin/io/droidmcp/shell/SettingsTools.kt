package io.droidmcp.shell

import io.droidmcp.core.McpTool
import io.droidmcp.core.ParameterType
import io.droidmcp.core.ToolAnnotations
import io.droidmcp.core.ToolParameter
import io.droidmcp.core.ToolResult

/**
 * Shared body for the three `put_*_setting` tools. `namespace` is the
 * `settings` CLI namespace (`secure` / `global` / `system`).
 */
private suspend fun putSetting(
    shell: ShellBackend,
    namespace: String,
    params: Map<String, Any>,
): ToolResult {
    val key = ShellValidation.requireSettingsKey(params["key"]?.toString())
        .getOrElse { return (it as ShellValidationFailure).let { f -> ToolResult.error(f.code, f.detail) } }
    val value = ShellValidation.requireSettingsValue(params["value"]?.toString())
        .getOrElse { return (it as ShellValidationFailure).let { f -> ToolResult.error(f.code, f.detail) } }
    return shell.gatedExec("settings", listOf("put", namespace, key, value)) { result ->
        if (result.isSuccess && result.stderr.isEmpty()) {
            ToolResult.success(mapOf(
                "success" to true,
                "namespace" to namespace,
                "key" to key,
                "value" to value,
            ))
        } else {
            ToolResult.error("settings_put_failed", result.stderr.lineSequence().firstOrNull()?.take(200) ?: "exit ${result.exitCode}")
        }
    }
}

class PutSecureSettingTool(private val shell: ShellBackend) : McpTool {
    override val name = "put_secure_setting"
    override val description = "Write a Settings.Secure value via `settings put secure`. Examples: location-mode, accessibility-enabled toggles. Most apps cannot write these without privileged shell access. Idempotent."
    override val parameters = listOf(
        ToolParameter("key", "Settings key (e.g. 'location_mode').", ParameterType.STRING, required = true),
        ToolParameter("value", "Value to write. The shell converts numbers / booleans implicitly.", ParameterType.STRING, required = true),
    )
    override val annotations = ToolAnnotations(destructiveHint = true, idempotentHint = true)
    override suspend fun execute(params: Map<String, Any>): ToolResult = putSetting(shell, "secure", params)
}

class PutGlobalSettingTool(private val shell: ShellBackend) : McpTool {
    override val name = "put_global_setting"
    override val description = "Write a Settings.Global value via `settings put global`. Examples: airplane_mode_on, wifi_on. Idempotent."
    override val parameters = listOf(
        ToolParameter("key", "Settings key (e.g. 'airplane_mode_on').", ParameterType.STRING, required = true),
        ToolParameter("value", "Value to write.", ParameterType.STRING, required = true),
    )
    override val annotations = ToolAnnotations(destructiveHint = true, idempotentHint = true)
    override suspend fun execute(params: Map<String, Any>): ToolResult = putSetting(shell, "global", params)
}

class PutSystemSettingTool(private val shell: ShellBackend) : McpTool {
    override val name = "put_system_setting"
    override val description = "Write a Settings.System value via `settings put system`. Examples: screen_brightness, screen_off_timeout. Idempotent."
    override val parameters = listOf(
        ToolParameter("key", "Settings key (e.g. 'screen_brightness').", ParameterType.STRING, required = true),
        ToolParameter("value", "Value to write.", ParameterType.STRING, required = true),
    )
    override val annotations = ToolAnnotations(destructiveHint = true, idempotentHint = true)
    override suspend fun execute(params: Map<String, Any>): ToolResult = putSetting(shell, "system", params)
}
